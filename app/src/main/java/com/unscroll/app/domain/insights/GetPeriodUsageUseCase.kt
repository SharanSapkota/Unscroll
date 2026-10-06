package com.unscroll.app.domain.insights

import java.time.ZoneId
import javax.inject.Inject

/** Totals, opens, average and longest session, per app and combined, plus the hourly heatmap. */
class GetPeriodUsageUseCase @Inject constructor(
    private val source: UsageDataSource,
) {
    suspend operator fun invoke(period: UsagePeriod, now: Long, zone: ZoneId): PeriodUsage {
        val range = period.range(now, zone)
        val totals = source.appTotals(range, now)
        val stats = source.appSessionStats(range, now).associateBy { it.packageName }

        val apps = (totals.keys + stats.keys)
            .map { packageName ->
                AppUsage(packageName, summary(totals[packageName] ?: 0L, stats[packageName]))
            }
            .filter { it.usage.totalMillis > 0 || it.usage.opens > 0 }
            .sortedWith(
                compareByDescending<AppUsage> { it.usage.totalMillis }.thenBy { it.packageName },
            )

        val combinedStats = AppSessionStats(
            packageName = "",
            opens = stats.values.sumOf { it.opens },
            totalDurationMillis = stats.values.sumOf { it.totalDurationMillis },
            longestMillis = stats.values.maxOfOrNull { it.longestMillis } ?: 0L,
        )
        val combined = summary(totals.values.sum(), combinedStats)

        val hourly = UsageMath.hourlyTotals(source.sessionsOverlapping(range), range, zone, now)
        return PeriodUsage(period, combined, apps, hourly)
    }

    private fun summary(totalMillis: Long, stats: AppSessionStats?): UsageSummary {
        val opens = stats?.opens ?: 0
        return UsageSummary(
            totalMillis = totalMillis,
            opens = opens,
            averageSessionMillis = if (opens == 0) 0L else stats!!.totalDurationMillis / opens,
            longestSessionMillis = stats?.longestMillis ?: 0L,
        )
    }
}
