package com.unscroll.app.domain.overlay

enum class PillSize { SMALL, MEDIUM }

/** User settings for the floating timer pill. */
data class OverlaySettings(
    val enabled: Boolean = true,
    val showTodayTotal: Boolean = false,
    val thresholds: ColorThresholds = ColorThresholds(),
    val size: PillSize = PillSize.MEDIUM,
    /** 0.4 (faint) to 1.0 (solid). */
    val opacity: Float = DEFAULT_OPACITY,
    /** Show the session's swipe count next to the timer while scroll counting is on (M7). */
    val showSwipes: Boolean = true,
    /** The pill also turns yellow/red by swipes in the session, whichever is further along. */
    val swipeThresholds: SwipeColorThresholds = SwipeColorThresholds(),
    /** Apps the user hid the pill for (App detail › Timer pill). */
    val pillHiddenFor: Set<String> = emptySet(),
    /** Apps whose pill shows no swipe count, even with [showSwipes] on. */
    val swipesHiddenFor: Set<String> = emptySet(),
) {
    /** The global switch and the app's own switch must both be on. */
    fun showsPillFor(packageName: String): Boolean = enabled && packageName !in pillHiddenFor

    fun showsSwipesFor(packageName: String): Boolean = showSwipes && packageName !in swipesHiddenFor

    companion object {
        const val DEFAULT_OPACITY = 0.9f
        const val MIN_OPACITY = 0.4f
        const val MAX_OPACITY = 1f

        fun clampOpacity(opacity: Float): Float = opacity.coerceIn(MIN_OPACITY, MAX_OPACITY)
    }
}

/** The pill is calm until [warningAfterMinutes], a warning until [dangerAfterMinutes], then danger. */
data class ColorThresholds(
    val warningAfterMinutes: Int = DEFAULT_WARNING_MINUTES,
    val dangerAfterMinutes: Int = DEFAULT_DANGER_MINUTES,
) {
    /** Keeps both thresholds in range and the danger threshold after the warning one. */
    fun normalized(): ColorThresholds {
        val warning = warningAfterMinutes.coerceIn(MIN_MINUTES, MAX_MINUTES - 1)
        val danger = dangerAfterMinutes.coerceIn(warning + 1, MAX_MINUTES)
        return ColorThresholds(warning, danger)
    }

    companion object {
        const val DEFAULT_WARNING_MINUTES = 10
        const val DEFAULT_DANGER_MINUTES = 20
        const val MIN_MINUTES = 1
        const val MAX_MINUTES = 120
    }
}

/** Like [ColorThresholds], by swipes in the session: calm, then warning, then danger. */
data class SwipeColorThresholds(
    val warningAfterSwipes: Int = DEFAULT_WARNING_SWIPES,
    val dangerAfterSwipes: Int = DEFAULT_DANGER_SWIPES,
) {
    fun normalized(): SwipeColorThresholds {
        val warning = warningAfterSwipes.coerceIn(MIN_SWIPES, MAX_SWIPES - 1)
        val danger = dangerAfterSwipes.coerceIn(warning + 1, MAX_SWIPES)
        return SwipeColorThresholds(warning, danger)
    }

    companion object {
        const val DEFAULT_WARNING_SWIPES = 50
        const val DEFAULT_DANGER_SWIPES = 100
        const val MIN_SWIPES = 5
        const val MAX_SWIPES = 500
    }
}
