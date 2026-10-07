package com.unscroll.app.overlay

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unscroll.app.R
import com.unscroll.app.domain.overlay.PillLevel
import com.unscroll.app.domain.overlay.PillSize

/** Everything the pill needs to draw one frame. */
data class TimerPillState(
    val appName: String,
    /** Session time, already formatted ("12:41"). */
    val elapsedText: String,
    /** Today's total for the app, already formatted, or null to hide it. */
    val todayText: String?,
    val level: PillLevel,
    val size: PillSize,
    val opacity: Float,
    val collapsed: Boolean,
    /** "86 swipes", already formatted, or null to hide. Shown right after the time. */
    val swipesText: String? = null,
    /** "12 swipes left" near a swipe limit, or null. */
    val remainingText: String? = null,
)

/** Fixed traffic-light colors: they must read the same over any app, in light or dark mode. */
private fun PillLevel.background(): Color = when (this) {
    PillLevel.CALM -> Color(0xFF2E7D32)
    PillLevel.WARNING -> Color(0xFFF9A825)
    PillLevel.DANGER -> Color(0xFFC62828)
}

private fun PillLevel.content(): Color = when (this) {
    PillLevel.WARNING -> Color(0xFF1C1B1F)
    PillLevel.CALM, PillLevel.DANGER -> Color.White
}

/**
 * The floating timer: "Instagram 12:41" (plus today's total if enabled), or a small dot when
 * collapsed. Colors animate between levels.
 */
@Composable
fun TimerPill(state: TimerPillState, modifier: Modifier = Modifier) {
    val background by animateColorAsState(
        targetValue = state.level.background(),
        animationSpec = tween(durationMillis = COLOR_ANIMATION_MILLIS),
        label = "pillBackground",
    )
    val content by animateColorAsState(
        targetValue = state.level.content(),
        animationSpec = tween(durationMillis = COLOR_ANIMATION_MILLIS),
        label = "pillContent",
    )
    if (state.collapsed) {
        Box(
            modifier = modifier
                .alpha(state.opacity)
                .size(if (state.size == PillSize.SMALL) 14.dp else 18.dp)
                .background(background, CircleShape),
        )
        return
    }
    val small = state.size == PillSize.SMALL
    Row(
        modifier = modifier
            .alpha(state.opacity)
            .background(background, RoundedCornerShape(50))
            .padding(
                horizontal = if (small) 10.dp else 14.dp,
                vertical = if (small) 4.dp else 6.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (small) 6.dp else 8.dp),
    ) {
        Text(
            text = state.appName,
            color = content,
            fontSize = if (small) 11.sp else 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
        Column(horizontalAlignment = Alignment.End) {
            // "12:41 · 86 swipes"
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = state.elapsedText,
                    color = content,
                    fontSize = if (small) 13.sp else 16.sp,
                    fontWeight = FontWeight.Bold,
                    // Monospaced digits so the pill doesn't jiggle every second.
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                )
                state.swipesText?.let {
                    Text(
                        text = stringResource(R.string.overlay_swipes_separator, it),
                        color = content,
                        fontSize = if (small) 11.sp else 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                    )
                }
            }
            state.remainingText?.let {
                Text(
                    text = it,
                    color = content,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = if (small) 10.sp else 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
            state.todayText?.let {
                Text(
                    text = it,
                    color = content.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = if (small) 9.sp else 10.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

private const val COLOR_ANIMATION_MILLIS = 600

@Preview
@Composable
private fun TimerPillPreview() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(8.dp)) {
        PillLevel.entries.forEach { level ->
            TimerPill(
                TimerPillState(
                    appName = "Instagram",
                    elapsedText = "12:41",
                    todayText = "Today 1h 5m",
                    level = level,
                    size = PillSize.MEDIUM,
                    opacity = 0.9f,
                    collapsed = false,
                ),
            )
        }
        TimerPill(
            TimerPillState("TikTok", "03:07", null, PillLevel.CALM, PillSize.SMALL, 1f, collapsed = false),
        )
        TimerPill(
            TimerPillState("TikTok", "03:07", null, PillLevel.DANGER, PillSize.MEDIUM, 1f, collapsed = true),
        )
    }
}
