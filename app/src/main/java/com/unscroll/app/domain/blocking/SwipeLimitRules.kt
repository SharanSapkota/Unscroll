package com.unscroll.app.domain.blocking

import com.unscroll.app.domain.insights.localDate
import com.unscroll.app.domain.insights.startOfDay
import java.time.ZoneId
import kotlin.math.ceil

/** Where a swipe limit stands for one app. */
data class SwipeLimitStatus(
    val limit: Int,
    /** Extra swipes from "I need access" in the current window. */
    val extensionSwipes: Int,
    /** Swipes in the current window (today, or the current swipe session). */
    val used: Int,
) {
    val allowance: Int get() = limit + extensionSwipes
    val remaining: Int get() = (allowance - used).coerceAtLeast(0)
    val reached: Boolean get() = used >= allowance

    /** Within the last 20 %: the pill shows how many swipes are left. */
    val showRemaining: Boolean
        get() = !reached && used >= ceil(allowance * SwipeLimitRules.WARN_FRACTION).toInt()
}

/** A logged session's times, for working out a per-session swipe window. */
data class SessionSpan(val start: Long, val end: Long?)

/** Pure rules for the hard swipe limit. */
object SwipeLimitRules {
    val PRESETS = listOf(25, 50, 100, 200, 300)
    val SESSION_GAP_PRESETS = listOf(10, 15, 30, 60)
    const val DEFAULT_SESSION_GAP_MINUTES = 30
    const val MIN_LIMIT = 1
    const val MAX_LIMIT = 10_000

    /** "I need access" on the cover grants this many more swipes. */
    const val EXTENSION_SWIPES = 20

    /** Show the remaining swipes once this share of the allowance is used. */
    const val WARN_FRACTION = 0.8

    fun status(limit: Int, used: Int, extensions: Int): SwipeLimitStatus =
        SwipeLimitStatus(limit = limit, extensionSwipes = extensions * EXTENSION_SWIPES, used = used)

    /**
     * Where the current counting window starts.
     * - Per day: local midnight today.
     * - Per session: the start of the latest run of the app's sessions with gaps shorter than
     *   [gapMinutes] between them. Staying away at least that long starts a new window.
     * [sessions] are the app's recent sessions, in any order. Without any, or when the latest one
     * ended at least [gapMinutes] ago, the window starts [now].
     */
    fun windowStart(
        scope: SwipeLimitScope,
        sessions: List<SessionSpan>,
        gapMinutes: Int,
        now: Long,
        zone: ZoneId,
    ): Long {
        if (scope == SwipeLimitScope.DAY) return startOfDay(localDate(now, zone), zone)
        val sorted = sessions.sortedByDescending { it.start }
        if (sorted.isEmpty()) return now
        val gapMillis = gapMinutes * 60_000L
        val latestEnd = sorted.first().end
        // Away long enough since the last session (e.g. checked as the app comes back, before its
        // new session is logged): a fresh window starts now.
        if (latestEnd != null && now - latestEnd >= gapMillis) return now
        var windowStart = sorted.first().start
        for (previous in sorted.drop(1)) {
            val previousEnd = previous.end ?: now
            if (windowStart - previousEnd >= gapMillis) break
            windowStart = previous.start
        }
        return windowStart
    }
}
