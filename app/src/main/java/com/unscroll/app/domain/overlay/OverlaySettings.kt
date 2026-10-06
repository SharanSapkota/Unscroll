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
) {
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
