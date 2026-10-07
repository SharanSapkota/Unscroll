package com.unscroll.app.ui.components

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import com.unscroll.app.ui.theme.Dimens

/**
 * Rounded bars, no gridlines: one bar per value, the last (today) in the accent and the rest
 * muted. Labels under the bars are optional. Read out as [description] by screen readers.
 */
@Composable
fun MiniBarChart(
    values: List<Long>,
    description: String,
    modifier: Modifier = Modifier,
    labels: List<String> = emptyList(),
    height: Dp = Dimens.sparklineHeight,
    highlight: Color = MaterialTheme.colorScheme.primary,
    muted: Color = MaterialTheme.colorScheme.primary.copy(alpha = MUTED_ALPHA),
    empty: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
) {
    val max = values.maxOrNull()?.takeIf { it > 0 } ?: 1L
    Column(modifier = modifier.clearAndSetSemantics { contentDescription = description }) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height),
        ) {
            if (values.isEmpty()) return@Canvas
            val gap = Dimens.barGap.toPx()
            val barWidth = (size.width - gap * (values.size - 1)) / values.size
            val corner = CornerRadius(Dimens.barCorner.toPx())
            val minBar = Dimens.minBar.toPx()
            values.forEachIndexed { index, value ->
                val barHeight = if (value <= 0L) minBar else maxOf(minBar, size.height * value / max)
                drawRoundRect(
                    color = when {
                        value <= 0L -> empty
                        index == values.lastIndex -> highlight
                        else -> muted
                    },
                    topLeft = Offset(index * (barWidth + gap), size.height - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = corner,
                )
            }
        }
        if (labels.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxWidth()) {
                labels.forEach { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

private const val MUTED_ALPHA = 0.35f
