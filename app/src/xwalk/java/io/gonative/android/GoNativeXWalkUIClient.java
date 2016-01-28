package io.gonative.android;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.webkit.ValueCallback;
import android.widget.Toast;

import org.xwalk.core.XWalkUIClient;
import org.xwalk.core.XWalkView;

/**
 * Created by weiyin on 9/9/15.
 */
public class GoNativeXWalkUIClient extends XWalkUIClient {
    private UrlNavigation urlNavigation;
    private MainActivity mainActivity;

    public GoNativeXWalkUIClient(XWalkView view, UrlNavigation urlNavigation, MainActivity mainActivity) {
        super(view);
        this.urlNavigation = urlNavigation;
        this.mainActivity = mainActivity;
    }

    @Override
    public void onPageLoadStopped(XWalkView view, String url, LoadStatus status) {
        super.onPageLoadStopped(view, url, status);

        if (status == LoadStatus.FINISHED) {
            urlNavigation.onPageFinished((GoNativeWebviewInterface)view, url);
        } else if (status == LoadStatus.FAILED) {
            urlNavigation.onReceivedError((GoNativeWebviewInterface)view, 0, null, url);
        }
    }

    @Override
    public void onPageLoadStarted(XWalkView view, String url) {
        super.onPageLoadStarted(view, url);

        urlNavigation.onPageStarted(url);
    }

    @Override
    public void openFileChooser(XWalkView view, ValueCallback<Uri> uploadFile, String acceptType, String capture) {
        // make sure there is no existing message
        if (mainActivity.getUploadMessage() != null) {
            mainActivity.getUploadMessage().onReceiveValue(null);
            mainActivity.setUploadMessage(null);
        }

        mainActivity.setUploadMessage(uploadFile);
        if (acceptType == null || acceptType.trim().isEmpty()) acceptType = "*/*";
        Intent intent = urlNavigation.createFileChooserIntent(new String[]{acceptType});
        try {
            mainActivity.startActivityForResult(intent, MainActivity.REQUEST_SELECT_FILE_OLD);
        } catch (ActivityNotFoundException e) {
            mainActivity.setUploadMessageLP(null);
            Toast.makeText(mainActivity, R.string.cannot_open_file_chooser, Toast.LENGTH_LONG).show();
        }
    }


}
