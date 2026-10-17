package com.unscroll.app.domain.overlay

import com.unscroll.app.domain.session.SessionSwipes
import java.util.Locale

enum class PillLevel { CALM, WARNING, DANGER }

/** Pure rules for what the pill shows. */
object PillRules {

    private const val MINUTE = 60_000L
    private const val PERCENT = 100L

    /** The pill is only ever on screen while a tracked app is in front and the user allows it. */
    fun shouldShow(
        foregroundPackage: String?,
        settings: OverlaySettings,
        canDrawOverlays: Boolean,
    ): Boolean = foregroundPackage != null && settings.showsPillFor(foregroundPackage) && canDrawOverlays

    /**
     * Swipe info for the pill, or null when the pill shows only the fox and the time. Swipes only
     * appear while scroll counting really runs ([counting]: consent given and the accessibility
     * service connected), so the pill never shows a "0" that only means "not counting".
     */
    fun swipes(counting: Boolean, session: SessionSwipes?, showCount: Boolean, remaining: Int?): PillSwipes? {
        if (!counting || session == null) return null
        return PillSwipes(sessionCount = session.count, showCount = showCount, remaining = remaining)
    }

    /** The number next to the swipe icon (today's swipes in the app), or null to hide icon and number. */
    fun shownSwipes(swipes: PillSwipes?, todaySwipes: Int): Int? = swipes?.takeIf { it.showCount }?.let { todaySwipes }

    /** With a daily limit: yellow from this share of it, red at 100 %. */
    const val LIMIT_WARNING_PERCENT = 60

    /**
     * The color for today's total in an app. With a daily limit: green under 60 % of it, yellow
     * from 60 %, red at or over 100 %. Without one: the user's absolute [thresholds].
     */
    fun levelForToday(todayMillis: Long, dailyLimitMillis: Long?, thresholds: ColorThresholds): PillLevel {
        if (dailyLimitMillis == null || dailyLimitMillis <= 0) return levelFor(todayMillis, thresholds)
        return when {
            todayMillis >= dailyLimitMillis -> PillLevel.DANGER
            todayMillis * PERCENT >= dailyLimitMillis * LIMIT_WARNING_PERCENT -> PillLevel.WARNING
            else -> PillLevel.CALM
        }
    }

    /** Green before the warning threshold, yellow before the danger threshold, red after. */
    fun levelFor(elapsedMillis: Long, thresholds: ColorThresholds): PillLevel {
        val safe = thresholds.normalized()
        return when {
            elapsedMillis >= safe.dangerAfterMinutes * MINUTE -> PillLevel.DANGER
            elapsedMillis >= safe.warningAfterMinutes * MINUTE -> PillLevel.WARNING
            else -> PillLevel.CALM
        }
    }

    /** Green before the warning threshold, yellow before the danger threshold, red after, by swipes. */
    fun levelForSwipes(swipes: Int, thresholds: SwipeColorThresholds): PillLevel {
        val safe = thresholds.normalized()
        return when {
            swipes >= safe.dangerAfterSwipes -> PillLevel.DANGER
            swipes >= safe.warningAfterSwipes -> PillLevel.WARNING
            else -> PillLevel.CALM
        }
    }

    /** The more urgent of the time level and the swipe level (no swipes counted: time only). */
    fun combinedLevel(timeLevel: PillLevel, swipeLevel: PillLevel?): PillLevel =
        if (swipeLevel != null && swipeLevel > timeLevel) swipeLevel else timeLevel

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
