package com.unscroll.app.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.unscroll.app.R
import com.unscroll.app.domain.goals.DailyGoal
import com.unscroll.app.domain.insights.TrendDirection
import com.unscroll.app.domain.insights.WeeklyReport
import com.unscroll.app.ui.durationText
import com.unscroll.app.util.appLabel
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

/** Today against the daily goal, plus the streak (M8). */
@Composable
internal fun GoalCard(goal: GoalProgress) {
    val goalMillis = DailyGoal.millis(goal.goalMinutes)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.goal_card_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(
                    R.string.goal_today_of_goal,
                    durationText(goal.todayMillis),
                    durationText(goalMillis),
                ),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            LinearProgressIndicator(
                progress = { (goal.todayMillis.toFloat() / goalMillis).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = if (goal.streak.todayOnTrack) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
            Text(
                text = if (goal.streak.todayOnTrack) {
                    stringResource(R.string.goal_left_today, durationText(goalMillis - goal.todayMillis))
                } else {
                    stringResource(R.string.goal_over_today, durationText(goal.todayMillis - goalMillis))
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (goal.streak.todayOnTrack) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = pluralStringResource(R.plurals.goal_days, goal.streak.current, goal.streak.current),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.goal_current_streak),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = pluralStringResource(R.plurals.goal_days, goal.streak.best, goal.streak.best),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.goal_best_streak),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** The last complete week (M8). */
@Composable
internal fun WeeklyReportCard(report: WeeklyReport) {
    val context = LocalContext.current
    val locale = Locale.getDefault()
    val weekOf = remember(report.weekStart) {
        report.weekStart.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.report_title, weekOf),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = durationText(report.totalMillis),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            val delta = durationText(abs(report.trend.deltaMillis))
            Text(
                text = when (report.trend.direction) {
                    TrendDirection.UP -> stringResource(R.string.report_trend_more, delta)
                    TrendDirection.DOWN -> stringResource(R.string.report_trend_less, delta)
                    TrendDirection.FLAT -> stringResource(R.string.report_trend_same)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (report.trend.direction == TrendDirection.UP) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
            Text(
                text = stringResource(R.string.report_daily_average, durationText(report.dailyAverageMillis)),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(
                    R.string.report_busiest_day,
                    report.busiestDay.date.dayOfWeek.getDisplayName(TextStyle.FULL, locale),
                    durationText(report.busiestDay.totalMillis),
                ),
                style = MaterialTheme.typography.bodyLarge,
            )
            report.topApp?.let { pkg ->
                val name = remember(pkg) { context.packageManager.appLabel(pkg) }
                Text(
                    text = stringResource(R.string.report_top_app, name, durationText(report.topAppMillis)),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            Text(
                text = pluralStringResource(R.plurals.report_opens, report.opens, report.opens),
                style = MaterialTheme.typography.bodyLarge,
            )
            report.goalDaysMet?.let { met ->
                Text(
                    text = stringResource(R.string.report_goal_days, met, report.goalDaysCounted),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}
