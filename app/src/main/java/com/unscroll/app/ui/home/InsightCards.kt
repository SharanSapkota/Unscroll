package com.unscroll.app.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.unscroll.app.R
import com.unscroll.app.domain.goals.DailyGoal
import com.unscroll.app.domain.insights.HoursInvested
import com.unscroll.app.domain.insights.TrendDirection
import com.unscroll.app.domain.insights.UsageSummary
import com.unscroll.app.domain.insights.WeekComparison
import com.unscroll.app.domain.insights.WeeklyReport
import com.unscroll.app.domain.scroll.ScrollStats
import com.unscroll.app.ui.components.ProgressLevel
import com.unscroll.app.ui.components.ProgressPill
import com.unscroll.app.ui.components.StatCard
import com.unscroll.app.ui.components.StatValue
import com.unscroll.app.ui.components.UnscrollCard
import com.unscroll.app.ui.components.rememberAppLabel
import com.unscroll.app.ui.durationText
import com.unscroll.app.ui.theme.Dimens
import com.unscroll.app.ui.theme.Motion
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/** Today against the daily goal, and the streak, as one small pill under the hero number. */
@Composable
internal fun GoalPill(goal: GoalProgress, modifier: Modifier = Modifier) {
    val goalMillis = DailyGoal.millis(goal.goalMinutes)
    val onTrack = goal.streak.todayOnTrack
    val text = if (onTrack) {
        stringResource(R.string.home_goal_left, durationText(goalMillis - goal.todayMillis), goal.streak.current)
    } else {
        stringResource(R.string.home_goal_over, durationText(goal.todayMillis - goalMillis))
    }
    ProgressPill(
        text = text,
        modifier = modifier,
        level = ProgressLevel.of(goal.todayMillis.toFloat() / goalMillis),
        icon = R.drawable.ic_flag,
    )
}

/** "212 h" all time, one fun equivalent; tap to see more. */
@Composable
internal fun HoursInvestedCard(invested: HoursInvested, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    StatCard(
        value = stringResource(R.string.home_hours_value, invested.hours),
        label = stringResource(R.string.home_hours_title),
        detail = stringResource(R.string.home_hours_days, invested.days),
        onClick = { expanded = !expanded },
        modifier = modifier.fillMaxWidth(),
    ) {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(Motion.MEDIUM)) + expandVertically(tween(Motion.MEDIUM)),
            exit = fadeOut(tween(Motion.SHORT)) + shrinkVertically(tween(Motion.SHORT)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Dimens.spaceM),
                horizontalArrangement = Arrangement.spacedBy(Dimens.spaceL),
            ) {
                StatValue(
                    value = stringResource(R.string.home_number_one_decimal, invested.books),
                    label = stringResource(R.string.home_hours_books),
                    modifier = Modifier.weight(1f),
                )
                StatValue(
                    value = stringResource(R.string.home_number_one_decimal, invested.flightsToTokyo),
                    label = stringResource(R.string.home_hours_flights),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** A titled card for the Insights section. */
@Composable
internal fun InsightCard(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    UnscrollCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Dimens.spaceXl),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceM),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            content()
        }
    }
}

/** All tracked apps in the period: opens, average and longest session. */
@Composable
internal fun SessionsCard(summary: UsageSummary) {
    InsightCard(title = stringResource(R.string.home_sessions_title)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.spaceL)) {
            StatValue(
                value = summary.opens.toString(),
                label = stringResource(R.string.detail_stats_opens),
                modifier = Modifier.weight(1f),
            )
            StatValue(
                value = durationText(summary.averageSessionMillis),
                label = stringResource(R.string.detail_stats_average),
                modifier = Modifier.weight(1f),
            )
            StatValue(
                value = durationText(summary.longestSessionMillis),
                label = stringResource(R.string.stats_longest),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
internal fun HeatmapCard(hourly: List<Long>) {
    val peakHour = hourly.indices.maxByOrNull { hourly[it] }?.takeIf { hourly[it] > 0 }
    InsightCard(title = stringResource(R.string.home_heatmap_title)) {
        if (peakHour != null) {
            Text(
                text = stringResource(R.string.home_heatmap_peak, peakHour, (peakHour + 1) % 24),
                style = MaterialTheme.typography.headlineSmall,
            )
        }
        HourHeatmap(
            hourly = hourly,
            description = stringResource(R.string.home_heatmap_description, peakHour ?: 0),
        )
    }
}

@Composable
internal fun WeekCard(comparison: WeekComparison) {
    val trend = comparison.trend
    val thisWeek = durationText(trend.currentMillis)
    val lastWeek = durationText(trend.previousMillis)
    InsightCard(title = stringResource(R.string.home_week_title)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceXl)) {
            StatValue(value = thisWeek, label = stringResource(R.string.home_week_this))
            StatValue(
                value = lastWeek,
                label = stringResource(R.string.home_week_last),
                valueColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        WeekComparisonChart(
            thisWeek = comparison.thisWeek,
            lastWeek = comparison.lastWeek,
            description = stringResource(R.string.home_week_description, thisWeek, lastWeek),
        )
    }
}

/** Swipes in the period, per app (M7). Hidden until scroll counting was ever on. */
@Composable
internal fun SwipesCard(stats: ScrollStats) {
    InsightCard(title = stringResource(R.string.home_swipes_title)) {
        StatValue(
            value = stats.combined.swipes.toString(),
            label = stringResource(R.string.home_swipes_total),
        )
        stats.apps.forEach { app ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.spaceL)) {
                StatValue(
                    value = app.swipes.toString(),
                    label = rememberAppLabel(app.packageName),
                    modifier = Modifier.weight(1f),
                )
                StatValue(
                    value = stringResource(R.string.home_number_one_decimal, app.averagePerSession),
                    label = stringResource(R.string.home_swipes_per_session),
                    modifier = Modifier.weight(1f),
                )
                StatValue(
                    value = stringResource(R.string.home_number_one_decimal, app.perMinute),
                    label = stringResource(R.string.home_swipes_per_minute),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** The last complete week (M8), numbers first. */
@Composable
internal fun WeeklyReportCard(report: WeeklyReport) {
    val locale = Locale.getDefault()
    val weekOf = remember(report.weekStart) {
        report.weekStart.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    }
    InsightCard(title = stringResource(R.string.home_report_title, weekOf)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = durationText(report.totalMillis),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
            )
            report.trend.percentChange?.let { percent ->
                TrendPill(percent = percent, direction = report.trend.direction, versus = R.string.home_trend_vs_week)
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.spaceL)) {
            StatValue(
                value = durationText(report.dailyAverageMillis),
                label = stringResource(R.string.home_report_average),
                modifier = Modifier.weight(1f),
            )
            StatValue(
                value = report.busiestDay.date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale),
                label = stringResource(R.string.home_report_busiest),
                modifier = Modifier.weight(1f),
            )
            StatValue(
                value = report.opens.toString(),
                label = pluralStringResource(R.plurals.home_report_opens, report.opens),
                modifier = Modifier.weight(1f),
            )
        }
        report.topApp?.let { pkg ->
            Text(
                text = stringResource(R.string.home_report_top_app, rememberAppLabel(pkg), durationText(report.topAppMillis)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        report.goalDaysMet?.let { met ->
            Text(
                text = stringResource(R.string.home_report_goal_days, met, report.goalDaysCounted),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** "-18% vs yesterday": less is good news (green), more is red. */
@Composable
internal fun TrendPill(percent: Int, direction: TrendDirection, versus: Int, modifier: Modifier = Modifier) {
    ProgressPill(
        text = stringResource(versus, stringResource(R.string.home_trend_percent, percent)),
        modifier = modifier,
        level = when (direction) {
            TrendDirection.UP -> ProgressLevel.DANGER
            TrendDirection.DOWN -> ProgressLevel.GOOD
            TrendDirection.FLAT -> null
        },
    )
}
