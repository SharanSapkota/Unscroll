package com.unscroll.app.domain.scroll

import com.unscroll.app.domain.insights.TimeRange
import com.unscroll.app.domain.insights.UsageDataSource
import com.unscroll.app.domain.insights.UsagePeriod
import com.unscroll.app.domain.insights.localDate
import com.unscroll.app.domain.insights.startOfDay
import java.time.ZoneId
import javax.inject.Inject

/** Raw swipe numbers for one app, for sessions that started in a range. */
data class AppScrollStats(
    val packageName: String,
    val swipes: Int,
    val sessions: Int,
    /** Full length of those sessions; open ones run until now. */
    val durationMillis: Long,
) {
    val averagePerSession: Double get() = if (sessions == 0) 0.0 else swipes.toDouble() / sessions

    val perMinute: Double get() = if (durationMillis <= 0) 0.0 else swipes * 60_000.0 / durationMillis
}

data class ScrollStats(
    val swipesToday: Int,
    val combined: AppScrollStats,
    /** Most swipes first. */
    val apps: List<AppScrollStats>,
)

/**
 * Swipe totals per app for a period. Only sessions since scroll counting was first switched on
 * ([countingSince]) count, so sessions from before don't drag the averages down with zeros.
 */
class GetScrollStatsUseCase @Inject constructor(
    private val source: UsageDataSource,
) {
    suspend operator fun invoke(period: UsagePeriod, countingSince: Long, now: Long, zone: ZoneId): ScrollStats {
        val periodRange = period.range(now, zone)
        val range = TimeRange(maxOf(periodRange.from, countingSince), periodRange.to)
        val apps = stats(range, now)
            .filter { it.sessions > 0 }
            .sortedWith(compareByDescending<AppScrollStats> { it.swipes }.thenBy { it.packageName })
        val todayStart = startOfDay(localDate(now, zone), zone)
        val today = stats(TimeRange(maxOf(todayStart, countingSince), periodRange.to), now)
        return ScrollStats(
            swipesToday = today.sumOf { it.swipes },
            combined = AppScrollStats(
                packageName = "",
                swipes = apps.sumOf { it.swipes },
                sessions = apps.sumOf { it.sessions },
                durationMillis = apps.sumOf { it.durationMillis },
            ),
            apps = apps,
        )
    }

    private suspend fun stats(range: TimeRange, now: Long): List<AppScrollStats> =
        if (range.from >= range.to) emptyList() else source.appScrollStats(range, now)
}
