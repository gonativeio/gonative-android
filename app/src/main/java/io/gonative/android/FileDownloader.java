package io.gonative.android;

import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.support.v4.content.FileProvider;
import android.util.Log;
import android.webkit.DownloadListener;
import android.webkit.URLUtil;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Created by weiyin on 6/24/14.
 */
public class FileDownloader implements DownloadListener{
    private static final String TAG = DownloadListener.class.getName();
    public static final String AUTHORITY = BuildConfig.APPLICATION_ID + ".fileprovider";
    private Context context;
    private ProgressDialog progressDialog;
    private String lastDownloadedUrl;

    public FileDownloader(Context context) {
        this.context = context;
    }

    private FileDownloader() {
    }

    @Override
    public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimetype, long contentLength) {
        lastDownloadedUrl = url;
        DownloadFileParams param = new DownloadFileParams(url, userAgent, mimetype, contentLength);
        new DownloadFileTask().execute(param);
    }

    private class DownloadFileParams {
        public String url;
        public String userAgent;
        public String mimetype;
        public long contentLength;

        private DownloadFileParams(String url, String userAgent, String mimetype, long contentLength) {
            this.url = url;
            this.userAgent = userAgent;
            this.mimetype = mimetype;
            this.contentLength = contentLength;
        }
    }

    private class DownloadFileResult {
        public File file;
        public String mimetype;

        private DownloadFileResult(File file, String mimetype) {
            this.file = file;
            this.mimetype = mimetype;
        }
    }

    private class DownloadFileTask extends AsyncTask<DownloadFileParams, Integer, DownloadFileResult> {
        @Override
        protected void onPreExecute() {
            progressDialog = new ProgressDialog(context);
            progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            progressDialog.setTitle(R.string.download);
            progressDialog.setIndeterminate(false);
            progressDialog.setMax(10000);
            progressDialog.setProgressNumberFormat(null);
            progressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    DownloadFileTask.this.cancel(true);
                }
            });
            progressDialog.show();
        }

        @Override
        protected DownloadFileResult doInBackground(DownloadFileParams... params) {
            HttpURLConnection connection = null;
            URL url = null;
            DownloadFileParams param = params[0];
            try {
                url = new URL(param.url);
            } catch (MalformedURLException e) {
                Log.e(TAG, e.getMessage(), e);
                return null;
            }

            if (param.contentLength > 0) publishProgress(0);

            try {
                connection = (HttpURLConnection) url.openConnection();
                connection.setInstanceFollowRedirects(true);
                connection.setRequestProperty("User-Agent", param.userAgent);

                connection.connect();
                if (connection.getResponseCode() < 400) {
                    File downloadDir = new File(context.getCacheDir(), "downloads");
                    downloadDir.mkdirs();

                    // guess file name and extension
                    String guessedName = URLUtil.guessFileName(url.toString(),
                            connection.getHeaderField("Content-Disposition"),
                            param.mimetype);
                    int pos = guessedName.lastIndexOf('.');
                    String filename;
                    String extension;
                    if (pos == -1) {
                        filename = guessedName;
                        extension = "";
                    } else if (pos == 0) {
                        filename = "download";
                        extension = guessedName.substring(1);
                    } else {
                        filename = guessedName.substring(0, pos);
                        extension = guessedName.substring(pos+1);
                    }

                    if (!extension.isEmpty()) extension = "." + extension;

                    File downloadFile = File.createTempFile(filename, extension, downloadDir);

                    downloadFile.createNewFile();
                    FileOutputStream os = new FileOutputStream(downloadFile);
                    byte buffer[] = new byte[16 * 1024];

                    InputStream is = connection.getInputStream();

                    long totalLen = 0;
                    int len1 = 0;
                    while ((len1 = is.read(buffer)) > 0) {
                        os.write(buffer, 0, len1);
                        totalLen += len1;

                        if (param.contentLength > 0){
                            publishProgress((int) (totalLen * 10000 / param.contentLength));
                        }

                        if (isCancelled()) break;
                    }
                    os.flush();
                    os.close();

                    return new DownloadFileResult(downloadFile, param.mimetype);
                } else {
                    return null;
                }

            } catch (Exception e) {
                Log.e(TAG, e.getMessage(), e);
            }

            return null;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            progressDialog.setProgress(values[0]);
        }

        @Override
        protected void onPostExecute(DownloadFileResult result) {
            progressDialog.dismiss();

            if (result != null && result.file != null) {
                String fileString = result.file.toString();

                Uri content = null;
                try {
                    content = FileProvider.getUriForFile(context, AUTHORITY, result.file);
                } catch (IllegalArgumentException e) {
                    Log.e(TAG, "Unable to get content url from FileProvider", e);
                    return;
                }

                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(content, result.mimetype);
                intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                try {
                    context.startActivity(intent);
                } catch (ActivityNotFoundException e) {
                    String message = context.getResources().getString(R.string.file_handler_not_found);
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show();
                }
            }
        }

        @Override
        protected void onCancelled(DownloadFileResult downloadFileResult) {
            Toast.makeText(context, R.string.download_canceled, Toast.LENGTH_SHORT).show();
        }
    }

    public String getLastDownloadedUrl() {
        return lastDownloadedUrl;
    }
}
