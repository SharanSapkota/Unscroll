package com.unscroll.app.overlay

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.unscroll.app.R
import com.unscroll.app.domain.blocking.AccessExtension
import com.unscroll.app.domain.blocking.SwipeLimitRules
import com.unscroll.app.domain.overlay.CoverReason
import com.unscroll.app.domain.tracking.DefaultTrackedApps
import com.unscroll.app.ui.components.StopScreen
import com.unscroll.app.ui.durationText
import com.unscroll.app.ui.theme.UnscrollTheme

/**
 * The cover's page: the reason ("Time limit reached", "Swipe limit reached"), the key number big,
 * "Go home", and for Instagram/TikTok "Take me back to chat" first. "I need access" stays one tap.
 */
@Composable
internal fun LimitCoverContent(
    state: LimitCoverState,
    onGoHome: () -> Unit,
    onBackToChat: () -> Unit,
    onAccess: () -> Unit,
) {
    val headline = stringResource(
        when (state.reason) {
            CoverReason.TIME_LIMIT -> R.string.cover_time_limit
            CoverReason.SWIPE_LIMIT -> R.string.block_headline_swipes
            CoverReason.SCHEDULE -> R.string.cover_schedule
        },
    )
    val timeText = durationText(state.todayMillis)
    val (number, label) = when (state.reason) {
        CoverReason.SWIPE_LIMIT -> state.swipes.toString() to pluralStringResource(R.plurals.swipe_cover_label, state.swipes, timeText)
        else -> timeText to stringResource(
            when {
                !state.reelsApp -> R.string.cover_today_in_app
                state.forYou -> R.string.cover_today_in_for_you
                else -> R.string.cover_today_in_reels
            },
        )
    }
    StopScreen(
        packageName = state.packageName,
        headline = headline,
        number = number,
        numberLabel = label,
        goHomeLabel = stringResource(R.string.action_go_home),
        onGoHome = onGoHome,
    ) {
        if (state.reelsApp) {
            OutlinedButton(onClick = onBackToChat, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.section_cover_back_chat))
            }
        }
        if (state.accessAllowed) {
            TextButton(onClick = onAccess) {
                Text(
                    if (state.reason == CoverReason.SWIPE_LIMIT) {
                        stringResource(R.string.swipe_cover_need_access, SwipeLimitRules.EXTENSION_SWIPES)
                    } else {
                        stringResource(R.string.block_need_access, (AccessExtension.MILLIS / 60_000L).toInt())
                    },
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun LimitCoverPreview() {
    UnscrollTheme(darkTheme = true) {
        LimitCoverContent(
            state = LimitCoverState(
                packageName = DefaultTrackedApps.INSTAGRAM,
                appName = "Instagram",
                reason = CoverReason.TIME_LIMIT,
                swipes = 100,
                todayMillis = 30 * 60_000L,
                accessAllowed = true,
                reelsApp = true,
            ),
            onGoHome = {},
            onBackToChat = {},
            onAccess = {},
        )
    }
}
