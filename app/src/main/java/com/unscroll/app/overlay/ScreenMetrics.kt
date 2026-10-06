package com.unscroll.app.overlay

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.view.WindowInsets
import android.view.WindowManager
import com.unscroll.app.domain.overlay.ScreenBounds
import com.unscroll.app.domain.overlay.ScreenOrientation

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

internal fun Context.screenOrientation(): ScreenOrientation =
    if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
        ScreenOrientation.LANDSCAPE
    } else {
        ScreenOrientation.PORTRAIT
    }

private const val LEGACY_STATUS_BAR_DP = 32
