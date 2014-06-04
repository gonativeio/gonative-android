package io.gonative.android;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.CookieStore;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

// this syncs cookies between webkit (webview) and java.net classes
public class WebkitCookieManagerProxy extends CookieManager {
	private android.webkit.CookieManager webkitCookieManager;

    public WebkitCookieManagerProxy()
    {
        this(null, null);
    }
    
    WebkitCookieManagerProxy(CookieStore store, CookiePolicy cookiePolicy)
    {
        super(null, cookiePolicy);

        this.webkitCookieManager = android.webkit.CookieManager.getInstance();
    }

    // java.net.CookieManager overrides
    @Override
    public void put(URI uri, Map<String, List<String>> responseHeaders) throws IOException 
    {
        // make sure our args are valid
        if ((uri == null) || (responseHeaders == null)) return;

        // save our url once
        String url = uri.toString();

        // go over the headers
        for (String headerKey : responseHeaders.keySet()) 
        {
            // ignore headers which aren't cookie related
            if ((headerKey == null) || !(headerKey.equalsIgnoreCase("Set-Cookie2") || headerKey.equalsIgnoreCase("Set-Cookie"))) continue;

            // process each of the headers
            for (String headerValue : responseHeaders.get(headerKey))
            {
                this.webkitCookieManager.setCookie(url, headerValue);
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
        String cookie = this.webkitCookieManager.getCookie(url);

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
