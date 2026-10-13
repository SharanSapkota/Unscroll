package com.unscroll.app.domain.insights

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlin.random.Random

/** When Insights show: how many a day, and the weekly life projection's day. */
object InsightSchedule {

    /** The Home card shows at most this many messages a day ("Next" included). */
    const val DAILY_MAX = 10

    /** The weekly life projection card (and its optional notification) comes on this day. */
    val WEEKLY_DAY: DayOfWeek = DayOfWeek.SUNDAY

    /** The optional weekly notification is posted around this local time on [WEEKLY_DAY]. */
    val NOTIFY_AT: LocalTime = LocalTime.of(18, 0)

    fun isWeeklyDay(now: Long, zone: ZoneId): Boolean = localDate(now, zone).dayOfWeek == WEEKLY_DAY

    /** The next [WEEKLY_DAY] at [NOTIFY_AT] after [now] (today's, if it is still ahead). */
    fun nextNotification(now: Long, zone: ZoneId): Long {
        val today = Instant.ofEpochMilli(now).atZone(zone)
        var at = today.toLocalDate().with(TemporalAdjusters.nextOrSame(WEEKLY_DAY)).atTime(NOTIFY_AT).atZone(zone)
        if (!at.toInstant().isAfter(today.toInstant())) {
            at = at.toLocalDate().plusWeeks(1).atTime(NOTIFY_AT).atZone(zone)
        }
        return at.toInstant().toEpochMilli()
    }

    /**
     * The random source that renders a picked message: the same all day for the same message, so
     * its landmark doesn't change every time the numbers refresh.
     */
    fun stableRandom(templateId: String, epochDay: Long): Random = Random(templateId.hashCode() * 31L + epochDay)
}
