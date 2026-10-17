package com.unscroll.app.overlay

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
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
    /** Today's total in the app, already formatted ("47:12"). */
    val elapsedText: String,
    /** This visit's time ("3:05"), or null to hide it ("Show session time", off by default). */
    val visitText: String?,
    val level: PillLevel,
    val size: PillSize,
    val opacity: Float,
    val collapsed: Boolean,
    /**
     * Today's swipes in the app, or null to hide the swipe icon and number: while scroll counting
     * isn't running (never a "0" that only means "not counting") or the count is switched off.
     */
    val swipes: Int? = null,
    /** Swipes left near a swipe limit; shown instead of [swipes] ("12 left"). */
    val swipesLeft: Int? = null,
)

/** Fixed traffic-light colors from the theme: they must read the same over any app, light or dark. */
private fun PillLevel.background(): Color = when (this) {
    PillLevel.CALM -> PillColors.calm
    PillLevel.WARNING -> PillColors.warning
    PillLevel.DANGER -> PillColors.danger
}

/** The level as a tint on the dark capsule (border, swipe icon, and the time once it isn't calm). */
private fun PillLevel.tint(): Color = when (this) {
    PillLevel.CALM -> PillColors.calmTint
    PillLevel.WARNING -> PillColors.warningTint
    PillLevel.DANGER -> PillColors.dangerTint
}

/** The pill's fox follows the pill's own level. */
private fun PillLevel.foxMood(): FoxMood = when (this) {
    PillLevel.CALM -> FoxMood.HAPPY
    PillLevel.WARNING -> FoxMood.ALERT
    PillLevel.DANGER -> FoxMood.CONCERNED
}

/**
 * The floating timer: a tiny dark capsule (28 dp high when small) with the fox face, today's time
 * in bold tabular digits and, while scroll counting runs, a swipe icon and today's swipes
 * ("🦊 12:41 ☝ 86"). The level (green, yellow, red) tints the border, the swipe icon and, once it
 * isn't calm, the time. Collapsed, it is a level-colored dot with the fox face inside (a plain dot
 * with the fox off). Colors cross-fade between levels.
 */
@Composable
fun TimerPill(state: TimerPillState, modifier: Modifier = Modifier) {
    val background by animateColorAsState(
        targetValue = state.level.background(),
        animationSpec = tween(durationMillis = Motion.PILL_COLOR),
        label = "pillBackground",
    )
    val tint by animateColorAsState(
        targetValue = state.level.tint(),
        animationSpec = tween(durationMillis = Motion.PILL_COLOR),
        label = "pillTint",
    )
    val small = state.size == PillSize.SMALL
    val showFox = LocalFoxSettings.current.showFox
    // Room for the shadow inside the overlay window.
    Box(modifier = modifier.padding(Dimens.pillShadow)) {
        if (state.collapsed) {
            CollapsedPill(state, background, small, showFox)
            return@Box
        }
        val timeColor = if (state.level == PillLevel.CALM) PillColors.onSurface else tint
        val detailSize = if (small) PillType.detailSmall else PillType.detailMedium
        Row(
            modifier = Modifier
                .alpha(state.opacity)
                .height(if (small) Dimens.pillHeightSmall else Dimens.pillHeightMedium)
                .shadow(Dimens.pillShadow, CircleShape)
                .background(PillColors.surface, CircleShape)
                .border(Dimens.pillBorder, tint, CircleShape)
                .padding(horizontal = if (small) Dimens.pillPaddingHSmall else Dimens.pillPaddingHMedium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.pillGap),
        ) {
            if (showFox) {
                // Still (no idle animation): it sits over other apps and must not draw attention.
                FoxMascot(
                    mood = state.level.foxMood(),
                    modifier = Modifier.size(if (small) Dimens.pillIconSmall else Dimens.pillIconMedium),
                    showTail = false,
                    animate = false,
                )
            } else {
                Box(Modifier.size(Dimens.pillLevelDot).background(tint, CircleShape))
            }
            Text(
                text = state.elapsedText,
                color = timeColor,
                fontSize = if (small) PillType.timeSmall else PillType.timeMedium,
                fontWeight = FontWeight.Bold,
                // Tabular digits so the pill doesn't jiggle every second.
                style = TextStyle(fontFeatureSettings = "tnum"),
                maxLines = 1,
            )
            state.visitText?.let {
                Text(
                    text = stringResource(R.string.overlay_this_visit_short, it),
                    color = PillColors.onSurface.copy(alpha = SECONDARY_ALPHA),
                    fontSize = detailSize,
                    style = TextStyle(fontFeatureSettings = "tnum"),
                    maxLines = 1,
                )
            }
            val left = state.swipesLeft
            val swipes = state.swipes
            if (left != null || swipes != null) {
                Icon(
                    painter = painterResource(R.drawable.ic_swipe),
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(if (small) Dimens.pillSwipeIconSmall else Dimens.pillSwipeIconMedium),
                )
                Text(
                    text = if (left != null) {
                        pluralStringResource(R.plurals.overlay_swipes_left_short, left, left)
                    } else {
                        swipes.toString()
                    },
                    color = if (left != null) tint else PillColors.onSurface.copy(alpha = SECONDARY_ALPHA),
                    fontSize = detailSize,
                    fontWeight = if (left != null) FontWeight.Bold else FontWeight.Medium,
                    style = TextStyle(fontFeatureSettings = "tnum"),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun CollapsedPill(state: TimerPillState, background: Color, small: Boolean, showFox: Boolean) {
    if (showFox) {
        // The dot keeps its level color; the fox face sits inside it.
        Box(
            modifier = Modifier
                .alpha(state.opacity)
                .size(if (small) Dimens.pillFoxSmall else Dimens.pillFoxMedium)
                .shadow(Dimens.pillShadow, CircleShape)
                .background(background, CircleShape)
                .padding(Dimens.pillFoxInset),
        ) {
            FoxMascot(
                mood = state.level.foxMood(),
                modifier = Modifier.fillMaxSize(),
                showTail = false,
                animate = false,
            )
        }
    } else {
        Box(
            modifier = Modifier
                .alpha(state.opacity)
                .size(if (small) Dimens.pillDotSmall else Dimens.pillDotMedium)
                .shadow(Dimens.pillShadow, CircleShape)
                .background(background, CircleShape),
        )
    }
}

private const val SECONDARY_ALPHA = 0.8f

@Preview
@Composable
private fun TimerPillPreview() {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceS)) {
        PillLevel.entries.forEach { level ->
            TimerPill(
                TimerPillState(
                    elapsedText = "12:41",
                    visitText = null,
                    level = level,
                    size = PillSize.SMALL,
                    opacity = 0.9f,
                    collapsed = false,
                    swipes = 86,
                ),
            )
        }
        // Scroll counting off: the fox and the time only.
        TimerPill(TimerPillState("1:03:07", null, PillLevel.CALM, PillSize.SMALL, 1f, collapsed = false))
        TimerPill(
            TimerPillState("47:12", "3:05", PillLevel.WARNING, PillSize.MEDIUM, 1f, collapsed = false, swipesLeft = 12),
        )
        TimerPill(TimerPillState("03:07", null, PillLevel.DANGER, PillSize.SMALL, 1f, collapsed = true))
    }
}
