package io.gonative.android;

import android.text.format.DateUtils;

import org.xwalk.core.XWalkCookieManager;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.CookieStore;
import java.net.HttpCookie;
import java.net.URI;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;

import io.gonative.android.library.AppConfig;

// this syncs cookies between webkit (webview) and java.net classes
public class WebkitCookieManagerProxy extends CookieManager {
    private static final String TAG = WebkitCookieManagerProxy.class.getName();
    private XWalkCookieManager crosswalkCookieManager;

    public WebkitCookieManagerProxy()
    {
        this(null, null);
    }
    
    WebkitCookieManagerProxy(CookieStore store, CookiePolicy cookiePolicy)
    {
        super(null, cookiePolicy);

        this.crosswalkCookieManager = new XWalkCookieManager();

        // calling getCookie() once on the main thread seems to "initialize" it and prevent a crash
        // that occurs if the first time called is on an Chrome_IOthread.
        this.crosswalkCookieManager.getCookie("");
    }

    // java.net.CookieManager overrides
    @Override
    public void put(URI uri, Map<String, List<String>> responseHeaders) throws IOException 
    {
        // make sure our args are valid
        if ((uri == null) || (responseHeaders == null)) return;

        // save our url once
        String url = uri.toString();

        String expiryString = null;
        int sessionExpiry = AppConfig.getInstance(null).forceSessionCookieExpiry;

        // go over the headers
        for (String headerKey : responseHeaders.keySet()) 
        {
            // ignore headers which aren't cookie related
            if ((headerKey == null) || !(headerKey.equalsIgnoreCase("Set-Cookie2") || headerKey.equalsIgnoreCase("Set-Cookie"))) continue;

            // process each of the headers
            for (String headerValue : responseHeaders.get(headerKey))
            {
                boolean passOriginalHeader = true;
                if (sessionExpiry > 0) {
                    List<HttpCookie> cookies = HttpCookie.parse(headerValue);
                    for (HttpCookie cookie : cookies) {
                        if (cookie.getMaxAge() < 0 || cookie.getDiscard()) {
                            // this is a session cookie. Modify it and pass it to the webview.
                            cookie.setMaxAge(sessionExpiry);
                            cookie.setDiscard(false);
                            if (expiryString == null) {
                                Calendar calendar = Calendar.getInstance();
                                calendar.add(Calendar.SECOND, sessionExpiry);
                                Date expiryDate = calendar.getTime();
                                expiryString = "; expires=" + LeanUtils.formatDateForCookie(expiryDate) +
                                        "; Max-Age=" + Integer.toString(sessionExpiry);
                            }

                            StringBuilder newHeader = new StringBuilder();
                            newHeader.append(cookie.toString());
                            newHeader.append(expiryString);
                            if (cookie.getPath() != null) {
                                newHeader.append("; path=");
                                newHeader.append(cookie.getPath());
                            }
                            if (cookie.getDomain() != null) {
                                newHeader.append("; domain=");
                                newHeader.append(cookie.getDomain());
                            }
                            if (cookie.getSecure()) {
                                newHeader.append("; secure");
                            }

                            this.crosswalkCookieManager.setCookie(url, newHeader.toString());
                            passOriginalHeader = false;
                        }
                    }
                }

                if (passOriginalHeader) this.crosswalkCookieManager.setCookie(url, headerValue);
            }
        }
    }

    @Override
    public Map<String, List<String>> get(URI uri, Map<String, List<String>> requestHeaders) throws IOException 
    {
        // make sure our args are valid
        if ((uri == null) || (requestHeaders == null)) throw new IllegalArgumentException("Argument is null");

        // save our url once
        String url = uri.toString();

        // prepare our response
        Map<String, List<String>> res = new java.util.HashMap<String, List<String>>();

        // get the cookie
        String cookie = this.crosswalkCookieManager.getCookie(url);

        // return it
        if (cookie != null) res.put("Cookie", Arrays.asList(cookie));
        return res;
    }

    @Override
    public CookieStore getCookieStore() 
    {
        // we don't want anyone to work with this cookie store directly
        throw new UnsupportedOperationException();
    }
}
