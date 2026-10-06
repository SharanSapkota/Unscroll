package com.unscroll.app.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.unscroll.app.R
import com.unscroll.app.data.friction.PauseStat
import com.unscroll.app.domain.insights.AppUsage
import com.unscroll.app.domain.insights.DayUsage
import com.unscroll.app.domain.insights.GetHoursInvestedUseCase
import com.unscroll.app.domain.insights.HoursInvested
import com.unscroll.app.domain.insights.PeriodUsage
import com.unscroll.app.domain.insights.Trend
import com.unscroll.app.domain.insights.TrendDirection
import com.unscroll.app.domain.insights.UsagePeriod
import com.unscroll.app.domain.insights.UsageSummary
import com.unscroll.app.domain.insights.WeekComparison
import com.unscroll.app.domain.scroll.AppScrollStats
import com.unscroll.app.domain.scroll.ScrollStats
import com.unscroll.app.domain.tracking.TrackedApps
import com.unscroll.app.ui.durationText
import com.unscroll.app.ui.scroll.ScrollCountingBanner
import com.unscroll.app.ui.theme.UnscrollTheme
import com.unscroll.app.util.appLabel
import java.time.LocalDate
import kotlin.math.abs

@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    DashboardContent(
        uiState = uiState,
        onPeriodSelected = viewModel::selectPeriod,
        modifier = modifier,
        banner = { ScrollCountingBanner() },
    )
}

@Composable
private fun DashboardContent(
    uiState: DashboardUiState,
    onPeriodSelected: (UsagePeriod) -> Unit,
    modifier: Modifier = Modifier,
    banner: @Composable () -> Unit = {},
) {
    when {
        uiState.isLoading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        !uiState.hasAnyData -> EmptyState(uiState.trackingEnabled, modifier)
        else -> DashboardList(uiState, onPeriodSelected, modifier, banner)
    }
}

@Composable
private fun DashboardList(
    uiState: DashboardUiState,
    onPeriodSelected: (UsagePeriod) -> Unit,
    modifier: Modifier = Modifier,
    banner: @Composable () -> Unit = {},
) {
    val periodUsage = uiState.periodUsage
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { TodayHeader(uiState.todayTrend) }
        // Only shows when the system switched scroll counting off after it had been working.
        item { banner() }
        uiState.goal?.let { goal -> item { GoalCard(goal) } }
        if (!uiState.trackingEnabled) {
            item {
                Text(
                    text = stringResource(R.string.dashboard_tracking_off),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        item { PeriodSelector(uiState.period, onPeriodSelected) }
        if (periodUsage != null) {
            item {
                UsageCard(
                    title = stringResource(R.string.dashboard_all_apps),
                    packageName = null,
                    usage = periodUsage.combined,
                )
            }
            if (periodUsage.apps.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.dashboard_no_usage_in_period),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(periodUsage.apps, key = { it.packageName }) { app ->
                UsageCard(
                    title = rememberAppLabel(app.packageName),
                    packageName = app.packageName,
                    usage = app.usage,
                )
            }
            item { HeatmapCard(periodUsage.hourly) }
        }
        uiState.scrollStats?.let { stats -> item { SwipesCard(stats) } }
        if (uiState.pauseStats.isNotEmpty()) {
            item { PausesCard(uiState.pauseStats) }
        }
        uiState.weeklyReport?.let { report -> item { WeeklyReportCard(report) } }
        item { HoursInvestedCard(uiState.hoursInvested) }
        uiState.weekComparison?.let { comparison -> item { WeekCard(comparison) } }
    }
}

@Composable
private fun TodayHeader(trend: Trend) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.dashboard_today_label),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = durationText(trend.currentMillis),
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Bold,
        )
        val delta = durationText(abs(trend.deltaMillis))
        Text(
            text = when (trend.direction) {
                TrendDirection.UP -> stringResource(R.string.dashboard_trend_more_than_yesterday, delta)
                TrendDirection.DOWN -> stringResource(R.string.dashboard_trend_less_than_yesterday, delta)
                TrendDirection.FLAT -> stringResource(R.string.dashboard_trend_same_as_yesterday)
            },
            style = MaterialTheme.typography.bodyLarge,
            color = trend.direction.color(),
        )
    }
}

/** More scrolling is bad news, so "up" uses the error color. */
@Composable
private fun TrendDirection.color(): Color = when (this) {
    TrendDirection.UP -> MaterialTheme.colorScheme.error
    TrendDirection.DOWN -> MaterialTheme.colorScheme.primary
    TrendDirection.FLAT -> MaterialTheme.colorScheme.onSurfaceVariant
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeriodSelector(selected: UsagePeriod, onSelected: (UsagePeriod) -> Unit) {
    val periods = UsagePeriod.entries
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        periods.forEachIndexed { index, period ->
            SegmentedButton(
                selected = period == selected,
                onClick = { onSelected(period) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = periods.size),
            ) {
                Text(stringResource(period.labelRes()))
            }
        }
    }
}

private fun UsagePeriod.labelRes(): Int = when (this) {
    UsagePeriod.TODAY -> R.string.dashboard_period_today
    UsagePeriod.WEEK -> R.string.dashboard_period_week
    UsagePeriod.MONTH -> R.string.dashboard_period_month
    UsagePeriod.ALL_TIME -> R.string.dashboard_period_all_time
}

@Composable
private fun UsageCard(title: String, packageName: String?, usage: UsageSummary) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                packageName?.let { pkg ->
                    rememberAppIcon(pkg)?.let { icon ->
                        Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.width(12.dp))
                    }
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = durationText(usage.totalMillis),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                Stat(
                    label = stringResource(R.string.dashboard_stat_opens),
                    value = usage.opens.toString(),
                    modifier = Modifier.weight(1f),
                )
                Stat(
                    label = stringResource(R.string.dashboard_stat_average),
                    value = durationText(usage.averageSessionMillis),
                    modifier = Modifier.weight(1f),
                )
                Stat(
                    label = stringResource(R.string.dashboard_stat_longest),
                    value = durationText(usage.longestSessionMillis),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HeatmapCard(hourly: List<Long>) {
    val peakHour = hourly.indices.maxByOrNull { hourly[it] }?.takeIf { hourly[it] > 0 }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.dashboard_heatmap_title),
                style = MaterialTheme.typography.titleMedium,
            )
            if (peakHour != null) {
                Text(
                    text = stringResource(R.string.dashboard_heatmap_peak, peakHour, (peakHour + 1) % 24),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HourHeatmap(
                hourly = hourly,
                description = stringResource(R.string.dashboard_heatmap_description, peakHour ?: 0),
            )
        }
    }
}

/** Swipes today, and swipes per app for the selected period (M7). Hidden until counting was ever on. */
@Composable
private fun SwipesCard(stats: ScrollStats) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.dashboard_swipes_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = pluralStringResource(R.plurals.dashboard_swipes_today, stats.swipesToday, stats.swipesToday),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            if (stats.apps.isEmpty()) {
                Text(
                    text = stringResource(R.string.dashboard_swipes_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            stats.apps.forEach { app ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = rememberAppLabel(app.packageName),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    SwipeStatsRow(app)
                }
            }
        }
    }
}

@Composable
private fun SwipeStatsRow(stats: AppScrollStats) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Stat(
            label = stringResource(R.string.dashboard_swipes_total),
            value = stats.swipes.toString(),
            modifier = Modifier.weight(1f),
        )
        Stat(
            label = stringResource(R.string.dashboard_swipes_per_session),
            value = stringResource(R.string.dashboard_swipes_decimal, stats.averagePerSession),
            modifier = Modifier.weight(1f),
        )
        Stat(
            label = stringResource(R.string.dashboard_swipes_per_minute),
            value = stringResource(R.string.dashboard_swipes_decimal, stats.perMinute),
            modifier = Modifier.weight(1f),
        )
    }
}

/** "Pauses that saved you": how often the pause screen talked the user out of an app today. */
@Composable
private fun PausesCard(stats: List<PauseStat>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.dashboard_pauses_title),
                style = MaterialTheme.typography.titleMedium,
            )
            val skipped = stats.sumOf { it.abandoned }
            Text(
                text = pluralStringResource(R.plurals.dashboard_pauses_total, skipped, skipped, stats.sumOf { it.shown }),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            stats.filter { it.abandoned > 0 }.forEach { stat ->
                Text(
                    text = pluralStringResource(
                        R.plurals.dashboard_pauses_skipped_app,
                        stat.abandoned,
                        rememberAppLabel(stat.packageName),
                        stat.abandoned,
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

@Composable
private fun HoursInvestedCard(invested: HoursInvested) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.dashboard_hours_invested_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.dashboard_hours_invested_total, invested.hours),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.dashboard_hours_invested_days, invested.days),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(R.string.dashboard_hours_invested_books, invested.books),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(R.string.dashboard_hours_invested_flights, invested.flightsToTokyo),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun WeekCard(comparison: WeekComparison) {
    val trend = comparison.trend
    val thisWeek = durationText(trend.currentMillis)
    val lastWeek = durationText(trend.previousMillis)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.dashboard_week_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.dashboard_week_totals, thisWeek, lastWeek),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            val delta = durationText(abs(trend.deltaMillis))
            Text(
                text = when (trend.direction) {
                    TrendDirection.UP -> stringResource(R.string.dashboard_week_more, delta)
                    TrendDirection.DOWN -> stringResource(R.string.dashboard_week_less, delta)
                    TrendDirection.FLAT -> stringResource(R.string.dashboard_week_same)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = trend.direction.color(),
            )
            WeekComparisonChart(
                thisWeek = comparison.thisWeek,
                lastWeek = comparison.lastWeek,
                description = stringResource(R.string.dashboard_week_description, thisWeek, lastWeek),
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LegendItem(MaterialTheme.colorScheme.primary, stringResource(R.string.dashboard_week_this))
                LegendItem(MaterialTheme.colorScheme.outlineVariant, stringResource(R.string.dashboard_week_last))
            }
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.size(10.dp)) { drawCircle(color) }
        Spacer(Modifier.width(6.dp))
        Text(text = label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun EmptyState(trackingEnabled: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.dashboard_empty_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = stringResource(
                if (trackingEnabled) R.string.dashboard_empty_body else R.string.dashboard_empty_tracking_off,
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun rememberAppLabel(packageName: String): String {
    val packageManager = LocalContext.current.packageManager
    return remember(packageName) { packageManager.appLabel(packageName) }
}

@Composable
private fun rememberAppIcon(packageName: String): ImageBitmap? {
    val packageManager = LocalContext.current.packageManager
    return remember(packageName) {
        runCatching { packageManager.getApplicationIcon(packageName).toBitmap().asImageBitmap() }
            .getOrNull()
    }
}

@Preview(showBackground = true, heightDp = 1600)
@Composable
private fun DashboardPreview() {
    val today = LocalDate.of(2026, 10, 6)
    val instagram = UsageSummary(2_700_000, 9, 300_000, 1_200_000)
    UnscrollTheme {
        DashboardContent(
            uiState = DashboardUiState(
                isLoading = false,
                period = UsagePeriod.TODAY,
                todayTrend = Trend(currentMillis = 3_900_000, previousMillis = 3_000_000),
                periodUsage = PeriodUsage(
                    period = UsagePeriod.TODAY,
                    combined = UsageSummary(3_900_000, 14, 278_000, 1_200_000),
                    apps = listOf(
                        AppUsage(TrackedApps.INSTAGRAM, instagram),
                        AppUsage(TrackedApps.TIKTOK, UsageSummary(1_200_000, 5, 240_000, 600_000)),
                    ),
                    hourly = List(24) { hour -> if (hour in 8..23) hour * 20_000L else 0L },
                ),
                weekComparison = WeekComparison(
                    thisWeek = List(7) { DayUsage(today.minusDays(6L - it), 3_000_000L + it * 200_000L) },
                    lastWeek = List(7) { DayUsage(today.minusDays(13L - it), 3_600_000L) },
                    trend = Trend(23_100_000, 25_200_000),
                ),
                hoursInvested = GetHoursInvestedUseCase.calculate(130 * 3_600_000L),
            ),
            onPeriodSelected = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DashboardEmptyPreview() {
    UnscrollTheme {
        DashboardContent(
            uiState = DashboardUiState(isLoading = false),
            onPeriodSelected = {},
        )
    }
}
