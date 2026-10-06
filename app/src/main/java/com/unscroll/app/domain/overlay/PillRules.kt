package com.unscroll.app.domain.overlay

import java.util.Locale

enum class PillLevel { CALM, WARNING, DANGER }

/** Pure rules for what the pill shows. */
object PillRules {

    private const val MINUTE = 60_000L

    /** The pill is only ever on screen while a tracked app is in front and the user allows it. */
    fun shouldShow(
        trackedAppInForeground: Boolean,
        settings: OverlaySettings,
        canDrawOverlays: Boolean,
    ): Boolean = trackedAppInForeground && settings.enabled && canDrawOverlays

    /** Green before the warning threshold, yellow before the danger threshold, red after. */
    fun levelFor(elapsedMillis: Long, thresholds: ColorThresholds): PillLevel {
        val safe = thresholds.normalized()
        return when {
            elapsedMillis >= safe.dangerAfterMinutes * MINUTE -> PillLevel.DANGER
            elapsedMillis >= safe.warningAfterMinutes * MINUTE -> PillLevel.WARNING
            else -> PillLevel.CALM
        }
    }

    /** "mm:ss" under an hour ("07:05", "59:59"), then "h:mm:ss" ("1:00:00"). */
    fun formatTime(elapsedMillis: Long): String {
        val totalSeconds = elapsedMillis.coerceAtLeast(0) / 1_000
        val hours = totalSeconds / 3_600
        val minutes = (totalSeconds % 3_600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
        }
    }
}
