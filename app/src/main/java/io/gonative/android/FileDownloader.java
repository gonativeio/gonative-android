package io.gonative.android;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.gonative.android.library.AppConfig;
import io.gonative.gonative_core.LeanUtils;

/**
 * Created by weiyin on 6/24/14.
 */
public class FileDownloader implements DownloadListener {
    private enum DownloadLocation {
        PUBLIC_DOWNLOADS, PRIVATE_INTERNAL
    }

    private static final String TAG = FileDownloader.class.getName();
    private MainActivity context;
    private UrlNavigation urlNavigation;
    private String lastDownloadedUrl;
    private DownloadLocation defaultDownloadLocation;
    private Map<String, DownloadManager.Request> pendingExternalDownloads;
    ActivityResultLauncher<Intent> directorySelectorActivityLauncher;
    private ActivityResultLauncher<String> requestPermissionLauncher;
    
    private HttpURLConnection connection;
    private URL url;
    private String mimetype = "*/*";
    
    private String downloadUrl;
    private Uri saveUri;
    private boolean shouldSaveToGallery;
    private boolean isDownloadFromWeb;
    private String filename = "download";
    private String extension = "";
    private static final int timeout = 5; // in seconds
    private DownloadManager downloadManager;
    private Map<Long, String> pendingDownloadNotification;
    
    ExecutorService executor = Executors.newSingleThreadExecutor();
    Handler handler = new Handler(Looper.getMainLooper());

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
        initLauncher();
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
                    this.mimetype = guessedMimeType;
                }
            }
        } else {
            this.mimetype = mimetype;
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

        this.isDownloadFromWeb = true;
        initializeDownload(url, userAgent);
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

    public void downloadFile(String url, boolean shouldSaveToGallery) {
        if (TextUtils.isEmpty(url)) {
            Log.d(TAG, "downloadFile: Url empty!");
            return;
        }

        if (url.startsWith("blob:") && context != null) {
            context.getFileWriterSharer().downloadBlobUrl(url);
            return;
        }

        this.downloadUrl = url;
        this.shouldSaveToGallery = shouldSaveToGallery;
        this.isDownloadFromWeb = false;
        
        setupAppDownload();
    }
    
    private void setupAppDownload() {
        String userAgent = AppConfig.getInstance(context).userAgent;
        
        MimeTypeMap mimeTypeMap = MimeTypeMap.getSingleton();
        String extension = MimeTypeMap.getFileExtensionFromUrl(downloadUrl);
        if (extension != null && !extension.isEmpty()) {
            String guessedMimeType = mimeTypeMap.getMimeTypeFromExtension(extension);
            if (guessedMimeType != null) {
                this.mimetype = guessedMimeType;
            }
        }
        
        initializeDownload(downloadUrl, userAgent);
    }

    private void initializeDownload(String downloadUrl, String userAgent) {
        executor.execute(() -> {
            try {
                url = new URL(downloadUrl);
                connection = (HttpURLConnection) url.openConnection();
                connection.setInstanceFollowRedirects(true);
                connection.setRequestProperty("User-Agent", userAgent);
                connection.setConnectTimeout(timeout * 1000);
                
                Log.d(TAG, "initializeDownload: Connecting to download url ...");
                connection.connect();

                int responseCode = connection.getResponseCode();
                Log.d(TAG, "initializeDownload: Connected - response code: " + responseCode);
                if (responseCode < 400) {
                    
                    // guess file name and extension
                    if (connection.getHeaderField("Content-Type") != null)
                        this.mimetype = connection.getHeaderField("Content-Type");
                    String guessedName = LeanUtils.guessFileName(url.toString(),
                            connection.getHeaderField("Content-Disposition"),
                            this.mimetype);
                    int pos = guessedName.lastIndexOf('.');
                    String fileName;
                    if (pos == -1) {
                        fileName = guessedName;
                        this.extension = "";
                    } else if (pos == 0) {
                        fileName = "download";
                        this.extension = guessedName.substring(1);
                    } else {
                        fileName = guessedName.substring(0, pos);
                        this.extension = guessedName.substring(pos + 1);
                    }
                    if (shouldSaveToGallery || isDownloadFromWeb) {

                        // Check runtime permission for this android versions
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                            this.filename = fileName;
                            handler.post(() -> createFileForImageWithPermission(fileName, mimetype));
                            return;
                        }

                        saveUri = createFile(fileName, this.mimetype);
                        if (saveUri == null) {
                            Log.d(TAG, "initializeDownload: Cant create file, resetting download");
                            resetDownload();
                            return;
                        }
                        Log.d(TAG, "initializeDownload: Save path: " + saveUri.getPath());
                        handler.post(this::startDownload);
 
                    } else {
                        handler.post(() -> openStorageAccessFrameworkIntent(fileName, mimetype, extension));
                    }
                } else {
                    Log.d(TAG, "initializeDownload: Failed to connect to url. Response code: " + responseCode);
                    handler.post(() -> Toast.makeText(context, context.getString(R.string.download_disconnected), Toast.LENGTH_LONG).show());
                    resetDownload();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error occurred while downloading file", e);
                handler.post(() -> Toast.makeText(context, e.getLocalizedMessage(), Toast.LENGTH_SHORT).show());
                resetDownload();
            }
        });
    }
    
    private void startDownload() {
        if (connection == null) {
            Log.d(TAG, "continueDownloadFile: HttpConnection not created.");
            return;
        }
        
        if (saveUri == null) {
            Log.d(TAG, "continueDownloadFile: File path not created");
            return;
        }
        
        executor.execute(() -> {
            try {
                int count;
                int lengthOfFile = connection.getContentLength();

                InputStream input = new BufferedInputStream(url.openStream(), 8192);
                OutputStream output = context.getContentResolver().openOutputStream(saveUri);
                byte[] data = new byte[1024];
                long total = 0;
                
                while ((count = input.read(data)) != -1) {
                    total += count;
                    Log.d(TAG, "startDownload: Progress: " + (int) ((total * 100) / lengthOfFile));
                    
                    // writing data to file
                    output.write(data, 0, count);
                }
                
                output.flush();
                output.close();
                input.close();
                Log.d(TAG, "startDownload: Download Done!");
                handler.post(() -> {
                    //Open File
                    viewFile(saveUri, mimetype);
                });
                
            } catch (Exception e) {
                Log.e(TAG, "Error occurred while downloading file", e);
                handler.post(() -> Toast.makeText(context, e.getLocalizedMessage(), Toast.LENGTH_SHORT).show());
                
                resetDownload();
            }
        });
    }
    
    private void createFileForImageWithPermission(String filename, String mimetype) {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "createFileForImageWithPermission: Requesting permission");
            requestPermissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
        } else {
            Log.d(TAG, "createFileForImageWithPermission: Permission already granted, creating file");
            this.saveUri = createFile(filename, mimetype);
            if (saveUri == null) {
                Log.d(TAG, "initializeDownload: Cant create file, resetting download");
                resetDownload();
                return;
            }
            
            this.startDownload();
        }
    }
    
    private Uri createFile(String filename, String mimetype) {
        if (isDownloadFromWeb)
            return createFileForWebDownload(filename, mimetype);
        return createFileForImage(filename, mimetype);
    }
    
    private Uri createFileForImage(String filename, String mimetype) {
        ContentResolver resolver = context.getContentResolver();
        ContentValues fileDetails = new ContentValues();
        fileDetails.put(MediaStore.Video.Media.DISPLAY_NAME, filename);
        fileDetails.put(MediaStore.Video.Media.MIME_TYPE, mimetype);
        return resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, fileDetails);
    }
    
    private Uri createFileForWebDownload(String filename, String mimetype) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentResolver resolver = context.getContentResolver();
            ContentValues fileDetails = new ContentValues();
            fileDetails.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
            fileDetails.put(MediaStore.MediaColumns.MIME_TYPE, mimetype);
            return resolver.insert(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL), fileDetails);
        } else {
            try {
                File downloadDir = new File(context.getCacheDir(), "downloads");
                if (!downloadDir.mkdirs()) {
                    Log.v(TAG, "Download directory " + downloadDir + " exists");
                }
                Log.d(TAG, "createFileForWebDownload: extension: " + extension);
                
                File file = File.createTempFile(filename, extension, downloadDir);
                return FileProvider.getUriForFile(context, context.getApplicationContext().getPackageName() + ".fileprovider", file);
            } catch (IOException e) {
                Log.e(TAG, "createFileForWebDownload:", e);
                return null;
            }
        }
    }
    
    private void openStorageAccessFrameworkIntent(String fileName, String mimetype, String extension) {
        // Let user pick/create the file
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(mimetype);
        intent.putExtra(Intent.EXTRA_TITLE, fileName + "." + extension);
        
        directorySelectorActivityLauncher.launch(intent);
    }
    
    private void initLauncher() {
        directorySelectorActivityLauncher = context.registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        Intent data = result.getData();
                        if (data == null) {
                            Log.d(TAG, "initLauncher: File not created");
                            resetDownload();
                            return;
                        }
                        saveUri = data.getData();
                        startDownload();
                    }
                });
    
        requestPermissionLauncher =
                context.registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                    if (isGranted) {
                        Log.d(TAG, "initLauncher: Granted");
                        saveUri = createFile(this.filename, mimetype);
                        
                        if (saveUri == null) {
                            Log.d(TAG, "initializeDownload: Cant create file, resetting download");
                            resetDownload();
                            return;
                        }
                        
                        startDownload();
                    } else {
                        Log.d(TAG, "initLauncher: Permission Denied");
                    }
                });
    }
    
    private void resetDownload() {
        this.mimetype = "*/*";
        this.connection = null;
        this.url = null;
        this.filename = "download";
        this.extension = "";
        
        if (saveUri != null) {
            deleteFile(new File(saveUri.getPath()));
            saveUri = null;
        }
    }
    
    private void deleteFile(File file) {
        if (file == null) return;
        if (file.exists()) {
            if (file.delete()) {
                Log.d(TAG, "deleteFile: File " + file.getPath() + " deleted.");
            } else {
                Log.d(TAG, "deleteFile: Unable to delete file " + file.getPath());
            }
        }
    }
    
    private void viewFile(Uri uri, String mimeType) {
        try {
            if (shouldSaveToGallery) {
                addFileToGallery(uri);
            }
            
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, mimeType);
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            String message = context.getResources().getString(R.string.file_handler_not_found);
            Toast.makeText(context, message, Toast.LENGTH_LONG).show();
        } catch (Exception ex) {
            Log.e(TAG, "viewFile: Exception: ", ex);
        }
    }
    
    private void addFileToGallery(Uri uri) {
        Log.d(TAG, "addFileToGallery: Adding to Albums . . .");
        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        mediaScanIntent.setData(uri);
        context.sendBroadcast(mediaScanIntent);
    }

    public String getLastDownloadedUrl() {
        return lastDownloadedUrl;
    }

    public void setUrlNavigation(UrlNavigation urlNavigation) {
        this.urlNavigation = urlNavigation;
    }
}
