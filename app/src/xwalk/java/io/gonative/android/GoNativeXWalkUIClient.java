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
            // workaround for crosswalk's window.open not working if features is specified
            view.evaluateJavascript("if (!window.gonative_original_open) {\n" +
                    "window.gonative_original_open = window.open;\n" +
                    "window.open = function(url, name) {return gonative_original_open(url, name);}\n" +
                    "}", null);

            urlNavigation.onPageFinished((GoNativeWebviewInterface)view, url);
        } else if (status == LoadStatus.FAILED) {
            urlNavigation.onReceivedError((GoNativeWebviewInterface)view, 0);
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
        mainActivity.cancelFileUpload();

        mainActivity.setUploadMessage(uploadFile);
        if (acceptType == null || acceptType.trim().isEmpty()) acceptType = "*/*";
        urlNavigation.chooseFileUpload(new String[]{acceptType});
    }

    @Override
    public boolean onCreateWindowRequested(XWalkView view, InitiateBy initiator, ValueCallback<XWalkView> callback) {
        return urlNavigation.createNewWindow(callback);
    }

    @Override
    public void onJavascriptCloseWindow(XWalkView view) {
        if (!mainActivity.isRoot()) mainActivity.finish();
    }
}
