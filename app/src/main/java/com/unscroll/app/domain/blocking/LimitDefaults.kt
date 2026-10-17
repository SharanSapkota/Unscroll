package com.unscroll.app.domain.blocking

import com.unscroll.app.domain.overlay.CoverReason

/**
 * App detail › Limits: two separate controls, "Time limit" and "Swipe limit", each a switch with
 * value chips. A null value in [LimitSettings] means the switch is off.
 */
object LimitDefaults {
    /** Time limit chips, in minutes (plus Custom). */
    val TIME_PRESETS = listOf(15, 30, 45, 60, 120)

    /** Swipe limit chips (plus Custom). */
    val SWIPE_PRESETS = listOf(50, 100, 200, 300)

    /** A fresh install's apps and every newly added app start with a 30-minute time limit. */
    const val TIME_LIMIT_MINUTES = 30

    /** The swipe limit is off by default; switched on it starts at 100 per day. */
    const val SWIPE_LIMIT = 100

    /**
     * What a newly tracked app starts with: time limit on at 30 minutes, swipe limit off. Only
     * written when the app has no stored limits yet, so nobody's saved values change.
     */
    val NEW_APP: LimitSettings = LimitSettings(dailyLimitMinutes = TIME_LIMIT_MINUTES)

    /** The "Time limit" switch: on keeps the last value or starts at 30 minutes; off clears it. */
    fun withTimeLimit(settings: LimitSettings, on: Boolean): LimitSettings =
        settings.copy(dailyLimitMinutes = if (on) settings.dailyLimitMinutes ?: TIME_LIMIT_MINUTES else null)

    /** The "Swipe limit" switch: on starts at 100 per day; off clears it (the scope chips stay). */
    fun withSwipeLimit(settings: LimitSettings, on: Boolean): LimitSettings = when {
        !on -> settings.copy(swipeLimit = null)
        settings.swipeLimit != null -> settings
        else -> settings.copy(swipeLimit = SWIPE_LIMIT, swipeLimitScope = SwipeLimitScope.DAY)
    }
}

/**
 * Which limit the cover names when more than one holds. The first one reached wins: once the cover
 * says "Time limit reached" it keeps saying so while that limit holds, even if the swipe limit is
 * reached too. With nothing up yet (both found at once, e.g. on opening the app), the time limit
 * comes first, then the swipe limit, then the schedule.
 */
object LimitCoverRules {
    fun reason(current: CoverReason?, timeReached: Boolean, swipeReached: Boolean, inSchedule: Boolean): CoverReason? {
        val holding = buildSet {
            if (timeReached) add(CoverReason.TIME_LIMIT)
            if (swipeReached) add(CoverReason.SWIPE_LIMIT)
            if (inSchedule) add(CoverReason.SCHEDULE)
        }
        if (current != null && current in holding) return current
        return ORDER.firstOrNull { it in holding }
    }

    private val ORDER = listOf(CoverReason.TIME_LIMIT, CoverReason.SWIPE_LIMIT, CoverReason.SCHEDULE)
}
