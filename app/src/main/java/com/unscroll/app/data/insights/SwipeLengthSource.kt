package com.unscroll.app.data.insights

import android.content.Context
import android.os.Build
import android.view.WindowManager
import com.unscroll.app.domain.insights.ThumbDistance
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** The length of one swipe on this phone: 55 % of the screen height, from DisplayMetrics. */
@Singleton
class SwipeLengthSource @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun swipeLengthMeters(): Double {
        val metrics = context.resources.displayMetrics
        // The full screen height in portrait (the longer side), where feeds are scrolled.
        val height = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.getSystemService(WindowManager::class.java)?.maximumWindowMetrics?.bounds
                ?.let { maxOf(it.width(), it.height()) }
        } else {
            null
        } ?: maxOf(metrics.widthPixels, metrics.heightPixels)
        // ydpi is the density along the screen's vertical axis in the current orientation.
        val dpi = if (metrics.heightPixels >= metrics.widthPixels) metrics.ydpi else metrics.xdpi
        return ThumbDistance.swipeLengthMeters(height, dpi)
    }
}
