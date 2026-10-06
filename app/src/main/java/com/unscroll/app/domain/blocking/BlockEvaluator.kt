package com.unscroll.app.domain.blocking

import com.unscroll.app.domain.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject

enum class BlockReason { BLOCKED_ALWAYS, DAILY_LIMIT_REACHED, INSIDE_SCHEDULE }

sealed interface BlockDecision {
    /** [remainingMillis] is the time left under the daily limit, or null without a limit. */
    data class Allowed(val remainingMillis: Long?) : BlockDecision

    /** [until] is when the block lifts by itself, or null for "Block completely". */
    data class Blocked(val reason: BlockReason, val until: Long?) : BlockDecision
}

/**
 * Decides whether an app may be used right now. Pure: the caller supplies today's usage and any
 * active extension, and the time comes from the injected [Clock].
 *
 * Order matters: "Block completely" wins, then the schedule, then the daily limit. An extension
 * granted from the block screen only lifts the daily limit.
 */
class BlockEvaluator @Inject constructor(private val clock: Clock) {

    fun evaluate(
        settings: LimitSettings,
        usedTodayMillis: Long,
        extensionUntil: Long? = null,
        now: Long = clock.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): BlockDecision {
        if (settings.blockedAlways) return BlockDecision.Blocked(BlockReason.BLOCKED_ALWAYS, until = null)

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
