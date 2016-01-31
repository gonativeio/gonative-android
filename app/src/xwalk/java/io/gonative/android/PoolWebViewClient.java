package io.gonative.android;

import android.webkit.WebResourceResponse;

import org.xwalk.core.XWalkResourceClient;
import org.xwalk.core.XWalkUIClient;
import org.xwalk.core.XWalkView;

/**
 * Created by weiyin on 9/9/15.
 */
public class PoolWebViewClient {
    private WebViewPool.WebViewPoolCallback webViewPoolCallback;
    private PoolWebViewUIClient uiClient;
    private PoolWebViewResourceClient resourceClient;


    private class PoolWebViewUIClient extends XWalkUIClient {
        public PoolWebViewUIClient(XWalkView view) {
            super(view);
        }

        @Override
        public void onPageLoadStopped(XWalkView view, String url, LoadStatus status) {
            super.onPageLoadStopped(view, url, status);

            if (status == LoadStatus.FINISHED) {
                webViewPoolCallback.onPageFinished((GoNativeWebviewInterface)view, url);
            }
        }
    }

    private class PoolWebViewResourceClient extends XWalkResourceClient {
        public PoolWebViewResourceClient(XWalkView view) {
            super(view);
        }

        @Override
        public WebResourceResponse shouldInterceptLoadRequest(XWalkView view, String url) {
            return webViewPoolCallback.interceptHtml((GoNativeWebviewInterface)view, url);
        }
    }

    public PoolWebViewClient(WebViewPool.WebViewPoolCallback webViewPoolCallback, LeanWebView view) {
        this.webViewPoolCallback = webViewPoolCallback;

        uiClient = new PoolWebViewUIClient(view);
        view.setUIClient(uiClient);

        resourceClient = new PoolWebViewResourceClient(view);
        view.setResourceClient(resourceClient);
    }
}
