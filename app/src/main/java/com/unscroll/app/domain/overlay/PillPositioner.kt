package com.unscroll.app.domain.overlay

/** Top-left corner of the pill window in screen pixels. */
data class PillPosition(val x: Int, val y: Int)

enum class ScreenOrientation { PORTRAIT, LANDSCAPE }

/**
 * The screen area the pill may occupy: the full screen minus system insets (status bar, display
 * cutout, navigation bar).
 */
data class ScreenBounds(
    val width: Int,
    val height: Int,
    val insetLeft: Int = 0,
    val insetTop: Int = 0,
    val insetRight: Int = 0,
    val insetBottom: Int = 0,
)

/** Pure placement rules, so the WindowManager code stays thin. */
object PillPositioner {

    /** Top center, just below the status bar and cutout. */
    fun defaultPosition(
        screen: ScreenBounds,
        pillWidth: Int,
        pillHeight: Int,
        marginTop: Int,
    ): PillPosition = clamp(
        PillPosition(x = (screen.width - pillWidth) / 2, y = screen.insetTop + marginTop),
        pillWidth,
        pillHeight,
        screen,
    )

    /** Keeps the whole pill on screen and out of the inset areas. */
    fun clamp(position: PillPosition, pillWidth: Int, pillHeight: Int, screen: ScreenBounds): PillPosition {
        val minX = screen.insetLeft
        val maxX = maxOf(minX, screen.width - screen.insetRight - pillWidth)
        val minY = screen.insetTop
        val maxY = maxOf(minY, screen.height - screen.insetBottom - pillHeight)
        return PillPosition(position.x.coerceIn(minX, maxX), position.y.coerceIn(minY, maxY))
    }
}
