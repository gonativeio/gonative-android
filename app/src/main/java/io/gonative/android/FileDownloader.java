package io.gonative.android;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.DownloadListener;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

import io.gonative.gonative_core.AppConfig;

/**
 * Created by weiyin on 6/24/14.
 */
public class FileDownloader implements DownloadListener {
    public enum DownloadLocation {
        PUBLIC_DOWNLOADS, PRIVATE_INTERNAL
    }

    private static final String TAG = FileDownloader.class.getName();
    private final MainActivity context;
    private final DownloadLocation defaultDownloadLocation;
    private final ActivityResultLauncher<String[]> requestPermissionLauncher;
    private UrlNavigation urlNavigation;
    private String lastDownloadedUrl;
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

        AppConfig appConfig = AppConfig.getInstance(this.context);
        if (appConfig.downloadToPublicStorage) {
            this.defaultDownloadLocation = DownloadLocation.PUBLIC_DOWNLOADS;
        } else {
            this.defaultDownloadLocation = DownloadLocation.PRIVATE_INTERNAL;
        }

        Intent intent = new Intent(context, DownloadService.class);
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);

        // initialize request permission launcher
        requestPermissionLauncher = context.registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), isGranted -> {

            if (isGranted.containsKey(Manifest.permission.WRITE_EXTERNAL_STORAGE) && Boolean.FALSE.equals(isGranted.get(Manifest.permission.WRITE_EXTERNAL_STORAGE))) {
                Toast.makeText(context, "Unable to save download, storage permission denied", Toast.LENGTH_SHORT).show();
                return;
            }

            if (preDownloadInfo != null && isBound) {
                if (preDownloadInfo.isBlob) {
                    context.getFileWriterSharer().downloadBlobUrl(preDownloadInfo.url, preDownloadInfo.open);
                } else {
                    downloadService.startDownload(preDownloadInfo.url, preDownloadInfo.mimetype, preDownloadInfo.shouldSaveToGallery, preDownloadInfo.open, defaultDownloadLocation);
                }
                preDownloadInfo = null;
            }
        });
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
            if (requestRequiredPermission(new PreDownloadInfo(url, true))) {
                return;
            }

            context.getFileWriterSharer().downloadBlobUrl(url, false);
            return;
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

        startDownload(url, mimetype, false, false);
    }

    public void downloadFile(String url, boolean shouldSaveToGallery, boolean open) {
        if (TextUtils.isEmpty(url)) {
            Log.d(TAG, "downloadFile: Url empty!");
            return;
        }

        if (url.startsWith("blob:") && context != null) {
            if (requestRequiredPermission(new PreDownloadInfo(url, true))) {
                return;
            }
            context.getFileWriterSharer().downloadBlobUrl(url, open);
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
            if (requestRequiredPermission(new PreDownloadInfo(downloadUrl, mimetype, shouldSaveToGallery, open, false))) return;
            downloadService.startDownload(downloadUrl, mimetype, shouldSaveToGallery, open, defaultDownloadLocation);
        }
    }

    // Requests required permission depending on device's SDK version
    private boolean requestRequiredPermission(PreDownloadInfo preDownloadInfo) {

        List<String> permissions = new ArrayList<>();

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED &&
                defaultDownloadLocation == DownloadLocation.PUBLIC_DOWNLOADS) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }

        if (permissions.size() > 0) {
            this.preDownloadInfo = preDownloadInfo;
            requestPermissionLauncher.launch(permissions.toArray(new String[] {}));
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
