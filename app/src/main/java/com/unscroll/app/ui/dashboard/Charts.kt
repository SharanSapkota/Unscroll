package com.unscroll.app.ui.dashboard

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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.unscroll.app.R
import com.unscroll.app.domain.insights.DayUsage
import com.unscroll.app.ui.theme.UnscrollTheme
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * 24 cells, one per hour of the day, shaded by how much time fell into that hour.
 * [hourly] must have 24 entries.
 */
@Composable
fun HourHeatmap(hourly: List<Long>, description: String, modifier: Modifier = Modifier) {
    val filled = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.surfaceVariant
    val max = hourly.maxOrNull()?.takeIf { it > 0 } ?: 1L
    Column(modifier = modifier.clearAndSetSemantics { contentDescription = description }) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp),
        ) {
            val gap = 2.dp.toPx()
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
                    cornerRadius = CornerRadius(3.dp.toPx()),
                )
            }
        }
        // Labels at 00, 06, 12 and 18, each spanning six cells.
        Row(modifier = Modifier.fillMaxWidth()) {
            for (hour in 0 until 24 step 6) {
                Text(
                    text = stringResource(R.string.dashboard_heatmap_hour, hour),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * Grouped bars: for each of the last 7 days, the same weekday position from the week before
 * (muted) next to this week (primary). Both weeks share one scale.
 */
@Composable
fun WeekComparisonChart(
    thisWeek: List<DayUsage>,
    lastWeek: List<DayUsage>,
    description: String,
    modifier: Modifier = Modifier,
) {
    val current = MaterialTheme.colorScheme.primary
    val previous = MaterialTheme.colorScheme.outlineVariant
    val max = (thisWeek + lastWeek).maxOfOrNull { it.totalMillis }?.takeIf { it > 0 } ?: 1L
    val locale = Locale.getDefault()
    Column(modifier = modifier.clearAndSetSemantics { contentDescription = description }) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
        ) {
            val groupWidth = size.width / thisWeek.size
            val barWidth = groupWidth * 0.3f
            val corner = CornerRadius(3.dp.toPx())
            val minBar = 2.dp.toPx()
            thisWeek.indices.forEach { index ->
                val groupStart = index * groupWidth + groupWidth * 0.15f
                listOf(
                    lastWeek.getOrNull(index)?.totalMillis to previous,
                    thisWeek[index].totalMillis to current,
                ).forEachIndexed { barIndex, (millis, color) ->
                    val value = millis ?: 0L
                    val height = if (value == 0L) {
                        minBar
                    } else {
                        maxOf(minBar, size.height * value / max)
                    }
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(groupStart + barIndex * barWidth * 1.1f, size.height - height),
                        size = Size(barWidth, height),
                        cornerRadius = corner,
                    )
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            thisWeek.forEach { day ->
                Text(
                    text = day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private const val MIN_ALPHA = 0.15f

@Preview(showBackground = true)
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
