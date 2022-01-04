package io.gonative.gonative_core;

import android.content.Context;
import android.content.ContextWrapper;

public class GoNativeContext extends ContextWrapper {
    public GoNativeContext(Context base) {
        super(base);
    }
}
