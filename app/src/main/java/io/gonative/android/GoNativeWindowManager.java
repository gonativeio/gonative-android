package io.gonative.android;

import android.text.TextUtils;

import java.util.LinkedHashMap;
import java.util.Map;

public class GoNativeWindowManager {
    private final LinkedHashMap<String, ActivityWindow> windows;
    private ExcessWindowsClosedListener excessWindowsClosedListener;

    public GoNativeWindowManager() {
        windows = new LinkedHashMap<>();
    }

    public void addNewWindow(String activityId, boolean isRoot) {
        this.windows.put(activityId, new ActivityWindow(activityId, isRoot));
    }

    public void removeWindow(String activityId) {
        this.windows.remove(activityId);

        if (excessWindowsClosedListener != null && windows.size() <= 1) {
            excessWindowsClosedListener.onAllExcessWindowClosed();
        }
    }

    public void setOnExcessWindowClosedListener(ExcessWindowsClosedListener listener) {
        this.excessWindowsClosedListener = listener;
    }

    public ActivityWindow getActivityWindowInfo(String activityId) {
        return windows.get(activityId);
    }

    public void setUrlLevel(String activityId, int urlLevel) {
        ActivityWindow window = windows.get(activityId);
        if (window != null) {
            window.setUrlLevels(urlLevel, window.parentUrlLevel);
        }
    }

    public int getUrlLevel(String activityId) {
        ActivityWindow window = windows.get(activityId);
        if (window != null) {
            return window.urlLevel;
        }
        return -1;
    }

    public void setParentUrlLevel(String activityId, int parentLevel) {
        ActivityWindow window = windows.get(activityId);
        if (window != null) {
            window.setUrlLevels(window.urlLevel, parentLevel);
        }
    }

    public int getParentUrlLevel(String activityId) {
        ActivityWindow window = windows.get(activityId);
        if (window != null) {
            return window.parentUrlLevel;
        }
        return -1;
    }

    public void setUrlLevels(String activityId, int urlLevel, int parentLevel) {
        ActivityWindow window = windows.get(activityId);
        if (window != null) {
            window.setUrlLevels(urlLevel, parentLevel);
        }
    }

    public boolean isRoot(String activityId) {
        ActivityWindow window = windows.get(activityId);
        if (window != null) {
            return window.isRoot;
        }
        return false;
    }

    public void setAsNewRoot(String activityId) {
        for (Map.Entry<String, ActivityWindow> entry : windows.entrySet()) {
            ActivityWindow window = entry.getValue();
            if (TextUtils.equals(activityId, entry.getKey())) {
                window.isRoot = true;
            } else {
                window.isRoot = false;
            }
        }
    }

    public void setIgnoreInterceptMaxWindows(String activityId, boolean ignore) {
        ActivityWindow window = windows.get(activityId);
        if (window != null) {
            window.ignoreInterceptMaxWindows = ignore;
        }
    }

    public boolean isIgnoreInterceptMaxWindows(String activityId) {
        ActivityWindow window = windows.get(activityId);
        if (window != null) {
            return window.ignoreInterceptMaxWindows;
        }
        return false;
    }

    public int getWindowCount() {
        return windows.size();
    }

    // Returns ID of the next window after root as Excess window
    public String getExcessWindow() {
        for (Map.Entry<String, ActivityWindow> entry : windows.entrySet()) {
            ActivityWindow window = entry.getValue();
            if (window.isRoot) continue;
            return window.id;
        }
        return null;
    }

    public static class ActivityWindow {
        private final String id;
        private boolean isRoot;
        private int urlLevel;
        private int parentUrlLevel;
        private boolean ignoreInterceptMaxWindows;

        ActivityWindow(String id, boolean isRoot) {
            this.id = id;
            this.isRoot = isRoot;
            this.urlLevel = -1;
            this.parentUrlLevel = -1;
        }

        public void setUrlLevels(int urlLevel, int parentUrlLevel) {
            this.urlLevel = urlLevel;
            this.parentUrlLevel = parentUrlLevel;
        }

        @Override
        public String toString() {
            return "id=" + id + "\n" +
                    "isRoot=" + isRoot + "\n" +
                    "urlLevel=" + urlLevel + "\n" +
                    "parentUrlLevel=" + parentUrlLevel;
        }
    }


    interface ExcessWindowsClosedListener {
        void onAllExcessWindowClosed();
    }
}
