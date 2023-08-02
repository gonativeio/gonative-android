package io.gonative.android;


import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import io.gonative.gonative_core.GNLog;

public class IOUtils {
    private static final String TAG = IOUtils.class.getName();

	public static void copy(InputStream in, OutputStream out) throws IOException{
		byte[] buf = new byte[1024];
	    int len;
	    while ((len = in.read(buf)) > 0) {
	        out.write(buf, 0, len);
	    }
	}
	
	public static void close(Closeable c) {
        if (c == null) return;
        try {
            c.close();
        } catch (IOException e){
            GNLog.getInstance().logError(TAG, e.toString(), e);
        }
    }
}
