package com.unscroll.app.domain.overlay

import com.unscroll.app.domain.insights.localDate
import com.unscroll.app.domain.insights.startOfDay
import java.time.ZoneId

/**
 * What the pill needs to count today's total for the app on screen, measured once when the app
 * came to the foreground. The live part is added from the clock every second, so the database is
 * not queried while the pill ticks.
 */
data class PillTodayBase(
    val packageName: String,
    /** The open session ([sessionStart] is its start), so it is never counted twice. */
    val sessionId: Long,
    val sessionStart: Long,
    /** Local midnight of the day the totals below belong to. */
    val dayStart: Long,
    /** Time in the app's earlier sessions since [dayStart] (clipped to it), excluding the open one. */
    val completedMillis: Long,
    /** Swipes in the app's earlier sessions that started since [dayStart], excluding the open one. */
    val completedSwipes: Int,
    /**
     * The highest total already shown for the app today. The pill never shows less, so the total
     * can't step back when a session's stored end is a moment before the pill last ticked.
     */
    val floorMillis: Long = 0,
)

/** Pure rules for the pill's numbers: today's total, this visit, today's swipes. */
object PillToday {

    fun dayStart(now: Long, zone: ZoneId): Long = startOfDay(localDate(now, zone), zone)

    /**
     * Today's total at [now]: the earlier sessions plus the open one's time since midnight. Once
     * [now] is on a later day than [base], only the open session's time since that day's midnight
     * counts (the total starts again from zero at midnight).
     */
    fun totalMillis(base: PillTodayBase, now: Long, zone: ZoneId): Long {
        val today = dayStart(now, zone)
        if (today != base.dayStart) return (now - maxOf(base.sessionStart, today)).coerceAtLeast(0)
        val live = (now - maxOf(base.sessionStart, base.dayStart)).coerceAtLeast(0)
        return maxOf(base.completedMillis + live, base.floorMillis)
    }

    /** This visit (the open session), for "this visit 3:05". */
    fun sessionMillis(base: PillTodayBase, now: Long): Long = (now - base.sessionStart).coerceAtLeast(0)

    /**
     * Today's swipes: the earlier sessions' plus [sessionSwipes] (the open session's). After
     * midnight only the open session's count is left.
     */
    fun swipes(base: PillTodayBase, sessionSwipes: Int, now: Long, zone: ZoneId): Int =
        if (dayStart(now, zone) == base.dayStart) base.completedSwipes + sessionSwipes else sessionSwipes

    /** The floor to carry into the app's next base today: what the pill shows at [now]. */
    fun shownAt(base: PillTodayBase, now: Long, zone: ZoneId): ShownTotal =
        ShownTotal(dayStart(now, zone), totalMillis(base, now, zone))
}

/** The last total shown for an app, and the day it belongs to. */
data class ShownTotal(val dayStart: Long, val millis: Long) {
    /** The floor for a new base on [dayStart]: this total if it is from the same day, else 0. */
    fun floorFor(dayStart: Long): Long = if (dayStart == this.dayStart) millis else 0
}
