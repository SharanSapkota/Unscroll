package com.unscroll.app.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.WindowManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Experimental "grayscale-style" warning: a semi-transparent gray layer over the tracked app once
 * the daily limit is exceeded and the user chose to continue. Android doesn't let apps switch the
 * system to grayscale without a special permission, and we don't use hidden APIs, so this only
 * dims and desaturates the look.
 *
 * It never takes touches (FLAG_NOT_TOUCHABLE), and its alpha stays at or under 0.8, the limit
 * Android 12+ allows for an untrusted overlay that touches pass through. Main thread only.
 */
@Singleton
class TintOverlay @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val windowManager: WindowManager? = context.getSystemService(WindowManager::class.java)
    private var view: View? = null

    fun show() {
        if (view != null || !Settings.canDrawOverlays(context)) return
        val manager = windowManager ?: return
        val tint = View(context).apply { setBackgroundColor(Color.rgb(96, 96, 96)) }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply { alpha = TINT_ALPHA }
        try {
            manager.addView(tint, params)
            view = tint
        } catch (e: WindowManager.BadTokenException) {
            Log.w(TAG, "Tint not shown", e)
        } catch (e: SecurityException) {
            Log.w(TAG, "Tint not shown", e)
        }
    }

    fun hide() {
        val current = view ?: return
        if (current.isAttachedToWindow) windowManager?.removeViewImmediate(current)
        view = null
    }

    private companion object {
        const val TAG = "TintOverlay"
        const val TINT_ALPHA = 0.45f
    }
}
