package io.gonative.android.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.drawerlayout.widget.DrawerLayout;

public class GoNativeDrawerLayout extends DrawerLayout {
    
    private boolean disableTouch = false;
    
    public GoNativeDrawerLayout(@NonNull Context context) {
        this(context, null);
    }
    
    public GoNativeDrawerLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }
    
    public GoNativeDrawerLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }
    
    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (disableTouch) {
            Log.d("SWIPE", "GNDrawerLayout disabled touch");
            return false;
        }
        return super.onInterceptTouchEvent(ev);
    }
    
    
    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (disableTouch) {
            Log.d("SWIPE", "GNDrawerLayout disabled touch");
            return false;
        }
        return super.onTouchEvent(ev);
    }
    
    public void setDisableTouch(boolean disableTouch){
        this.disableTouch = disableTouch;
    }
}
