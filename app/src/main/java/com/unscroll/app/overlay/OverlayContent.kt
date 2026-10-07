package com.unscroll.app.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unscroll.app.R
import com.unscroll.app.domain.overlay.OverlaySettings
import com.unscroll.app.domain.overlay.PillRules
import com.unscroll.app.ui.durationText
import kotlinx.coroutines.delay

/** Swipe info for the pill: the session count (for color, and text if shown) and swipes left near a limit. */
data class PillSwipes(
    val count: Int,
    val showCount: Boolean,
    /** Swipes left under the app's swipe limit, once within the last 20 %; else null. */
    val remaining: Int? = null,
)

/** What the overlay window shows. Produced by [OverlayTimerManager]. */
data class OverlayUiState(
    val appName: String,
    val sessionStart: Long,
    val settings: OverlaySettings,
    /** Today's total for the app (this session included) at [todayBaseTime], or null to hide. */
    val todayBaseMillis: Long?,
    /** When [todayBaseMillis] was measured; time since then is added live. */
    val todayBaseTime: Long,
    val collapsed: Boolean,
    /** A break reminder or limit warning to show under the pill, or null. */
    val message: PillMessage? = null,
)

/**
 * The overlay window's content. It ticks once per second, and only exists while the window is
 * attached, which is only while a tracked app is in the foreground.
 */
@Composable
fun OverlayContent(
    state: OverlayUiState,
    now: () -> Long,
    onTap: () -> Unit,
    onDrag: (dx: Float, dy: Float) -> Unit,
    onDragEnd: () -> Unit,
    onMessageAction: (PillAction) -> Unit = {},
    /** Swipes for the pill, or null when scroll counting is off. */
    swipes: PillSwipes? = null,
) {
    val currentTime by produceState(now(), state.sessionStart) {
        while (true) {
            value = now()
            // Wake up right after the next whole second so the display never skips a second.
            delay(1_000 - (value - state.sessionStart).mod(1_000L))
        }
    }
    val elapsed = currentTime - state.sessionStart
    val todayText = state.todayBaseMillis?.let { base ->
        stringResource(
            R.string.overlay_today_total,
            durationText(base + (currentTime - state.todayBaseTime).coerceAtLeast(0)),
        )
    }
    val swipesText = swipes?.takeIf { it.showCount }?.let {
        pluralStringResource(R.plurals.overlay_swipes, it.count, it.count)
    }
    val remainingText = swipes?.remaining?.let { pluralStringResource(R.plurals.overlay_swipes_left, it, it) }
    val level = PillRules.combinedLevel(
        timeLevel = PillRules.levelFor(elapsed, state.settings.thresholds),
        swipeLevel = swipes?.let { PillRules.levelForSwipes(it.count, state.settings.swipeThresholds) },
    )
    val pill = @Composable {
        TimerPill(
            state = TimerPillState(
                appName = state.appName,
                elapsedText = PillRules.formatTime(elapsed),
                todayText = todayText,
                level = level,
                size = state.settings.size,
                opacity = state.settings.opacity,
                // A message always expands a collapsed pill.
                collapsed = state.collapsed && state.message == null,
                swipesText = swipesText,
                remainingText = remainingText,
            ),
            modifier = Modifier
                .pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = onDragEnd,
                        onDragCancel = onDragEnd,
                        onDrag = { change, dragAmount ->
                            change.consume()
                            onDrag(dragAmount.x, dragAmount.y)
                        },
                    )
                },
        )
    }
    val message = state.message
    if (message == null) {
        pill()
    } else {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            pill()
            PillMessageCard(message, state.appName, onMessageAction)
        }
    }
}

@Composable
private fun PillMessageCard(message: PillMessage, appName: String, onAction: (PillAction) -> Unit) {
    Column(
        modifier = Modifier
            .padding(top = 6.dp)
            .widthIn(max = 280.dp)
            .background(Color(0xF2202124), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = when (message.kind) {
                PillMessageKind.BREAK -> stringResource(R.string.overlay_break_message, message.minutes, appName)
                PillMessageKind.LIMIT_WARNING -> stringResource(R.string.overlay_limit_warning, appName)
                PillMessageKind.LIMIT_REACHED -> stringResource(R.string.overlay_limit_reached, appName)
            },
            color = Color.White,
            fontSize = 14.sp,
        )
        if (message.kind == PillMessageKind.BREAK) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onAction(PillAction.KEEP_GOING) }) {
                    Text(stringResource(R.string.overlay_keep_going), color = Color(0xFFA5D6A7))
                }
                TextButton(onClick = { onAction(PillAction.LEAVE) }) {
                    Text(stringResource(R.string.overlay_leave), color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            TextButton(onClick = { onAction(PillAction.DISMISS) }) {
                Text(stringResource(R.string.overlay_ok), color = Color(0xFFA5D6A7))
            }
        }
    }
}
