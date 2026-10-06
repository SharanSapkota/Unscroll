package com.unscroll.app.domain.goals

import com.unscroll.app.domain.insights.TimeRange
import com.unscroll.app.domain.insights.UsageDataSource
import com.unscroll.app.domain.insights.UsageMath
import com.unscroll.app.domain.insights.localDate
import com.unscroll.app.domain.insights.startOfDay
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/**
 * Streak history for a daily goal: complete days from the first tracked day (at most
 * [DailyGoal.HISTORY_DAYS] back) up to yesterday. Days before Unscroll tracked anything don't count.
 */
class GetStreakHistoryUseCase @Inject constructor(
    private val source: UsageDataSource,
) {
    suspend operator fun invoke(goalMinutes: Int, now: Long, zone: ZoneId): StreakHistory {
        val today = localDate(now, zone)
        val firstTracked = source.firstSessionStart()?.let { localDate(it, zone) }
            ?: return StreakHistory(0, 0)
        val firstDay = maxOf(firstTracked, today.minusDays(DailyGoal.HISTORY_DAYS.toLong()))
        if (!firstDay.isBefore(today)) return StreakHistory(0, 0)
        val range = TimeRange(startOfDay(firstDay, zone), startOfDay(today, zone))
        val totals = UsageMath.totalsByDay(source.sessionsOverlapping(range), range, zone, now)
        val days = ChronoUnit.DAYS.between(firstDay, today)
        val daily = (0 until days).map { totals[firstDay.plusDays(it)] ?: 0L }
        return StreakRules.history(daily, DailyGoal.millis(goalMinutes))
    }
}
