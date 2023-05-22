package io.gonative.android;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
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

import io.gonative.gonative_core.AppConfig;
import io.gonative.gonative_core.LeanUtils;

public class FileWriterSharer {
    private static final String TAG = FileWriterSharer.class.getSimpleName();
    private static final long MAX_SIZE = 1024 * 1024 * 1024; // 1 gigabyte
    private static final String BASE64TAG = ";base64,";
    private final FileDownloader.DownloadLocation defaultDownloadLocation;
    private boolean open = false;

    private static class FileInfo{
        public String id;
        public String name;
        public long size;
        public String mimetype;
        public String extension;
        public File savedFile;
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

    public void downloadBlobUrl(String url, boolean open) {
        if (url == null || !url.startsWith("blob:")) {
            return;
        }

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

        String fileName = LeanUtils.optString(message, "name");
        if (fileName == null || fileName.isEmpty()) {
            if (this.nextFileName != null) {
                fileName = this.nextFileName;
                this.nextFileName = null;
            } else {
                fileName = "download";
            }
        }

        long fileSize = message.optLong("size", -1);
        if (fileSize <= 0 || fileSize > MAX_SIZE) {
            Log.e(TAG, "Invalid file size");
            return;
        }

        String type = LeanUtils.optString(message, "type");
        if (type == null || type.isEmpty()) {
            Log.e(TAG, "Invalid file type");
            return;
        }

        MimeTypeMap mimeTypeMap = MimeTypeMap.getSingleton();
        String extension = mimeTypeMap.getExtensionFromMimeType(type);

        final FileInfo info = new FileInfo();
        info.id = identifier;
        info.name = fileName;
        info.size = fileSize;
        info.mimetype = type;
        info.extension = extension;

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
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
        if (granted) {

            File downloadsDir;
            if (defaultDownloadLocation == FileDownloader.DownloadLocation.PUBLIC_DOWNLOADS) {
                downloadsDir =  Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            } else {
                downloadsDir =  context.getFilesDir();
            }

            if (downloadsDir != null) {
                int appendNum = 0;
                File outputFile = new File(downloadsDir, info.name + "." + info.extension);
                while (outputFile.exists()) {
                    appendNum++;
                    outputFile = new File(downloadsDir, info.name + "(" + appendNum +
                            ")" + "." +info.extension);
                }

                info.savedFile = outputFile;
            }
        }

        info.fileOutputStream = new BufferedOutputStream(new FileOutputStream(info.savedFile));
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
                Intent intent = getIntentToOpenFile(fileInfo.savedFile, fileInfo.mimetype);
                try {
                    context.startActivity(intent);
                } catch (ActivityNotFoundException e) {
                    String message1 = context.getResources().getString(R.string.file_handler_not_found);
                    Toast.makeText(context, message1, Toast.LENGTH_LONG).show();
                }
            });
        } else {
            Toast.makeText(context, context.getString(R.string.file_download_finished), Toast.LENGTH_SHORT).show();
        }
    }

    private Intent getIntentToOpenFile(File file, String mimetype) {
        Uri content;
        try {
            content = FileProvider.getUriForFile(context, context.getApplicationContext().getPackageName() + ".fileprovider", file);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Unable to get content url from FileProvider", e);
            return null;
        }

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(content, mimetype);
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
