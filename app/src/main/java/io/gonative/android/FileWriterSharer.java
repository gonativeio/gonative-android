package io.gonative.android;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import io.gonative.gonative_core.AppConfig;
import io.gonative.gonative_core.LeanUtils;

public class FileWriterSharer {
    private static final String TAG = FileWriterSharer.class.getSimpleName();
    private static final long MAX_SIZE = 1024 * 1024 * 1024; // 1 gigabyte
    private static final String BASE64TAG = ";base64,";
    private final FileDownloader.DownloadLocation defaultDownloadLocation;
    private String downloadFilename;
    private boolean open = false;

    private static class FileInfo{
        public String id;
        public String name;
        public long size;
        public String mimetype;
        public String extension;
        public File savedFile;
        public Uri savedUri;
        public OutputStream fileOutputStream;
        public long bytesWritten;
    }

    private class JavascriptBridge {
        @JavascriptInterface
        public void postMessage(String jsonMessage) {
            Log.d(TAG, "got message " + jsonMessage);
            try {
                JSONObject json = new JSONObject(jsonMessage);
                String event = LeanUtils.optString(json, "event");
                if ("fileStart".equals(event)) {
                    onFileStart(json);
                } else if ("fileChunk".equals(event)) {
                    onFileChunk(json);
                } else if ("fileEnd".equals(event)) {
                    onFileEnd(json);
                } else if ("nextFileInfo".equals(event)) {
                    onNextFileInfo(json);
                } else {
                    Log.e(TAG, "Invalid event " + event);
                }
            } catch (JSONException e) {
                Log.e(TAG, "Error parsing message as json", e);
            } catch (IOException e) {
                Log.e(TAG, "IO Error", e);
            }
        }
    }

    private JavascriptBridge javascriptBridge;
    private MainActivity context;
    private Map<String, FileInfo> idToFileInfo;
    private String nextFileName;

    public FileWriterSharer(MainActivity context) {
        this.javascriptBridge = new JavascriptBridge();
        this.context = context;
        this.idToFileInfo = new HashMap<>();

        AppConfig appConfig = AppConfig.getInstance(this.context);
        if (appConfig.downloadToPublicStorage) {
            this.defaultDownloadLocation = FileDownloader.DownloadLocation.PUBLIC_DOWNLOADS;
        } else {
            this.defaultDownloadLocation = FileDownloader.DownloadLocation.PRIVATE_INTERNAL;
        }
    }

    public JavascriptBridge getJavascriptBridge() {
        return javascriptBridge;
    }

    public void downloadBlobUrl(String url, String filename, boolean open) {
        if (url == null || !url.startsWith("blob:")) {
            return;
        }

        this.downloadFilename = filename;
        this.open = open;

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            BufferedInputStream is = new BufferedInputStream(context.getAssets().open("BlobDownloader.js"));
            IOUtils.copy(is, baos);
            String js = baos.toString();
            context.runJavascript(js);
            js = "gonativeDownloadBlobUrl(" + LeanUtils.jsWrapString(url) + ")";
            context.runJavascript(js);
        } catch (IOException e) {
            Log.e(TAG, "Error reading asset", e);
        }
    }

    private void onFileStart(JSONObject message) throws IOException {
        String identifier = LeanUtils.optString(message, "id");
        if (identifier == null || identifier.isEmpty()) {
            Log.e(TAG, "Invalid file id");
            return;
        }

        String fileName;
        String extension = null;
        String type = null;

        if (!TextUtils.isEmpty(downloadFilename)) {
            extension = FileDownloader.getFilenameExtension(downloadFilename);
            if (!TextUtils.isEmpty(extension)) {
                if (Objects.equals(extension, downloadFilename)) {
                    fileName = "download";
                } else {
                    fileName = downloadFilename.substring(0, downloadFilename.length() - (extension.length() + 1));
                }
                type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
            } else {
                fileName = downloadFilename;
            }
        } else {
            fileName = LeanUtils.optString(message, "name");
            if (fileName == null || fileName.isEmpty()) {
                if (this.nextFileName != null) {
                    fileName = this.nextFileName;
                    this.nextFileName = null;
                } else {
                    fileName = "download";
                }
            }
        }

        long fileSize = message.optLong("size", -1);
        if (fileSize <= 0 || fileSize > MAX_SIZE) {
            Log.e(TAG, "Invalid file size");
            return;
        }

        if (TextUtils.isEmpty(type)) {
            type = LeanUtils.optString(message, "type");
            if (TextUtils.isEmpty(type)) {
                Log.e(TAG, "Invalid file type");
                return;
            }
        }

        if (TextUtils.isEmpty(extension)) {
            MimeTypeMap mimeTypeMap = MimeTypeMap.getSingleton();
            extension = mimeTypeMap.getExtensionFromMimeType(type);
        }

        final FileInfo info = new FileInfo();
        info.id = identifier;
        info.name = fileName;
        info.size = fileSize;
        info.mimetype = type;
        info.extension = extension;

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && defaultDownloadLocation == FileDownloader.DownloadLocation.PUBLIC_DOWNLOADS) {
            // request permissions
            context.getPermission(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, (permissions, grantResults) -> {
                try {
                    onFileStartAfterPermission(info, grantResults[0] == PackageManager.PERMISSION_GRANTED);
                    final String js = "gonativeGotStoragePermissions()";
                    context.runOnUiThread(() -> context.runJavascript(js));
                } catch (IOException e) {
                    Log.e(TAG, "IO Error", e);
                }
            });
        } else {
            onFileStartAfterPermission(info, true);
            final String js = "gonativeGotStoragePermissions()";
            context.runOnUiThread(() -> context.runJavascript(js));
        }
    }

    private void onFileStartAfterPermission(FileInfo info, boolean granted) throws IOException {
        if (granted && defaultDownloadLocation == FileDownloader.DownloadLocation.PUBLIC_DOWNLOADS) {
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                ContentResolver contentResolver = context.getApplicationContext().getContentResolver();
                Uri uri = FileDownloader.createExternalFileUri(contentResolver, info.name, info.mimetype, Environment.DIRECTORY_DOWNLOADS);
                if (uri != null) {
                    info.fileOutputStream = contentResolver.openOutputStream(uri);
                    info.savedUri = uri;
                }
            } else {
                info.savedFile = FileDownloader.createOutputFile(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), info.name, info.extension);
                info.fileOutputStream = new BufferedOutputStream(new FileOutputStream(info.savedFile));
            }
        } else {
            info.savedFile = FileDownloader.createOutputFile(context.getFilesDir(), info.name, info.extension);
            info.fileOutputStream = new BufferedOutputStream(new FileOutputStream(info.savedFile));
        }
        info.bytesWritten = 0;
        this.idToFileInfo.put(info.id, info);
    }

    private void onFileChunk(JSONObject message) throws IOException {
        String identifier = LeanUtils.optString(message, "id");
        if (identifier == null || identifier.isEmpty()) {
            return;
        }

        FileInfo fileInfo = this.idToFileInfo.get(identifier);
        if (fileInfo == null) {
            return;
        }

        String data = LeanUtils.optString(message, "data");
        if (data == null) {
            return;
        }

        int idx = data.indexOf(BASE64TAG);
        if (idx == -1) {
            return;
        }

        idx += BASE64TAG.length();
        byte[] chunk = Base64.decode(data.substring(idx), Base64.DEFAULT);

        if (fileInfo.bytesWritten + chunk.length > fileInfo.size) {
            Log.e(TAG, "Received too many bytes. Expected " + fileInfo.size);
            try {
                fileInfo.fileOutputStream.close();
                fileInfo.savedFile.delete();
                this.idToFileInfo.remove(identifier);
            } catch (Exception ignored) {

            }

            return;
        }

        fileInfo.fileOutputStream.write(chunk);
        fileInfo.bytesWritten += chunk.length;
    }

    private void onFileEnd(JSONObject message) throws IOException {
        String identifier = LeanUtils.optString(message, "id");
        if (identifier == null || identifier.isEmpty()) {
            Log.e(TAG, "Invalid identifier " + identifier + " for fileEnd");
            return;
        }

        final FileInfo fileInfo = this.idToFileInfo.get(identifier);
        if (fileInfo == null) {
            Log.e(TAG, "Invalid identifier " + identifier + " for fileEnd");
            return;
        }

        fileInfo.fileOutputStream.close();

        if (open) {
            context.runOnUiThread(() -> {
                if (fileInfo.savedUri == null && fileInfo.savedFile != null) {
                    fileInfo.savedUri = FileProvider.getUriForFile(context, context.getApplicationContext().getPackageName() + ".fileprovider", fileInfo.savedFile);
                }
                if (fileInfo.savedUri == null) return;

                Intent intent = getIntentToOpenFile(fileInfo.savedUri, fileInfo.mimetype);
                try {
                    context.startActivity(intent);
                } catch (ActivityNotFoundException e) {
                    String message1 = context.getResources().getString(R.string.file_handler_not_found);
                    Toast.makeText(context, message1, Toast.LENGTH_LONG).show();
                }
            });
        } else {
            String downloadCompleteMessage = fileInfo.name != null && !fileInfo.name.isEmpty()
                    ? String.format(context.getString(R.string.file_download_finished_with_name), fileInfo.name + '.' + fileInfo.extension)
                    : context.getString(R.string.file_download_finished);
            Toast.makeText(context, downloadCompleteMessage, Toast.LENGTH_SHORT).show();
        }
    }

    private Intent getIntentToOpenFile(Uri uri, String mimetype) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, mimetype);
        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

    private void onNextFileInfo(JSONObject message) {
        String name = LeanUtils.optString(message, "name");
        if (name == null || name.isEmpty()) {
            Log.e(TAG, "Invalid name for nextFileInfo");
            return;
        }
        this.nextFileName = name;
    }
}
