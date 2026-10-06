package com.unscroll.app.domain.insights

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

/** Last complete calendar week, next to the week before it. */
data class WeeklyReport(
    /** First day of the reported week. */
    val weekStart: LocalDate,
    val totalMillis: Long,
    /** Against the week before. */
    val trend: Trend,
    val dailyAverageMillis: Long,
    /** The day with the most time. */
    val busiestDay: DayUsage,
    /** App with the most time, and its time. */
    val topApp: String?,
    val topAppMillis: Long,
    val opens: Int,
    /** Days within the daily goal, or null without a goal. Only days since tracking started count. */
    val goalDaysMet: Int?,
    val goalDaysCounted: Int,
)

class GetWeeklyReportUseCase @Inject constructor(
    private val source: UsageDataSource,
) {
    /** Null when nothing was tracked in the last complete week. */
    suspend operator fun invoke(
        now: Long,
        zone: ZoneId,
        firstDayOfWeek: DayOfWeek,
        goalMinutes: Int?,
    ): WeeklyReport? {
        val today = localDate(now, zone)
        val thisWeekStart = today.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
        val weekStart = thisWeekStart.minusWeeks(1)
        val previousStart = weekStart.minusWeeks(1)
        val twoWeeks = TimeRange(startOfDay(previousStart, zone), startOfDay(thisWeekStart, zone))
        val days = UsageMath.dailyTotals(source.sessionsOverlapping(twoWeeks), previousStart, DAYS * 2, zone, now)
        val previous = days.take(DAYS)
        val week = days.drop(DAYS)
        val total = week.sumOf { it.totalMillis }
        if (total == 0L) return null

        val weekRange = TimeRange(startOfDay(weekStart, zone), startOfDay(thisWeekStart, zone))
        val top = source.appTotals(weekRange, now).entries
            .maxWithOrNull(compareBy<Map.Entry<String, Long>>({ it.value }, { it.key }))
        val firstTracked = source.firstSessionStart()?.let { localDate(it, zone) }
        val countedDays = week.filter { firstTracked == null || !it.date.isBefore(firstTracked) }
        return WeeklyReport(
            weekStart = weekStart,
            totalMillis = total,
            trend = Trend(currentMillis = total, previousMillis = previous.sumOf { it.totalMillis }),
            dailyAverageMillis = total / DAYS,
            busiestDay = week.maxWith(compareBy<DayUsage> { it.totalMillis }.thenByDescending { it.date }),
            topApp = top?.key,
            topAppMillis = top?.value ?: 0L,
            opens = source.appSessionStats(weekRange, now).sumOf { it.opens },
            goalDaysMet = goalMinutes?.let { goal ->
                countedDays.count { it.totalMillis <= goal * 60_000L }
            },
            goalDaysCounted = countedDays.size,
        )
    }

    private companion object {
        const val DAYS = 7
    }
}
