package io.gonative.android;

import android.Manifest;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;

import java.util.HashMap;
import java.util.Map;

import io.gonative.gonative_core.AppConfig;
import io.gonative.gonative_core.LeanUtils;

/**
 * Created by weiyin on 6/24/14.
 */
public class FileDownloader implements DownloadListener {
    private enum DownloadLocation {
        PUBLIC_DOWNLOADS, PRIVATE_INTERNAL
    }

    private static final String TAG = FileDownloader.class.getName();
    private final MainActivity context;
    private final DownloadLocation defaultDownloadLocation;
    private final Map<String, DownloadManager.Request> pendingExternalDownloads;
    private final ActivityResultLauncher<String> requestPermissionLauncher;
    private UrlNavigation urlNavigation;
    private String lastDownloadedUrl;

    private DownloadManager downloadManager;
    private final Map<Long, String> pendingDownloadNotification;

    private DownloadService downloadService;
    private boolean isBound = false;
    private PreDownloadInfo preDownloadInfo;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            DownloadService.DownloadBinder binder = (DownloadService.DownloadBinder) iBinder;
            downloadService = binder.getService();
            isBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            downloadService = null;
            isBound = false;
        }
    };

    FileDownloader(MainActivity context) {
        this.context = context;
        this.pendingExternalDownloads = new HashMap<>();
        this.pendingDownloadNotification = new HashMap<>();

        AppConfig appConfig = AppConfig.getInstance(this.context);
        if (appConfig.downloadToPublicStorage) {
            this.defaultDownloadLocation = DownloadLocation.PUBLIC_DOWNLOADS;
        } else {
            this.defaultDownloadLocation = DownloadLocation.PRIVATE_INTERNAL;
        }
        registerDownloadBroadCastReceiver(context);

        Intent intent = new Intent(context, DownloadService.class);
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);

        // initialize request permission launcher
        requestPermissionLauncher = context.registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            if (preDownloadInfo != null) {
                if (preDownloadInfo.isBlob) {
                    context.getFileWriterSharer().downloadBlobUrl(preDownloadInfo.url);
                } else {
                    startDownload(preDownloadInfo.url, preDownloadInfo.mimetype, preDownloadInfo.shouldSaveToGallery, preDownloadInfo.open);
                }
                preDownloadInfo = null;
            }
        });
    }

    private void registerDownloadBroadCastReceiver(Context context) {
        if (context == null) return;
        BroadcastReceiver onDownloadComplete = new BroadcastReceiver() {
            public void onReceive(Context ctx, Intent intent) {
                long dwnId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                if (dwnId == -1 || downloadManager == null) {
                    return;
                }
                Cursor cursor = downloadManager.query(new DownloadManager.Query().setFilterById(dwnId));
                if(cursor.moveToFirst()) {
                    int columnIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                    int status = cursor.getInt(columnIndex);
                    String filename = getDownloadFilename(dwnId);
                    if (TextUtils.isEmpty(filename)) return;
                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        Toast.makeText(ctx, ctx.getString(R.string.file_download_finished) + ": " + filename, Toast.LENGTH_SHORT).show();
                    } else if (status == DownloadManager.STATUS_FAILED) {
                        Toast.makeText(ctx, ctx.getString(R.string.download_canceled) + ": " + filename, Toast.LENGTH_SHORT).show();
                    }
                }
            }
        };
        context.getApplicationContext().registerReceiver(onDownloadComplete, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
    }

    @Override
    public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimetype, long contentLength) {
        if (urlNavigation != null) {
            urlNavigation.onDownloadStart();
        }

        if (context != null) {
            context.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    context.showWebview();
                }
            });
        }

        if (url.startsWith("blob:") && context != null) {
            if (requestPostNotificationPermission(new PreDownloadInfo(url, true))) {
                return;
            }

            context.getFileWriterSharer().downloadBlobUrl(url);
            return;
        }

        if (userAgent == null) {
            userAgent = AppConfig.getInstance(context).userAgent;
        }

        lastDownloadedUrl = url;

        // try to guess mimetype
        if (mimetype == null || mimetype.equalsIgnoreCase("application/force-download") ||
                mimetype.equalsIgnoreCase("application/octet-stream")) {
            MimeTypeMap mimeTypeMap = MimeTypeMap.getSingleton();
            String extension = MimeTypeMap.getFileExtensionFromUrl(url);
            if (extension != null && !extension.isEmpty()) {
                String guessedMimeType = mimeTypeMap.getMimeTypeFromExtension(extension);
                if (guessedMimeType != null) {
                    mimetype = guessedMimeType;
                }
            }
        }

        if (this.defaultDownloadLocation == DownloadLocation.PUBLIC_DOWNLOADS) {
            if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                try {
                    Uri uri = Uri.parse(url);
                    DownloadManager.Request request = new DownloadManager.Request(uri);
                    request.addRequestHeader("User-Agent", userAgent);

                    // set cookies
                    CookieManager cookieManager = android.webkit.CookieManager.getInstance();
                    String cookie = cookieManager.getCookie(url);
                    if (cookie != null) {
                        request.addRequestHeader("Cookie", cookie);
                    }

                    request.allowScanningByMediaScanner();
                    String guessedName = LeanUtils.guessFileName(url, contentDisposition, mimetype);
                    request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, guessedName);
                    request.setMimeType(mimetype);
                    request.setTitle(context.getResources().getString(R.string.file_download_title) + " " + guessedName);
                    request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                    enqueueBackgroundDownload(guessedName, request);

                } catch (Exception e) {
                    Log.e(TAG, e.getMessage(), e);
                    Toast.makeText(context, e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                }

                return;
            } else {
                Log.w(TAG, "External storage is not mounted. Downloading internally.");
            }
        }

        startDownload(url, mimetype, false, false);
    }

    private void enqueueBackgroundDownload(String filename, DownloadManager.Request request) {
        this.pendingExternalDownloads.put(filename, request);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            this.context.getExternalStorageWritePermission();
        }else {
            gotExternalStoragePermissions(true);
        }
    }

    public void gotExternalStoragePermissions(boolean granted) {
        if (granted) {
            if (this.downloadManager == null) {
                this.downloadManager = (DownloadManager) this.context.getSystemService(Context.DOWNLOAD_SERVICE);
                if (downloadManager == null) {
                    Log.e(TAG, "Error getting system download manager");
                    return;
                }
            }
            for (Map.Entry<String, DownloadManager.Request> set : this.pendingExternalDownloads.entrySet()) {
                long downloadId = downloadManager.enqueue(set.getValue());
                this.pendingDownloadNotification.put(downloadId, set.getKey());
            }
            this.pendingExternalDownloads.clear();
        }
    }

    private String getDownloadFilename(long dwnId) {
        String filename = null;
        if (pendingDownloadNotification.containsKey(dwnId)) {
            filename = pendingDownloadNotification.get(dwnId);
            pendingDownloadNotification.remove(dwnId);
        }
        return filename;
    }

    public void downloadFile(String url, boolean shouldSaveToGallery, boolean open) {
        if (TextUtils.isEmpty(url)) {
            Log.d(TAG, "downloadFile: Url empty!");
            return;
        }

        if (url.startsWith("blob:") && context != null) {
            if (requestPostNotificationPermission(new PreDownloadInfo(url, true))) {
                return;
            }
            context.getFileWriterSharer().downloadBlobUrl(url);
            return;
        }

        String mimetype = "*/*";
        MimeTypeMap mimeTypeMap = MimeTypeMap.getSingleton();
        String extension = MimeTypeMap.getFileExtensionFromUrl(url);
        if (extension != null && !extension.isEmpty()) {
            String guessedMimeType = mimeTypeMap.getMimeTypeFromExtension(extension);
            if (guessedMimeType != null) {
                mimetype = guessedMimeType;
            }
        }

        startDownload(url, mimetype, shouldSaveToGallery, open);
    }

    private void startDownload(String downloadUrl, String mimetype, boolean shouldSaveToGallery, boolean open) {
        if (isBound) {
            if (requestPostNotificationPermission(new PreDownloadInfo(downloadUrl, mimetype, shouldSaveToGallery, open, false))) return;
            downloadService.startDownload(downloadUrl, mimetype, shouldSaveToGallery, open);
        }
    }

    // Requests Notification permission on Android 13+ for download progress info.
    // If NOT granted, will only show Toast message
    private boolean requestPostNotificationPermission(PreDownloadInfo preDownloadInfo) {
        if (!DownloadService.enableDownloadNotification) return false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            this.preDownloadInfo = preDownloadInfo;
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            return true;
        }
        return false;
    }

    public String getLastDownloadedUrl() {
        return lastDownloadedUrl;
    }

    public void setUrlNavigation(UrlNavigation urlNavigation) {
        this.urlNavigation = urlNavigation;
    }

    public void unbindDownloadService() {
        if (isBound) {
            context.unbindService(serviceConnection);
            isBound = false;
        }
    }

    private static class PreDownloadInfo {
        String url;
        String mimetype;
        boolean shouldSaveToGallery;
        boolean open;
        boolean isBlob;

        public PreDownloadInfo(String url, String mimetype, boolean shouldSaveToGallery, boolean open, boolean isBlob) {
            this.url = url;
            this.mimetype = mimetype;
            this.shouldSaveToGallery = shouldSaveToGallery;
            this.open = open;
            this.isBlob = isBlob;
        }

        public PreDownloadInfo(String url, boolean isBlob) {
            this.url = url;
            this.isBlob = isBlob;
        }
    }
}
