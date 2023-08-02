package io.gonative.android

import android.graphics.Rect
import android.text.TextUtils
import android.view.ViewGroup
import io.gonative.gonative_core.LeanUtils
import org.json.JSONObject


class KeyboardManager(val activity: MainActivity, private val rootLayout: ViewGroup) {

    var callback: String? = ""
    private var keyboardWidth = 0
    private var keyboardHeight = 0
    private var screenWidth = 0
    private var screenHeight = 0
    private var isKeyboardVisible = false
    private var screenHeightOffset = 0

    init {
        rootLayout.viewTreeObserver
            .addOnGlobalLayoutListener {
                val r = Rect()
                rootLayout.getWindowVisibleDisplayFrame(r)

                if (screenHeightOffset == 0) {
                    screenHeightOffset = rootLayout.rootView.height - r.bottom
                }

                screenWidth = rootLayout.rootView.width
                screenHeight = r.bottom + screenHeightOffset

                keyboardHeight = rootLayout.rootView.height - screenHeight

                if (keyboardHeight == screenHeightOffset) {
                    keyboardHeight = 0
                }

                val visible =  keyboardHeight != 0

                if (visible) {
                    keyboardWidth = screenWidth
                    if (!isKeyboardVisible) {
                        isKeyboardVisible = true
                        notifyCallback();
                    }
                } else {
                    keyboardWidth = 0
                    if (isKeyboardVisible) {
                        isKeyboardVisible = false
                        notifyCallback();
                    }
                }
            }
    }

    private fun notifyCallback() {
        if (TextUtils.isEmpty(callback)) return
        activity.runJavascript(LeanUtils.createJsForCallback(callback, getKeyboardData()))
    }

    fun getKeyboardData() : JSONObject {
        val keyboardWindowSize = JSONObject()
        keyboardWindowSize.put("visible", isKeyboardVisible)
        keyboardWindowSize.put("width", keyboardWidth)
        keyboardWindowSize.put("height", keyboardHeight)

        val visibleWindowSize = JSONObject()
        visibleWindowSize.put("width", screenWidth)
        visibleWindowSize.put("height", screenHeight)

        val data = JSONObject()
        data.put("keyboardWindowSize", keyboardWindowSize)
        data.put("visibleWindowSize", visibleWindowSize)

        return data
    }
}