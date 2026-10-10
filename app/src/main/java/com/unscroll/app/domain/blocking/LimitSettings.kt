package com.unscroll.app.domain.blocking

import java.time.DayOfWeek

/**
 * Block on [days] between [startMinute] and [endMinute] (minutes after local midnight). A window
 * whose end is before its start crosses midnight: 22:00 to 07:00 on Monday blocks Monday 22:00
 * to Tuesday 07:00. A window whose start equals its end blocks the whole day.
 */
data class BlockSchedule(
    val enabled: Boolean = false,
    val days: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
    val startMinute: Int = 22 * 60,
    val endMinute: Int = 7 * 60,
) {
    val crossesMidnight: Boolean get() = endMinute < startMinute

    /** Monday is bit 0, Sunday bit 6. */
    val daysBitmask: Int get() = days.fold(0) { mask, day -> mask or (1 shl (day.value - 1)) }

    companion object {
        const val MINUTES_PER_DAY = 24 * 60

        fun daysFromBitmask(mask: Int): Set<DayOfWeek> =
            DayOfWeek.entries.filter { mask and (1 shl (it.value - 1)) != 0 }.toSet()
    }
}

/** Whether a swipe limit counts swipes per day or per (swipe) session. */
enum class SwipeLimitScope { DAY, SESSION }

/** What the user has configured for one app. */
data class LimitSettings(
    /** Null means no daily limit. */
    val dailyLimitMinutes: Int? = null,
    /**
     * "Block entire app": blocked until this epoch-millis time ([TimedBlock.FOREVER]: until the
     * user turns it off), or null when off. A past value means it ran out.
     */
    val entireAppBlockedUntil: Long? = null,
    /** "Block reels only" (Reels-capable apps): same as [entireAppBlockedUntil], for the section. */
    val reelsBlockedUntil: Long? = null,
    /** The duration chip last chosen for each toggle; the next "on" uses it. */
    val lastEntireDuration: BlockDuration = BlockDuration.DEFAULT,
    val lastReelsDuration: BlockDuration = BlockDuration.DEFAULT,
    val schedule: BlockSchedule = BlockSchedule(),
    /** Hard swipe limit (needs the opt-in scroll counting), or null for none. */
    val swipeLimit: Int? = null,
    val swipeLimitScope: SwipeLimitScope = SwipeLimitScope.DAY,
    /** Per-session scope: the count starts again after the user stayed away this long. */
    val swipeSessionGapMinutes: Int = SwipeLimitRules.DEFAULT_SESSION_GAP_MINUTES,
    /** Offer "I need access" (one tap, +20 swipes) on the swipe-limit cover. */
    val swipeAccessAllowed: Boolean = false,
) {
    /**
     * Time-based rules, enforced by BlockEnforcer. The swipe limit and the reels block have their
     * own enforcers. A stored entire-app block counts even if it ran out; the evaluator checks.
     */
    val hasAnyRule: Boolean
        get() = dailyLimitMinutes != null || entireAppBlockedUntil != null || schedule.enabled

    fun entireAppBlocked(now: Long): Boolean = TimedBlock.isActive(entireAppBlockedUntil, now)

    /** The reels toggle itself; the entire-app block includes reels on top of this. */
    fun reelsBlocked(now: Long): Boolean = TimedBlock.isActive(reelsBlockedUntil, now)

    companion object {
        val NONE = LimitSettings()
    }
}

data class AppLimit(
    val packageName: String,
    val settings: LimitSettings = LimitSettings.NONE,
)

/** "I need access" on the block screen: one tap, logged in `block_overrides`. */
object AccessExtension {
    /** How long a time extension from the block screen lasts. */
    const val MILLIS = 5 * 60_000L
}
