package io.gonative.android;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
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
import androidx.multidex.BuildConfig;

import java.io.BufferedInputStream;
import java.io.File;
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

    private static final String TAG = DownloadListener.class.getName();
    static final String AUTHORITY = BuildConfig.APPLICATION_ID + ".fileprovider";
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
    private Uri saveUri;
    private boolean shouldSaveToGallery;
    private String filename = "download";
    private static final int timeout = 5; // in seconds
    
    ExecutorService executor = Executors.newSingleThreadExecutor();
    Handler handler = new Handler(Looper.getMainLooper());

    FileDownloader(MainActivity context) {
        this.context = context;
        this.pendingExternalDownloads = new HashMap<>();

        AppConfig appConfig = AppConfig.getInstance(this.context);
        if (appConfig.downloadToPublicStorage) {
            this.defaultDownloadLocation = DownloadLocation.PUBLIC_DOWNLOADS;
        } else {
            this.defaultDownloadLocation = DownloadLocation.PRIVATE_INTERNAL;
        }

        initLauncher();
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
                    request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

                    enqueueBackgroundDownload(url, request);

                } catch (Exception e) {
                    Log.e(TAG, e.getMessage(), e);
                    Toast.makeText(context, e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                }

                return;
            } else {
                Log.w(TAG, "External storage is not mounted. Downloading internally.");
            }
        }
        initializeDownload(url, userAgent);
    }

    private void enqueueBackgroundDownload(String url, DownloadManager.Request request) {
        this.pendingExternalDownloads.put(url, request);
        this.context.getExternalStorageWritePermission();
    }

    public void gotExternalStoragePermissions(boolean granted) {
        if (granted) {
            Toast.makeText(this.context, R.string.starting_download, Toast.LENGTH_SHORT).show();

            DownloadManager downloadManager = (DownloadManager) this.context.getSystemService(Context.DOWNLOAD_SERVICE);
            if (downloadManager == null) {
                Log.e(TAG, "Error getting system download manager");
                return;
            }
            for (DownloadManager.Request request : this.pendingExternalDownloads.values()) {
                downloadManager.enqueue(request);
            }
            this.pendingExternalDownloads.clear();
        }
    }

    public void downloadFile(String url, boolean shouldSaveToGallery) {
        if (TextUtils.isEmpty(url)) {
            Log.d(TAG, "downloadFile: Url empty!");
            return;
        }

        this.shouldSaveToGallery = shouldSaveToGallery;
        onDownloadStart(url, null, null, null, -1);
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
                    String guessedName = LeanUtils.guessFileName(url.toString(),
                            connection.getHeaderField("Content-Disposition"),
                            this.mimetype);
                    int pos = guessedName.lastIndexOf('.');
                    String fileName;
                    if (pos == -1) {
                        fileName = guessedName;
                    } else if (pos == 0) {
                        fileName = "download";
                    } else {
                        fileName = guessedName.substring(0, pos);
                    }
    
                    if (shouldSaveToGallery) {
                        // Check runtime permission for this android versions
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                            this.filename = fileName;
                            handler.post(() -> createFileForImageWithPermission(fileName, mimetype));
                            return;
                        }
    
                        saveUri = createFileForImage(fileName, mimetype);
                        if (saveUri == null) {
                            Log.d(TAG, "initializeDownload: Cant create file, resetting download");
                            resetDownload();
                            return;
                        }
                        Log.d(TAG, "initializeDownload: Save path: " + saveUri.getPath());
                        handler.post(() -> {
                            startDownload();
                        });
                    } else {
                        handler.post(() -> {
                            openSafIntent(fileName, mimetype);
                        });
                    }
                } else {
                    Log.d(TAG, "initializeDownload: Failed to connect to url. Response code: " + responseCode);
                    handler.post(() -> {
                        Toast.makeText(context, context.getString(R.string.download_disconnected), Toast.LENGTH_LONG).show();
                    });
                    resetDownload();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error occurred while downloading file", e);
                handler.post(() -> {
                    Toast.makeText(context, e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
                });
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
        
        Toast.makeText(context, R.string.starting_download, Toast.LENGTH_SHORT).show();
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
                    output.write(data, 0, count);
                }
                
                output.flush();
                output.close();
                input.close();
                Log.d(TAG, "startDownload: Download Done!");
                
                handler.post(() -> {
                    Toast.makeText(context, R.string.download_complete, Toast.LENGTH_SHORT).show();
                    viewFile(saveUri, mimetype);
                });
                
            } catch (Exception e) {
                Log.e(TAG, "Error occurred while downloading file", e);
                handler.post(() -> {
                    Toast.makeText(context, e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
                });
                
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
            this.saveUri = createFileForImage(filename, mimetype);
            this.startDownload();
        }
    }
    
    private Uri createFileForImage(String filename, String mimetype) {
        ContentResolver resolver = context.getContentResolver();
        ContentValues fileDetails = new ContentValues();
        fileDetails.put(MediaStore.Video.Media.DISPLAY_NAME, filename);
        fileDetails.put(MediaStore.Video.Media.MIME_TYPE, mimetype);
        return resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, fileDetails);
    }

    private void openSafIntent(String fileName, String mimetype) {
        // Let user pick/create the file
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(mimetype);
        intent.putExtra(Intent.EXTRA_TITLE, fileName);
        
        directorySelectorActivityLauncher.launch(intent);
    }
    
    private void initLauncher() {
        directorySelectorActivityLauncher = context.registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        Intent data = result.getData();
                        if (data !=null) {
                            saveUri = data.getData();
                            startDownload();
                        }
                    }
                });
    
        requestPermissionLauncher =
                context.registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                    if (isGranted) {
                        Log.d(TAG, "initLauncher: Granted");
                        saveUri = createFileForImage(this.filename, mimetype);
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
        
        if (saveUri != null) {
            deleteFile(new File(saveUri.getPath()));
            saveUri = null;
        }
    }
    
    private void deleteFile(File file) {
        if (file == null) return;
        if (file.exists()) {
            if (file.delete()) Log.d(TAG, "deleteFile: File " + file.getPath() + " deleted.");
        }
    }
    
    private void viewFile(Uri uri, String mimeType) {
        try {
            if (shouldSaveToGallery) {
                addFileToGallery(uri);
            }
            
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, mimeType);
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            context.startActivity(intent);
        } catch (Exception ex) {
            Log.e(TAG, "viewFile: Exception: ", ex);
        }
    }
    
    private void addFileToGallery(Uri uri) {
        Log.d(TAG, "addFileToGallery: Adding to Albums ...");
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
