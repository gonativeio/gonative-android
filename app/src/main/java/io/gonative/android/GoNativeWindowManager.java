package io.gonative.android;

import android.text.TextUtils;

import java.util.HashMap;
import java.util.Map;

public class GoNativeWindowManager {
    private final Map<String, ActivityWindow> windows;

    public GoNativeWindowManager() {
        windows = new HashMap<>();
    }

    public void addNewWindow(String activityId, boolean isRoot) {
        this.windows.put(activityId, new ActivityWindow(activityId, isRoot));
    }

    public void removeWindow(String activityId) {
        this.windows.remove(activityId);
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

    public int getWindowCount() {
        return windows.size();
    }

    public static class ActivityWindow {
        private final String id;
        private boolean isRoot;
        private int urlLevel;
        private int parentUrlLevel;

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
}
