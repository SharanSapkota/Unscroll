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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.unscroll.app.R
import com.unscroll.app.domain.fox.FoxMood
import com.unscroll.app.domain.overlay.PillLevel
import com.unscroll.app.domain.overlay.PillSize
import com.unscroll.app.ui.fox.FoxMascot
import com.unscroll.app.ui.fox.LocalFoxSettings
import com.unscroll.app.ui.theme.Dimens
import com.unscroll.app.ui.theme.Motion
import com.unscroll.app.ui.theme.PillColors
import com.unscroll.app.ui.theme.PillType

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

/** Fixed traffic-light colors from the theme: they must read the same over any app, light or dark. */
private fun PillLevel.background(): Color = when (this) {
    PillLevel.CALM -> PillColors.calm
    PillLevel.WARNING -> PillColors.warning
    PillLevel.DANGER -> PillColors.danger
}

/** The collapsed pill's fox follows the pill's own level. */
private fun PillLevel.foxMood(): FoxMood = when (this) {
    PillLevel.CALM -> FoxMood.HAPPY
    PillLevel.WARNING -> FoxMood.ALERT
    PillLevel.DANGER -> FoxMood.CONCERNED
}

private fun PillLevel.content(): Color = when (this) {
    PillLevel.CALM -> PillColors.onCalm
    PillLevel.WARNING -> PillColors.onWarning
    PillLevel.DANGER -> PillColors.onDanger
}

/**
 * The floating timer: a compact capsule with a soft shadow, the time bold, the app name and the
 * swipe count smaller ("Instagram 12:41 · 86 swipes"), or, collapsed, a tiny fox face (a small
 * dot with the fox off). Colors cross-fade between levels.
 */
@Composable
fun TimerPill(state: TimerPillState, modifier: Modifier = Modifier) {
    val background by animateColorAsState(
        targetValue = state.level.background(),
        animationSpec = tween(durationMillis = Motion.PILL_COLOR),
        label = "pillBackground",
    )
    val content by animateColorAsState(
        targetValue = state.level.content(),
        animationSpec = tween(durationMillis = Motion.PILL_COLOR),
        label = "pillContent",
    )
    val small = state.size == PillSize.SMALL
    // Room for the shadow inside the overlay window.
    Box(modifier = modifier.padding(Dimens.pillShadow)) {
        if (state.collapsed && LocalFoxSettings.current.showFox) {
            // Still (no idle animation): it sits over other apps and must not draw attention.
            FoxMascot(
                mood = state.level.foxMood(),
                modifier = Modifier
                    .alpha(state.opacity)
                    .size(if (small) Dimens.pillFoxSmall else Dimens.pillFoxMedium),
                showTail = false,
                animate = false,
                fur = background,
            )
            return@Box
        }
        if (state.collapsed) {
            Box(
                modifier = Modifier
                    .alpha(state.opacity)
                    .size(if (small) Dimens.pillDotSmall else Dimens.pillDotMedium)
                    .shadow(Dimens.pillShadow, CircleShape)
                    .background(background, CircleShape),
            )
            return@Box
        }
        Row(
            modifier = Modifier
                .alpha(state.opacity)
                .shadow(Dimens.pillShadow, CircleShape)
                .background(background, CircleShape)
                .padding(
                    horizontal = if (small) Dimens.pillPaddingHSmall else Dimens.pillPaddingHMedium,
                    vertical = if (small) Dimens.pillPaddingVSmall else Dimens.pillPaddingVMedium,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (small) Dimens.spaceXs + Dimens.spaceXxs else Dimens.spaceS),
        ) {
            Text(
                text = state.appName,
                color = content.copy(alpha = SECONDARY_ALPHA),
                fontSize = if (small) PillType.appNameSmall else PillType.appNameMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            Column(horizontalAlignment = Alignment.End) {
                // "12:41 · 86 swipes"
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = state.elapsedText,
                        color = content,
                        fontSize = if (small) PillType.timeSmall else PillType.timeMedium,
                        fontWeight = FontWeight.Bold,
                        // Tabular digits so the pill doesn't jiggle every second.
                        style = TextStyle(fontFeatureSettings = "tnum"),
                        maxLines = 1,
                    )
                    state.swipesText?.let {
                        Text(
                            text = stringResource(R.string.overlay_swipes_separator, it),
                            color = content.copy(alpha = SECONDARY_ALPHA),
                            fontSize = if (small) PillType.detailSmall else PillType.detailMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                        )
                    }
                }
                state.remainingText?.let {
                    Text(
                        text = it,
                        color = content,
                        fontSize = if (small) PillType.detailSmall else PillType.detailMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
                state.todayText?.let {
                    Text(
                        text = it,
                        color = content.copy(alpha = SECONDARY_ALPHA),
                        fontSize = if (small) PillType.detailSmall else PillType.detailMedium,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

private const val SECONDARY_ALPHA = 0.85f

@Preview
@Composable
private fun TimerPillPreview() {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceS)) {
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
                    swipesText = "86 swipes",
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
