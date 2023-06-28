package io.gonative.android;

import android.Manifest;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.DownloadListener;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.gonative.gonative_core.AppConfig;
import io.gonative.gonative_core.LeanUtils;

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
                    context.getFileWriterSharer().downloadBlobUrl(preDownloadInfo.url, preDownloadInfo.filename, preDownloadInfo.open);
                } else {
                    downloadService.startDownload(preDownloadInfo.url, preDownloadInfo.filename, preDownloadInfo.mimetype, preDownloadInfo.shouldSaveToGallery, preDownloadInfo.open, defaultDownloadLocation);
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

        // get filename from content disposition
        String guessFilename = null;
        if (!TextUtils.isEmpty(contentDisposition)) {
             guessFilename = LeanUtils.guessFileName(url, contentDisposition, mimetype);
        }

        if (url.startsWith("blob:") && context != null) {

            boolean openAfterDownload = defaultDownloadLocation == DownloadLocation.PRIVATE_INTERNAL;

            if (requestRequiredPermission(new PreDownloadInfo(url, guessFilename, true, openAfterDownload))) {
                return;
            }

            context.getFileWriterSharer().downloadBlobUrl(url, guessFilename, openAfterDownload);
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

        startDownload(url, guessFilename,  mimetype, false, false);
    }

    public void downloadFile(String url, String filename, boolean shouldSaveToGallery, boolean open) {
        if (TextUtils.isEmpty(url)) {
            Log.d(TAG, "downloadFile: Url empty!");
            return;
        }

        if (url.startsWith("blob:") && context != null) {

            if (defaultDownloadLocation == DownloadLocation.PRIVATE_INTERNAL) {
                open = true;
            }

            if (requestRequiredPermission(new PreDownloadInfo(url, filename, true, open))) {
                return;
            }
            context.getFileWriterSharer().downloadBlobUrl(url, filename, open);
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

        startDownload(url, filename, mimetype, shouldSaveToGallery, open);
    }

    private void startDownload(String downloadUrl, String filename, String mimetype, boolean shouldSaveToGallery, boolean open) {
        if (isBound) {
            if (requestRequiredPermission(new PreDownloadInfo(downloadUrl, filename, mimetype, shouldSaveToGallery, open, false))) return;
            downloadService.startDownload(downloadUrl, filename, mimetype, shouldSaveToGallery, open, defaultDownloadLocation);
        }
    }

    // Requests required permission depending on device's SDK version
    private boolean requestRequiredPermission(PreDownloadInfo preDownloadInfo) {

        List<String> permissions = new ArrayList<>();

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
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

    public static Uri createExternalFileUri(ContentResolver contentResolver, String filename, String mimetype, String environment) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, mimetype);

        if (Objects.equals(environment, Environment.DIRECTORY_PICTURES)) {
            contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES);
            return contentResolver.insert(MediaStore.Images.Media.getContentUri("external"), contentValues);
        } else if (Objects.equals(environment, Environment.DIRECTORY_DOWNLOADS)) {
            contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            return contentResolver.insert(MediaStore.Files.getContentUri("external"), contentValues);
        }
        return null;
    }

    public static File createOutputFile(File dir, String filename, String extension) {
        return new File(dir, FileDownloader.getUniqueFileName(filename + "." + extension, dir));
    }

    public static String getUniqueFileName(String fileName, File dir) {
        File file = new File(dir, fileName);

        if (!file.exists()) {
            return fileName;
        }

        int count = 1;
        String nameWithoutExt = fileName.substring(0, fileName.lastIndexOf('.'));
        String ext = fileName.substring(fileName.lastIndexOf('.'));
        String newFileName = nameWithoutExt + "_" + count + ext;
        file = new File(dir, newFileName);

        while (file.exists()) {
            count++;
            newFileName = nameWithoutExt + "_" + count + ext;
            file = new File(dir, newFileName);
        }

        return file.getName();
    }

    public static String getFilenameExtension(String name) {
        int pos = name.lastIndexOf('.');
        if (pos == -1) {
            return null;
        } else if (pos == 0) {
            return name;
        } else {
            return name.substring(pos + 1);
        }
    }

    private static class PreDownloadInfo {
        String url;
        String filename;
        String mimetype;
        boolean shouldSaveToGallery;
        boolean open;
        boolean isBlob;

        public PreDownloadInfo(String url, String filename, String mimetype, boolean shouldSaveToGallery, boolean open, boolean isBlob) {
            this.url = url;
            this.filename = filename;
            this.mimetype = mimetype;
            this.shouldSaveToGallery = shouldSaveToGallery;
            this.open = open;
            this.isBlob = isBlob;
        }

        public PreDownloadInfo(String url, String filename, boolean isBlob, boolean open) {
            this.url = url;
            this.filename = filename;
            this.isBlob = isBlob;
            this.open = open;
        }
    }
}
