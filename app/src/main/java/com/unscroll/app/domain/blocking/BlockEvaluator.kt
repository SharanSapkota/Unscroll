package com.unscroll.app.domain.blocking

import com.unscroll.app.domain.apps.ExcludedApps
import com.unscroll.app.domain.section.SectionVerdict
import com.unscroll.app.domain.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject

enum class BlockReason {
    /** "Block entire app", until a time or until the user turns it off. */
    BLOCKED_ENTIRE_APP,
    DAILY_LIMIT_REACHED,
    INSIDE_SCHEDULE,

    /** Never returned by BlockEvaluator: the swipe limit's fallback when the cover can't be drawn. */
    SWIPE_LIMIT_REACHED,
}

sealed interface BlockDecision {
    /** [remainingMillis] is the time left under the daily limit, or null without a limit. */
    data class Allowed(val remainingMillis: Long?) : BlockDecision

    /** [until] is when the block lifts by itself, or null for "Block entire app" until turned off. */
    data class Blocked(val reason: BlockReason, val until: Long?) : BlockDecision
}

/**
 * Decides whether an app may be used right now. Pure: the caller supplies today's usage and any
 * active extension, and the time comes from the injected [Clock].
 *
 * Order matters: "Block entire app" (while its time runs) wins, then the schedule, then the daily
 * limit. An extension granted from the block screen only lifts the daily limit. Timed blocks are
 * compared with the clock on every call, so they end on time even after process death.
 *
 * Safety first: an excluded app (Unscroll, a home screen, Settings, the phone, emergency apps, the
 * Play Store; see AppExclusions) is never blocked, whatever its stored settings say.
 */
class BlockEvaluator @Inject constructor(
    private val clock: Clock,
    private val excludedApps: ExcludedApps,
) {

    fun evaluate(
        settings: LimitSettings,
        usedTodayMillis: Long,
        extensionUntil: Long? = null,
        now: Long = clock.now(),
        zone: ZoneId = ZoneId.systemDefault(),
        packageName: String? = null,
    ): BlockDecision {
        if (packageName != null && excludedApps.isExcluded(packageName)) return BlockDecision.Allowed(remainingMillis = null)
        if (settings.entireAppBlocked(now)) {
            val until = settings.entireAppBlockedUntil?.takeUnless { it == TimedBlock.FOREVER }
            return BlockDecision.Blocked(BlockReason.BLOCKED_ENTIRE_APP, until = until)
        }

        val time = Instant.ofEpochMilli(now).atZone(zone)
        scheduleEnd(settings.schedule, time)?.let { end ->
            return BlockDecision.Blocked(BlockReason.INSIDE_SCHEDULE, until = end)
        }

        val limit = settings.dailyLimitMinutes ?: return BlockDecision.Allowed(remainingMillis = null)
        val remaining = limit * MINUTE - usedTodayMillis
        return when {
            remaining > 0 -> BlockDecision.Allowed(remainingMillis = remaining)
            // Limit used up, but an extension from the block screen is still running.
            extensionUntil != null && extensionUntil > now ->
                BlockDecision.Allowed(remainingMillis = extensionUntil - now)
            else -> BlockDecision.Blocked(
                BlockReason.DAILY_LIMIT_REACHED,
                until = time.toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
            )
        }
    }

    /**
     * Whether section blocking should treat [verdict] as blocked for this app: the reels toggle
     * (or "Block entire app", which includes reels) is running and the detector positively
     * identified the short-video section. Fails open: UNKNOWN and allowed sections never block,
     * and excluded apps never do.
     */
    fun blocksSection(
        settings: LimitSettings,
        verdict: SectionVerdict,
        now: Long = clock.now(),
        packageName: String? = null,
    ): Boolean {
        if (packageName != null && excludedApps.isExcluded(packageName)) return false
        if (verdict != SectionVerdict.IN_BLOCKED_SECTION) return false
        return settings.reelsBlocked(now) || settings.entireAppBlocked(now)
    }

    /** When the schedule window containing [time] ends, or null if [time] is outside it. */
    private fun scheduleEnd(schedule: BlockSchedule, time: ZonedDateTime): Long? {
        if (!schedule.enabled || schedule.days.isEmpty()) return null
        val minute = time.hour * 60 + time.minute
        val today = time.toLocalDate()
        val start = schedule.startMinute
        val end = schedule.endMinute

        fun at(date: LocalDate, minuteOfDay: Int): Long =
            date.atStartOfDay(time.zone).plusMinutes(minuteOfDay.toLong()).toInstant().toEpochMilli()

        return when {
            // Whole-day window.
            start == end ->
                if (time.dayOfWeek in schedule.days) at(today.plusDays(1), 0) else null
            // Same-day window, e.g. 09:00 to 17:00.
            start < end ->
                if (time.dayOfWeek in schedule.days && minute in start until end) at(today, end) else null
            // Crosses midnight, e.g. 22:00 to 07:00: the evening part belongs to today's entry,
            // the early-morning part to yesterday's.
            minute >= start && time.dayOfWeek in schedule.days -> at(today.plusDays(1), end)
            minute < end && time.dayOfWeek.minus(1) in schedule.days -> at(today, end)
            else -> null
        }
    }

    private companion object {
        const val MINUTE = 60_000L
    }
}
