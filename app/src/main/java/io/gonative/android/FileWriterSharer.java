package io.gonative.android;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.support.v4.content.FileProvider;
import android.util.Base64;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class FileWriterSharer {
    private static final String TAG = FileWriterSharer.class.getSimpleName();
    private static final long MAX_SIZE = 1024 * 1024 * 1024; // 1 gigabyte
    private static final String BASE64TAG = ";base64,";

    private static class FileInfo{
        public String id;
        public String name;
        public long size;
        public String mimetype;
        public File containerDir;
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
    }

    public JavascriptBridge getJavascriptBridge() {
        return javascriptBridge;
    }

    public void downloadBlobUrl(String url) {
        if (url == null || !url.startsWith("blob:")) {
            return;
        }

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

        File cacheDir = context.getCacheDir();
        File downloadsDir = new File(cacheDir, "downloads");
        File containerDir = new File(downloadsDir, UUID.randomUUID().toString());
        containerDir.mkdirs();

        File savedFile = new File(containerDir, fileName);
        OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(savedFile));

        FileInfo info = new FileInfo();
        info.id = identifier;
        info.name = fileName;
        info.size = fileSize;
        info.mimetype = type;
        info.containerDir = containerDir;
        info.savedFile = savedFile;
        info.fileOutputStream = outputStream;
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
            Log.e(TAG, "Invalid identiifer " + identifier + " for fileEnd");
            return;
        }

        final FileInfo fileInfo = this.idToFileInfo.get(identifier);
        if (fileInfo == null) {
            Log.e(TAG, "Invalid identiifer " + identifier + " for fileEnd");
            return;
        }

        fileInfo.fileOutputStream.close();

        context.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Uri content;
                try {
                    content = FileProvider.getUriForFile(context, FileDownloader.AUTHORITY, fileInfo.savedFile);
                } catch (IllegalArgumentException e) {
                    Log.e(TAG, "Unable to get content url from FileProvider", e);
                    return;
                }

                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(content, fileInfo.mimetype);
                intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                try {
                    context.startActivity(intent);
                } catch (ActivityNotFoundException e) {
                    String message = context.getResources().getString(R.string.file_handler_not_found);
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show();
                }
            }
        });
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
