package com.unscroll.app.overlay

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import com.unscroll.app.R
import com.unscroll.app.domain.overlay.OverlaySettings
import com.unscroll.app.domain.overlay.PillRules
import com.unscroll.app.ui.durationText
import kotlinx.coroutines.delay

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
    TimerPill(
        state = TimerPillState(
            appName = state.appName,
            elapsedText = PillRules.formatTime(elapsed),
            todayText = todayText,
            level = PillRules.levelFor(elapsed, state.settings.thresholds),
            size = state.settings.size,
            opacity = state.settings.opacity,
            collapsed = state.collapsed,
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
