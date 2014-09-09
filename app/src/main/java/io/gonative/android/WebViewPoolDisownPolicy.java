package io.gonative.android;

/**
 * Created by weiyin on 9/3/14.
 */
public enum WebViewPoolDisownPolicy {
    Always, Reload, Never;

    public static WebViewPoolDisownPolicy defaultPolicy = WebViewPoolDisownPolicy.Reload;
}
