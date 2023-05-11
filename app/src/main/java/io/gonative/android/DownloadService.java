package io.gonative.android;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import io.gonative.gonative_core.AppConfig;
import io.gonative.gonative_core.LeanUtils;

public class DownloadService extends Service {

    public static final boolean enableDownloadNotification = false; // TODO transfer to AppConfig

    private static final String TAG = "DownloadService";
    private static final String CHANNEL_ID = "gonative.share.downloads";
    private static final String CHANNEL_NAME = "Downloads";
    private static final String EXTRA_DOWNLOAD_ID = "download_id";
    private static final String ACTION_CANCEL_DOWNLOAD = "action_cancel_download";
    private static final int BUFFER_SIZE = 4096;
    private static final int ICON_DOWNLOAD_IN_PROGRESS = android.R.drawable.stat_sys_download;
    private static final int ICON_DOWNLOAD_DONE = android.R.drawable.stat_sys_download_done;
    private static final int timeout = 5; // in seconds
    private final Handler handler = new Handler(Looper.getMainLooper());

    private NotificationManager notificationManager;
    private NotificationCompat.Builder notificationBuilder;

    private final Map<Integer, DownloadTask> downloadTasks = new HashMap<>();
    private int notificationId = 0;
    private String userAgent;

    @Override
    public void onCreate() {
        super.onCreate();
        AppConfig appConfig = AppConfig.getInstance(this);
        this.userAgent = appConfig.userAgent;

        if (enableDownloadNotification) {
            notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        }
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

    public void startDownload(String url, String mimetype, boolean shouldSaveToGallery, boolean open) {
        DownloadTask downloadTask = new DownloadTask(url, mimetype, shouldSaveToGallery, open);
        downloadTasks.put(downloadTask.getId(), downloadTask);
        downloadTask.startDownload();
    }

    public void cancelDownload(int downloadId) {
        DownloadTask downloadTask = downloadTasks.get(downloadId);
        if (downloadTask != null && downloadTask.isDownloading()) {
            downloadTask.cancelDownload();
        }
    }

    public void handleDownloadUri(File file, String mimeType, boolean shouldSaveToGallery, boolean open, String filename) {
        Uri uri = FileProvider.getUriForFile(DownloadService.this, DownloadService.this.getApplicationContext().getPackageName() + ".fileprovider", file);
        if (shouldSaveToGallery) {
            addFileToGallery(uri);
        }
        if (open) {
            viewFile(uri, mimeType);
        } else {
            handler.post(() -> {
                if (shouldSaveToGallery) {
                    Toast.makeText(this, "Image saved to Gallery", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, filename + " downloaded", Toast.LENGTH_SHORT).show();
                }
            });
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
        private String filename;
        private String extension;
        private String mimetype;
        private final boolean saveToGallery;
        private final boolean openOnFinish;

        public DownloadTask(String url, String mimetype, boolean saveToGallery, boolean open) {
            this.id = notificationId++;
            this.url = url;
            this.mimetype = mimetype;
            this.isDownloading = false;
            this.saveToGallery = saveToGallery;
            this.openOnFinish = open;
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

            if (enableDownloadNotification) {
                createNotification();
            }

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
                        updateNotification(getString(R.string.download_disconnected), 0, true);
                        isDownloading = false;
                        return;
                    }

                    // guess file name and extension
                    if (connection.getHeaderField("Content-Type") != null)
                        mimetype = connection.getHeaderField("Content-Type");
                    String guessedName = LeanUtils.guessFileName(url.toString(),
                            connection.getHeaderField("Content-Disposition"),
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

                    handler.post(() -> {
                        Toast.makeText(DownloadService.this, "Downloading " + filename, Toast.LENGTH_SHORT).show();
                    });

                    File downloadDir;
                    if (saveToGallery) {
                        downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
                    } else {
                        downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    }

                    // check and update filename if already exist
                    filename = getUniqueFileName(filename + "." + extension, downloadDir);

                    int fileLength = connection.getContentLength();
                    inputStream = connection.getInputStream();
                    outputFile = new File(downloadDir, filename);
                    outputStream = new FileOutputStream(outputFile);

                    byte[] buffer = new byte[BUFFER_SIZE];
                    int bytesRead;
                    int bytesDownloaded = 0;

                    while ((bytesRead = inputStream.read(buffer)) != -1 && isDownloading) {
                        outputStream.write(buffer, 0, bytesRead);
                        bytesDownloaded += bytesRead;
                        int progress = (int) (bytesDownloaded * 100 / fileLength);
                        updateNotification(getString(R.string.download_in_progress), progress, false);
                    }
                    if (!isDownloading) {
                        outputFile.delete();
                    } else {
                        updateNotification(getString(R.string.file_download_finished), 100, false);
                    }

                } catch (IOException e) {
                    Log.e(TAG, "startDownload: ", e);
                    updateNotification(getString(R.string.file_download_error), 0, true);
                } finally {
                    try {
                        if (inputStream != null) inputStream.close();
                        if (outputStream != null) outputStream.close();
                        if (connection != null) connection.disconnect();
                    } catch (IOException e) {
                        Log.e(TAG, "startDownload: ", e);
                    }
                    isDownloading = false;

                    if (outputStream != null && outputFile != null && outputFile.exists())
                        handleDownloadUri(outputFile, mimetype, saveToGallery, openOnFinish, filename);
                }
            }).start();
        }

        private void createNotification() {
            if (!enableDownloadNotification || notificationManager == null) return;

            // Create notification builder
            notificationBuilder =
                    new NotificationCompat.Builder(DownloadService.this, CHANNEL_ID)
                            .setSmallIcon(ICON_DOWNLOAD_IN_PROGRESS)
                            .setContentTitle(getString(R.string.file_download_title))
                            .setContentText(url)
                            .setProgress(100, 0, true)
                            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                            .addAction(R.drawable.ic_notification, "Cancel", createCancelDownloadPendingIntent())
                            .setOngoing(true)
                            .setOnlyAlertOnce(true);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                int importance = NotificationManager.IMPORTANCE_DEFAULT;
                NotificationChannel channel = new NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance);
                notificationManager.createNotificationChannel(channel);
            }

            notificationManager.notify(id, notificationBuilder.build());
        }

        public void cancelDownload() {
            isDownloading = false;
            notificationManager.cancel(id);
            Toast.makeText(DownloadService.this, getString(R.string.download_canceled) + " " + filename, Toast.LENGTH_SHORT).show();
        }

        private void updateNotification(String title, int progress, boolean downloadFailed) {
            if (!enableDownloadNotification || notificationManager == null || notificationBuilder == null) return;

            notificationBuilder.setContentTitle(title)
                    .setContentText(filename);

            if (downloadFailed) {
                notificationBuilder.setSmallIcon(ICON_DOWNLOAD_DONE)
                        .setOngoing(false)
                        .setProgress(100, progress, false);
                removeNotificationAction(notificationBuilder);
            } else if (progress < 100) {
                notificationBuilder.setProgress(100, progress, false);
            } else {
                notificationBuilder.setSmallIcon(ICON_DOWNLOAD_DONE)
                        .setOngoing(false)
                        .setAutoCancel(true)
                        .setProgress(100, progress, false)
                        .setContentIntent(createOpenFilePendingIntent());
                removeNotificationAction(notificationBuilder);
            }

            notificationManager.notify(id, notificationBuilder.build());
        }

        private void removeNotificationAction(NotificationCompat.Builder builder) {
            if (builder == null) return;
            try {
                //Use reflection clean up old actions
                Field f = builder.getClass().getDeclaredField("mActions");
                f.setAccessible(true);
                f.set(builder, new ArrayList<NotificationCompat.Action>());
            } catch (NoSuchFieldException e) {
                // no field
            } catch (IllegalAccessException e) {
                // wrong types
            }
        }

        public String getUniqueFileName(String fileName, File dir) {
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

        private PendingIntent createOpenFilePendingIntent() {
            if (outputFile == null || !outputFile.exists()) return null;
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                Uri uri = FileProvider.getUriForFile(DownloadService.this, getApplicationContext().getPackageName() + ".fileprovider", outputFile);
                intent.setDataAndType(uri, mimetype);
                intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    return PendingIntent.getActivity(DownloadService.this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
                } else {
                    return PendingIntent.getActivity(DownloadService.this, 0, intent, PendingIntent.FLAG_IMMUTABLE);
                }
            } catch (Exception ex) {
                Log.e(TAG, "createOpenFilePendingIntent: ", ex);
            }
            return null;
        }

        private PendingIntent createCancelDownloadPendingIntent() {
            Intent intent = new Intent(DownloadService.this, DownloadService.class);
            intent.setAction(ACTION_CANCEL_DOWNLOAD);
            intent.putExtra(EXTRA_DOWNLOAD_ID, id);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                return PendingIntent.getService(DownloadService.this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
            } else {
                return PendingIntent.getService(DownloadService.this, 0, intent, PendingIntent.FLAG_IMMUTABLE);
            }
        }
    }
}