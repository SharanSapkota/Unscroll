package com.unscroll.app.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.unscroll.app.R
import com.unscroll.app.domain.insights.DayUsage
import com.unscroll.app.ui.theme.Dimens
import com.unscroll.app.ui.theme.UnscrollTheme
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * 24 rounded cells, one per hour of the day, shaded by how much time fell into that hour. No
 * gridlines; four hour labels underneath. [hourly] must have 24 entries.
 */
@Composable
fun HourHeatmap(hourly: List<Long>, description: String, modifier: Modifier = Modifier) {
    val filled = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.surfaceContainerHighest
    val max = hourly.maxOrNull()?.takeIf { it > 0 } ?: 1L
    Column(modifier = modifier.clearAndSetSemantics { contentDescription = description }) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimens.heatmapHeight),
        ) {
            val gap = Dimens.heatmapGap.toPx()
            val cellWidth = (size.width - gap * (hourly.size - 1)) / hourly.size
            hourly.forEachIndexed { hour, millis ->
                val color = if (millis == 0L) {
                    empty
                } else {
                    filled.copy(alpha = MIN_ALPHA + (1f - MIN_ALPHA) * (millis.toFloat() / max))
                }
                drawRoundRect(
                    color = color,
                    topLeft = Offset(hour * (cellWidth + gap), 0f),
                    size = Size(cellWidth, size.height),
                    cornerRadius = CornerRadius(Dimens.barCorner.toPx()),
                )
            }
        }
        // Labels at 00, 06, 12 and 18, each spanning six cells.
        Row(modifier = Modifier.fillMaxWidth()) {
            for (hour in 0 until 24 step 6) {
                Text(
                    text = stringResource(R.string.home_heatmap_hour, hour),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * Paired rounded bars, no gridlines: for each of the last 7 days, the same position in the week
 * before (muted) next to this week (accent). Both weeks share one scale.
 */
@Composable
fun WeekComparisonChart(
    thisWeek: List<DayUsage>,
    lastWeek: List<DayUsage>,
    description: String,
    modifier: Modifier = Modifier,
) {
    val current = MaterialTheme.colorScheme.primary
    val previous = MaterialTheme.colorScheme.surfaceContainerHighest
    val max = (thisWeek + lastWeek).maxOfOrNull { it.totalMillis }?.takeIf { it > 0 } ?: 1L
    val locale = Locale.getDefault()
    Column(modifier = modifier.clearAndSetSemantics { contentDescription = description }) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimens.chartHeight),
        ) {
            val groupWidth = size.width / thisWeek.size
            val barWidth = groupWidth * BAR_SHARE
            val inner = Dimens.heatmapGap.toPx()
            val corner = CornerRadius(Dimens.barCorner.toPx())
            val minBar = Dimens.minBar.toPx()
            thisWeek.indices.forEach { index ->
                val groupStart = index * groupWidth + (groupWidth - barWidth * 2 - inner) / 2
                listOf(
                    lastWeek.getOrNull(index)?.totalMillis to previous,
                    thisWeek[index].totalMillis to current,
                ).forEachIndexed { barIndex, (millis, color) ->
                    val value = millis ?: 0L
                    val height = if (value == 0L) minBar else maxOf(minBar, size.height * value / max)
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(groupStart + barIndex * (barWidth + inner), size.height - height),
                        size = Size(barWidth, height),
                        cornerRadius = corner,
                    )
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            thisWeek.forEach { day ->
                Text(
                    text = day.date.dayOfWeek.getDisplayName(TextStyle.NARROW, locale),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private const val MIN_ALPHA = 0.18f
private const val BAR_SHARE = 0.3f

@PreviewLightDark
@Composable
private fun ChartsPreview() {
    val today = LocalDate.of(2026, 10, 6)
    UnscrollTheme {
        Column {
            HourHeatmap(
                hourly = List(24) { hour -> if (hour in 7..23) hour * 60_000L else 0L },
                description = "",
            )
            WeekComparisonChart(
                thisWeek = List(7) { DayUsage(today.minusDays(6L - it), it * 600_000L) },
                lastWeek = List(7) { DayUsage(today.minusDays(13L - it), (7 - it) * 500_000L) },
                description = "",
            )
        }
    }
}
