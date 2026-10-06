package com.unscroll.app.domain.insights

import java.time.ZoneId
import javax.inject.Inject

/** Daily totals for the last 7 days (ending today) and the 7 days before them. */
class GetWeekComparisonUseCase @Inject constructor(
    private val source: UsageDataSource,
) {
    suspend operator fun invoke(now: Long, zone: ZoneId): WeekComparison {
        val today = localDate(now, zone)
        val firstDay = today.minusDays(DAYS * 2L - 1)
        val range = TimeRange(startOfDay(firstDay, zone), startOfDay(today.plusDays(1), zone))
        val days = UsageMath.dailyTotals(
            sessions = source.sessionsOverlapping(range),
            firstDay = firstDay,
            days = DAYS * 2,
            zone = zone,
            now = now,
        )
        val lastWeek = days.take(DAYS)
        val thisWeek = days.drop(DAYS)
        return WeekComparison(
            thisWeek = thisWeek,
            lastWeek = lastWeek,
            trend = Trend(
                currentMillis = thisWeek.sumOf { it.totalMillis },
                previousMillis = lastWeek.sumOf { it.totalMillis },
            ),
        )
    }

    private companion object {
        const val DAYS = 7
    }
}
