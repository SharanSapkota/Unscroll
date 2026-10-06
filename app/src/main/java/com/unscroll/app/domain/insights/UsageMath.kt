package com.unscroll.app.domain.insights

import com.unscroll.app.domain.session.Session
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Splits sessions into local days and hours. Open sessions run until `now`. */
object UsageMath {

    /** The part of [session] inside [range], in milliseconds. */
    fun overlap(session: Session, range: TimeRange, now: Long): Long {
        val start = maxOf(session.startTime, range.from)
        val end = minOf(session.endTime ?: now, range.to)
        return (end - start).coerceAtLeast(0)
    }

    /**
     * Total time per local day for [days] days starting at [firstDay]. A session that crosses
     * midnight is split between the two days.
     */
    fun dailyTotals(
        sessions: List<Session>,
        firstDay: LocalDate,
        days: Int,
        zone: ZoneId,
        now: Long,
    ): List<DayUsage> = (0 until days).map { offset ->
        val date = firstDay.plusDays(offset.toLong())
        val range = TimeRange(startOfDay(date, zone), startOfDay(date.plusDays(1), zone))
        DayUsage(date, sessions.sumOf { overlap(it, range, now) })
    }

    /** Time per local hour of day (24 buckets) for the parts of sessions inside [range]. */
    fun hourlyTotals(sessions: List<Session>, range: TimeRange, zone: ZoneId, now: Long): List<Long> {
        val buckets = LongArray(HOURS_PER_DAY)
        for (session in sessions) {
            var cursor = maxOf(session.startTime, range.from)
            val end = minOf(session.endTime ?: now, range.to)
            while (cursor < end) {
                val time = Instant.ofEpochMilli(cursor).atZone(zone)
                val nextHour = time.truncatedTo(ChronoUnit.HOURS).plusHours(1)
                    .toInstant().toEpochMilli()
                val segmentEnd = minOf(end, nextHour)
                buckets[time.hour] += segmentEnd - cursor
                cursor = segmentEnd
            }
        }
        return buckets.toList()
    }

    private const val HOURS_PER_DAY = 24
}
