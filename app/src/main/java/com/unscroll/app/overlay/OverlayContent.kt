package com.unscroll.app.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.unscroll.app.R
import com.unscroll.app.domain.overlay.OverlaySettings
import com.unscroll.app.domain.overlay.PillRules
import com.unscroll.app.domain.overlay.PillToday
import com.unscroll.app.domain.overlay.PillTodayBase
import com.unscroll.app.ui.theme.Dimens
import com.unscroll.app.ui.theme.PillColors
import com.unscroll.app.ui.theme.PillType
import java.time.ZoneId
import kotlinx.coroutines.delay

private const val SECOND = 1_000L

/**
 * Swipe info for the pill: the open session's count (today's earlier swipes are added from the
 * base) and swipes left near a swipe limit.
 */
data class PillSwipes(
    val sessionCount: Int,
    val showCount: Boolean,
    /** Swipes left under the app's swipe limit, once within the last 20 %; else null. */
    val remaining: Int? = null,
)

/** What the overlay window shows. Produced by [OverlayTimerManager]. */
data class OverlayUiState(
    val appName: String,
    /** Today's earlier time and swipes in the app; the open session is added live. */
    val today: PillTodayBase,
    /** The app's daily limit, for the colors (share of it); null without one. */
    val dailyLimitMillis: Long?,
    val settings: OverlaySettings,
    val collapsed: Boolean,
    /** A break reminder or limit warning to show under the pill, or null. */
    val message: PillMessage? = null,
)

/**
 * The overlay window's content: today's total for the app, ticking once per second from the
 * clock (no database reads). It only exists while the window is attached, which is only while a
 * tracked app is in the foreground.
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
    val currentTime by produceState(now(), state.today) {
        while (true) {
            value = now()
            // Wake up right after the total's next whole second, so the display never skips one.
            val total = PillToday.totalMillis(state.today, value, ZoneId.systemDefault())
            delay(SECOND - total.mod(SECOND))
        }
    }
    val zone = ZoneId.systemDefault()
    val todayMillis = PillToday.totalMillis(state.today, currentTime, zone)
    val visitText = if (state.settings.showSessionTime) {
        stringResource(R.string.overlay_this_visit, PillRules.formatTime(PillToday.sessionMillis(state.today, currentTime)))
    } else {
        null
    }
    val todaySwipes = swipes?.let { PillToday.swipes(state.today, it.sessionCount, currentTime, zone) }
    val swipesText = swipes?.takeIf { it.showCount }?.let {
        pluralStringResource(R.plurals.overlay_swipes, todaySwipes ?: 0, todaySwipes ?: 0)
    }
    val remainingText = swipes?.remaining?.let { pluralStringResource(R.plurals.overlay_swipes_left, it, it) }
    val level = PillRules.combinedLevel(
        timeLevel = PillRules.levelForToday(todayMillis, state.dailyLimitMillis, state.settings.thresholds),
        swipeLevel = todaySwipes?.let { PillRules.levelForSwipes(it, state.settings.swipeThresholds) },
    )
    val pill = @Composable {
        TimerPill(
            state = TimerPillState(
                appName = state.appName,
                elapsedText = PillRules.formatTime(todayMillis),
                visitText = visitText,
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
            .padding(top = Dimens.spaceXs)
            .widthIn(max = Dimens.pillMessageMaxWidth)
            .shadow(Dimens.pillShadow, MaterialTheme.shapes.medium)
            .background(PillColors.messageBackground, MaterialTheme.shapes.medium)
            .padding(horizontal = Dimens.spaceL, vertical = Dimens.spaceM),
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs),
    ) {
        Text(
            text = when (message.kind) {
                PillMessageKind.BREAK -> stringResource(R.string.overlay_break_message, message.minutes, appName)
                PillMessageKind.LIMIT_WARNING -> stringResource(R.string.overlay_limit_warning, appName)
                PillMessageKind.LIMIT_REACHED -> stringResource(R.string.overlay_limit_reached, appName)
            },
            color = PillColors.onMessage,
            fontSize = PillType.message,
        )
        if (message.kind == PillMessageKind.BREAK) {
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceS)) {
                TextButton(onClick = { onAction(PillAction.KEEP_GOING) }) {
                    Text(stringResource(R.string.overlay_keep_going), color = PillColors.messageAccent)
                }
                TextButton(onClick = { onAction(PillAction.LEAVE) }) {
                    Text(stringResource(R.string.overlay_leave), color = PillColors.onMessage, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            TextButton(onClick = { onAction(PillAction.DISMISS) }) {
                Text(stringResource(R.string.overlay_ok), color = PillColors.messageAccent)
            }
        }
    }
}
