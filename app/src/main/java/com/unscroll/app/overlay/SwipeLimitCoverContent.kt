package com.unscroll.app.overlay

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.unscroll.app.R
import com.unscroll.app.domain.blocking.SwipeLimitRules
import com.unscroll.app.domain.tracking.TrackedApps
import com.unscroll.app.ui.components.StopScreen
import com.unscroll.app.ui.durationText
import com.unscroll.app.ui.theme.UnscrollTheme

/**
 * "Limit reached", the swipe count big, time today small, and "Go home". No countdown, no
 * animation. "I need access" (if the user allowed it) is one tap: +20 swipes.
 */
@Composable
internal fun SwipeLimitCoverContent(
    state: SwipeCoverState,
    onGoHome: () -> Unit,
    onAccessGranted: () -> Unit,
) {
    StopScreen(
        packageName = state.packageName,
        headline = stringResource(R.string.swipe_cover_title),
        number = state.swipes.toString(),
        numberLabel = pluralStringResource(
            R.plurals.swipe_cover_label,
            state.swipes,
            durationText(state.todayMillis),
        ),
        goHomeLabel = stringResource(R.string.action_go_home),
        onGoHome = onGoHome,
    ) {
        if (state.accessAllowed) {
            TextButton(onClick = onAccessGranted) {
                Text(stringResource(R.string.swipe_cover_need_access, SwipeLimitRules.EXTENSION_SWIPES))
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun SwipeLimitCoverPreview() {
    UnscrollTheme(darkTheme = true) {
        SwipeLimitCoverContent(
            state = SwipeCoverState(TrackedApps.INSTAGRAM, "Instagram", 100, 42 * 60_000L, accessAllowed = true),
            onGoHome = {},
            onAccessGranted = {},
        )
    }
}
