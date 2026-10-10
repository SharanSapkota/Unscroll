package com.unscroll.app.ui.block

import android.text.format.DateFormat
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.unscroll.app.R
import com.unscroll.app.domain.blocking.AccessExtension
import com.unscroll.app.domain.blocking.BlockReason
import com.unscroll.app.domain.tracking.DefaultTrackedApps
import com.unscroll.app.ui.components.StopScreen
import com.unscroll.app.ui.durationText
import com.unscroll.app.ui.theme.UnscrollTheme
import java.util.Date

/** "Limit reached", today's time in the app, and one big "Go home". */
@Composable
fun BlockScreen(
    state: BlockUiState,
    onGoHome: () -> Unit,
    onRequestAccess: () -> Unit,
) {
    val context = LocalContext.current
    val untilText = remember(state.until) {
        state.until?.let { DateFormat.getTimeFormat(context).format(Date(it)) }
    }
    StopScreen(
        packageName = state.packageName,
        headline = when (state.reason) {
            // "Block entire app": until a time, or until the user turns it off.
            BlockReason.BLOCKED_ENTIRE_APP -> untilText?.let { stringResource(R.string.block_headline_until, it) }
                ?: stringResource(R.string.block_headline_blocked)
            BlockReason.DAILY_LIMIT_REACHED -> stringResource(R.string.block_headline_limit)
            BlockReason.SWIPE_LIMIT_REACHED -> stringResource(R.string.block_headline_swipes)
            BlockReason.INSIDE_SCHEDULE -> untilText?.let { stringResource(R.string.block_headline_until, it) }
                ?: stringResource(R.string.block_headline_blocked)
        },
        number = durationText(state.usedTodayMillis),
        numberLabel = stringResource(R.string.block_today),
        goHomeLabel = stringResource(R.string.action_go_home),
        onGoHome = onGoHome,
    ) {
        if (state.canRequestAccess) {
            // One tap: the extension is granted, logged, and the app opens again.
            TextButton(onClick = onRequestAccess, enabled = !state.accessGranted) {
                Text(stringResource(R.string.block_need_access, EXTENSION_MINUTES))
            }
        }
    }
}

private val EXTENSION_MINUTES = (AccessExtension.MILLIS / 60_000L).toInt()

@PreviewLightDark
@Composable
private fun BlockScreenPreview() {
    UnscrollTheme(darkTheme = true) {
        BlockScreen(
            state = BlockUiState(
                packageName = DefaultTrackedApps.INSTAGRAM,
                reason = BlockReason.DAILY_LIMIT_REACHED,
                until = null,
                usedTodayMillis = 47 * 60_000L,
            ),
            onGoHome = {},
            onRequestAccess = {},
        )
    }
}
