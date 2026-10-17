package com.unscroll.app.overlay

import android.content.Context
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
import android.view.WindowInsets
import android.view.WindowManager
import com.unscroll.app.domain.overlay.ScreenBounds
import com.unscroll.app.domain.overlay.ScreenOrientation
import com.unscroll.app.util.AppLog

/** Reads the screen size and the insets the pill must avoid (status bar, cutout, nav bar). */
internal fun WindowManager.screenBounds(context: Context): ScreenBounds {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val metrics = currentWindowMetrics
        val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
            WindowInsets.Type.statusBars() or
                WindowInsets.Type.navigationBars() or
                WindowInsets.Type.displayCutout(),
        )
        return ScreenBounds(
            width = metrics.bounds.width(),
            height = metrics.bounds.height(),
            insetLeft = insets.left,
            insetTop = insets.top,
            insetRight = insets.right,
            insetBottom = insets.bottom,
        )
    }
    // Before Android 11 there is no public API for another window's insets from a service.
    // Display metrics already exclude the navigation bar; reserve a typical status bar height.
    val displayMetrics = context.resources.displayMetrics
    return ScreenBounds(
        width = displayMetrics.widthPixels,
        height = displayMetrics.heightPixels,
        insetTop = (LEGACY_STATUS_BAR_DP * displayMetrics.density).toInt(),
    )
}

/**
 * A context for TYPE_APPLICATION_OVERLAY windows. Android 11+ wants a window context for visual
 * work (WindowManager, window metrics) outside an Activity; it also follows the display's
 * configuration (rotation, fold/unfold). Falls back to [this] if one can't be created.
 */
internal fun Context.overlayWindowContext(): Context {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return this
    return try {
        val display = getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY) ?: return this
        createDisplayContext(display).createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
    } catch (e: RuntimeException) {
        AppLog.w("ScreenMetrics", "No window context; using the app context", e)
        this
    }
}

internal fun Context.screenOrientation(): ScreenOrientation =
    if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
        ScreenOrientation.LANDSCAPE
    } else {
        ScreenOrientation.PORTRAIT
    }

private const val LEGACY_STATUS_BAR_DP = 32
