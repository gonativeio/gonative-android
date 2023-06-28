package io.gonative.android;

import android.app.Service;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import io.gonative.gonative_core.AppConfig;
import io.gonative.gonative_core.LeanUtils;

public class DownloadService extends Service {

    private static final String TAG = "DownloadService";
    private static final String EXTRA_DOWNLOAD_ID = "download_id";
    private static final String ACTION_CANCEL_DOWNLOAD = "action_cancel_download";
    private static final int BUFFER_SIZE = 4096;
    private static final int timeout = 5; // in seconds
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Map<Integer, DownloadTask> downloadTasks = new HashMap<>();
    private int downloadId = 0;
    private String userAgent;

    @Override
    public void onCreate() {
        super.onCreate();
        AppConfig appConfig = AppConfig.getInstance(this);
        this.userAgent = appConfig.userAgent;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent.getAction().equals(ACTION_CANCEL_DOWNLOAD)) {
            int id = intent.getIntExtra(EXTRA_DOWNLOAD_ID, 0);
            cancelDownload(id);
        }
        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return new DownloadBinder();
    }

    public class DownloadBinder extends Binder {
        public DownloadService getService() {
            return DownloadService.this;
        }
    }

    public void startDownload(String url, String filename, String mimetype, boolean shouldSaveToGallery, boolean open, FileDownloader.DownloadLocation location) {
        DownloadTask downloadTask = new DownloadTask(url, filename, mimetype, shouldSaveToGallery, open, location);
        downloadTasks.put(downloadTask.getId(), downloadTask);
        downloadTask.startDownload();
    }

    public void cancelDownload(int downloadId) {
        DownloadTask downloadTask = downloadTasks.get(downloadId);
        if (downloadTask != null && downloadTask.isDownloading()) {
            downloadTask.cancelDownload();
        }
    }

    public void handleDownloadUri(FileDownloader.DownloadLocation location, Uri uri, String mimeType, boolean shouldSaveToGallery, boolean open, String filename) {
        if (uri == null) return;
        if (location == FileDownloader.DownloadLocation.PUBLIC_DOWNLOADS) {

            if (shouldSaveToGallery) {
                addFileToGallery(uri);
            }

            if (open) {
                viewFile(uri, mimeType);
            } else {
                handler.post(() -> {
                    if (shouldSaveToGallery) {
                        Toast.makeText(this, R.string.file_download_finished_gallery, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, String.format(this.getString(R.string.file_download_finished_with_name), filename), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        } else {
            if (open) {
                viewFile(uri, mimeType);
            } else {
                handler.post(() -> Toast.makeText(this, String.format(this.getString(R.string.file_download_finished_with_name), filename), Toast.LENGTH_SHORT).show());
            }
        }
    }

    private void viewFile(Uri uri, String mimeType) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, mimeType);
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            String message = getResources().getString(R.string.file_handler_not_found);
            handler.post(() -> {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            });
        } catch (Exception ex) {
            Log.e(TAG, "viewFile: Exception: ", ex);
        }
    }

    private void addFileToGallery(Uri uri) {
        Log.d(TAG, "addFileToGallery: Adding to Albums . . .");
        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        mediaScanIntent.setData(uri);
        sendBroadcast(mediaScanIntent);
    }

    private class DownloadTask {
        private final int id;
        private final String url;
        private boolean isDownloading;
        private HttpURLConnection connection;
        private InputStream inputStream;
        private FileOutputStream outputStream;
        private File outputFile = null;
        private Uri downloadUri;
        private String filename;
        private String extension;
        private String mimetype;
        private boolean saveToGallery;
        private boolean openOnFinish;
        private final FileDownloader.DownloadLocation location;

        public DownloadTask(String url, String filename, String mimetype, boolean saveToGallery, boolean open, FileDownloader.DownloadLocation location) {
            this.id = downloadId++;
            this.url = url;
            this.filename = filename;
            this.mimetype = mimetype;
            this.isDownloading = false;
            this.saveToGallery = saveToGallery;
            this.openOnFinish = open;
            this.location = location;
        }

        public int getId() {
            return id;
        }

        public boolean isDownloading() {
            return isDownloading;
        }

        public void startDownload() {
            Log.d(TAG, "startDownload: Starting download");
            isDownloading = true;
            new Thread(() -> {
                Log.d(TAG, "startDownload: Thread started");
                try {
                    URL downloadUrl = new URL(url);
                    connection = (HttpURLConnection) downloadUrl.openConnection();
                    connection.setInstanceFollowRedirects(true);
                    connection.setRequestProperty("User-Agent", userAgent);
                    connection.setConnectTimeout(timeout * 1000);
                    connection.connect();

                    if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                        Log.e(TAG, "Server returned HTTP " + connection.getResponseCode()
                                + " " + connection.getResponseMessage());
                        isDownloading = false;
                        return;
                    }

                    String contentDisposition = connection.getHeaderField("Content-Disposition");

                    double fileSizeInMB = connection.getContentLength() / 1048576.0;
                    Log.d(TAG, "startDownload: File size in MB: " + fileSizeInMB);

                    if (connection.getHeaderField("Content-Type") != null)
                        mimetype = connection.getHeaderField("Content-Type");

                    if (!TextUtils.isEmpty(filename)) {
                       extension = FileDownloader.getFilenameExtension(filename);
                       if (TextUtils.isEmpty(extension)) {
                           extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimetype);
                       } else if (Objects.equals(filename, extension)) {
                           filename = "download";
                       } else {
                           filename = filename.substring(0, filename.length() - (extension.length() + 1));
                           mimetype = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
                       }
                    } else {
                        // guess file name and extension
                        String guessedName = LeanUtils.guessFileName(url,
                                contentDisposition,
                                mimetype);
                        int pos = guessedName.lastIndexOf('.');

                        if (pos == -1) {
                            filename = guessedName;
                            extension = "";
                        } else if (pos == 0) {
                            filename = "download";
                            extension = guessedName.substring(1);
                        } else {
                            filename = guessedName.substring(0, pos);
                            extension = guessedName.substring(pos + 1);
                        }

                        if (!TextUtils.isEmpty(extension)) {
                            // Update mimetype based on final filename extension
                            mimetype = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
                        }
                    }

                    if (location == FileDownloader.DownloadLocation.PRIVATE_INTERNAL &&
                            !TextUtils.isEmpty(contentDisposition) &&
                            contentDisposition.startsWith("inline"))
                        this.openOnFinish = true;

                    if (location == FileDownloader.DownloadLocation.PUBLIC_DOWNLOADS) {
                        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                            ContentResolver contentResolver = getApplicationContext().getContentResolver();
                            if (saveToGallery && mimetype.contains("image")) {
                                downloadUri = FileDownloader.createExternalFileUri(contentResolver, filename, mimetype, Environment.DIRECTORY_PICTURES);
                            } else {
                                downloadUri = FileDownloader.createExternalFileUri(contentResolver, filename, mimetype, Environment.DIRECTORY_DOWNLOADS);
                                saveToGallery = false;
                            }
                            if (downloadUri != null) {
                                outputStream = (FileOutputStream) contentResolver.openOutputStream(downloadUri);
                            }
                        } else {
                            if (saveToGallery) {
                                outputFile = FileDownloader.createOutputFile(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), filename, extension);
                            } else {
                                outputFile = FileDownloader.createOutputFile(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), filename, extension);
                            }
                            outputStream = new FileOutputStream(outputFile);
                        }
                    } else {
                        this.openOnFinish = true;
                        outputFile = FileDownloader.createOutputFile(getFilesDir(), filename, extension);
                        outputStream = new FileOutputStream(outputFile);
                    }

                    int fileLength = connection.getContentLength();
                    inputStream = connection.getInputStream();

                    byte[] buffer = new byte[BUFFER_SIZE];
                    int bytesRead;
                    int bytesDownloaded = 0;

                    while ((bytesRead = inputStream.read(buffer)) != -1 && isDownloading) {
                        outputStream.write(buffer, 0, bytesRead);
                        bytesDownloaded += bytesRead;
                        int progress = (int) (bytesDownloaded * 100 / fileLength);
                        Log.d(TAG, "startDownload: Download progress: " + progress);
                    }
                    if (!isDownloading && outputFile != null) {
                        outputFile.delete();
                        outputFile = null;
                    }
                } catch (IOException e) {
                    Log.e(TAG, "startDownload: ", e);
                } finally {
                    try {
                        if (inputStream != null) inputStream.close();
                        if (outputStream != null) outputStream.close();
                        if (connection != null) connection.disconnect();
                    } catch (IOException e) {
                        Log.e(TAG, "startDownload: ", e);
                    }
                    isDownloading = false;
                    if (downloadUri == null && outputFile != null) {
                        downloadUri = FileProvider.getUriForFile(DownloadService.this, DownloadService.this.getApplicationContext().getPackageName() + ".fileprovider", outputFile);
                    }
                    handleDownloadUri(location, downloadUri, mimetype, saveToGallery, openOnFinish, filename + "." + extension);
                }
            }).start();
        }

        public void cancelDownload() {
            isDownloading = false;
            Toast.makeText(DownloadService.this, getString(R.string.download_canceled) + " " + filename, Toast.LENGTH_SHORT).show();
        }
    }
}