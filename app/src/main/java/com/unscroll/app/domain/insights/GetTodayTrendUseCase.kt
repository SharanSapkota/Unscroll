package com.unscroll.app.domain.insights

import java.time.ZoneId
import javax.inject.Inject

/**
 * Today's total so far compared with yesterday up to the same time of day, which is fairer than
 * comparing half a day with a whole one.
 */
class GetTodayTrendUseCase @Inject constructor(
    private val source: UsageDataSource,
) {
    suspend operator fun invoke(now: Long, zone: ZoneId): Trend {
        val today = localDate(now, zone)
        val todayStart = startOfDay(today, zone)
        val yesterdayStart = startOfDay(today.minusDays(1), zone)
        val elapsedToday = now - todayStart

        val todayTotal = source.appTotals(TimeRange(todayStart, now), now).values.sum()
        val yesterdaySoFar = source.appTotals(
            TimeRange(yesterdayStart, minOf(yesterdayStart + elapsedToday, todayStart)),
            now,
        ).values.sum()
        return Trend(currentMillis = todayTotal, previousMillis = yesterdaySoFar)
    }
}
