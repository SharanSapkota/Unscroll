package com.unscroll.app.domain.friction

/** Per-app nudges and friction. Every tracked app gets [DEFAULT] until the user changes it. */
data class FrictionSettings(
    val pauseEnabled: Boolean = true,
    /** 5 to 30 seconds. */
    val pauseSeconds: Int = DEFAULT_PAUSE_SECONDS,
    val nudgesEnabled: Boolean = true,
    /** Open counts that trigger a notification, ascending. */
    val nudgeThresholds: List<Int> = DEFAULT_THRESHOLDS,
    val breakRemindersEnabled: Boolean = true,
    val breakIntervalMinutes: Int = DEFAULT_BREAK_MINUTES,
    val limitWarningsEnabled: Boolean = true,
    /** Experimental gray tint after the daily limit, while an extension is running. */
    val tintEnabled: Boolean = false,
) {
    fun normalized(): FrictionSettings = copy(
        pauseSeconds = pauseSeconds.coerceIn(MIN_PAUSE_SECONDS, MAX_PAUSE_SECONDS),
        nudgeThresholds = nudgeThresholds.filter { it > 0 }.distinct().sorted(),
        breakIntervalMinutes = breakIntervalMinutes.coerceIn(1, 24 * 60),
    )

    companion object {
        const val DEFAULT_PAUSE_SECONDS = 10
        const val MIN_PAUSE_SECONDS = 5
        const val MAX_PAUSE_SECONDS = 30
        const val DEFAULT_BREAK_MINUTES = 15
        val DEFAULT_THRESHOLDS = listOf(5, 10, 20)
        val THRESHOLD_PRESETS = listOf(3, 5, 10, 15, 20, 30, 50)
        val BREAK_PRESETS = listOf(5, 10, 15, 20, 30, 45, 60)
        val DEFAULT = FrictionSettings()

        /** Thresholds are stored as "5,10,20". */
        fun encodeThresholds(thresholds: List<Int>): String = thresholds.joinToString(",")

        fun decodeThresholds(text: String): List<Int> =
            text.split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it > 0 }.distinct().sorted()
    }
}
