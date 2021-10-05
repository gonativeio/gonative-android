package io.gonative.android;

import android.content.Context;
import android.util.AttributeSet;

import io.gonative.android.widget.GoNativeSwipeRefreshLayout;

/**
 * Created by weiyin on 9/13/15.
 * Copyright 2014 GoNative.io LLC
 */
public class MySwipeRefreshLayout extends GoNativeSwipeRefreshLayout {
    private CanChildScrollUpCallback canChildScrollUpCallback;

    public interface CanChildScrollUpCallback {
        boolean canSwipeRefreshChildScrollUp();
    }

    public MySwipeRefreshLayout(Context context) {
        super(context);
    }

    public MySwipeRefreshLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setCanChildScrollUpCallback(CanChildScrollUpCallback canChildScrollUpCallback) {
        this.canChildScrollUpCallback = canChildScrollUpCallback;
    }

    @Override
    public boolean canChildScrollUp() {
        if (canChildScrollUpCallback != null) {
            return canChildScrollUpCallback.canSwipeRefreshChildScrollUp();
        } else {
            return super.canChildScrollUp();
        }
    }
}
