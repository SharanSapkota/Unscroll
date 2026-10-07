package com.unscroll.app.ui.block

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.unscroll.app.R
import com.unscroll.app.domain.blocking.BlockReason
import com.unscroll.app.domain.blocking.BlockingSettings
import com.unscroll.app.domain.blocking.FrictionMode
import com.unscroll.app.ui.durationText
import com.unscroll.app.ui.theme.UnscrollTheme
import com.unscroll.app.util.appLabel
import java.util.Date

@Composable
fun BlockScreen(
    state: BlockUiState,
    onGoHome: () -> Unit,
    onRequestAccess: () -> Unit,
    onCancelAccess: () -> Unit,
    onFrictionPassed: () -> Unit,
) {
    val context = LocalContext.current
    val appName = remember(state.packageName) { context.packageManager.appLabel(state.packageName) }
    val untilText = remember(state.until) {
        state.until?.let { DateFormat.getTimeFormat(context).format(Date(it)) }
    }
    val messages = stringArrayResource(R.array.block_messages)

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.block_title, appName),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = when (state.reason) {
                    BlockReason.BLOCKED_ALWAYS -> stringResource(R.string.block_reason_always)
                    BlockReason.DAILY_LIMIT_REACHED -> stringResource(R.string.block_reason_limit)
                    BlockReason.SWIPE_LIMIT_REACHED -> stringResource(R.string.block_reason_swipes)
                    BlockReason.INSIDE_SCHEDULE -> if (untilText != null) {
                        stringResource(R.string.block_reason_schedule_until, untilText)
                    } else {
                        stringResource(R.string.block_reason_schedule)
                    }
                },
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.block_used_today, durationText(state.usedTodayMillis)),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = messages[state.messageIndex.mod(messages.size)],
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = onGoHome, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.block_go_home))
            }
            if (state.canRequestAccess) {
                AccessSection(state, onRequestAccess, onCancelAccess, onFrictionPassed)
            }
        }
    }
}

@Composable
private fun AccessSection(
    state: BlockUiState,
    onRequestAccess: () -> Unit,
    onCancelAccess: () -> Unit,
    onFrictionPassed: () -> Unit,
) {
    when (val request = state.accessRequest) {
        AccessRequest.NotStarted -> TextButton(onClick = onRequestAccess) {
            Text(
                text = stringResource(R.string.block_need_access),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        AccessRequest.TypingPhrase -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            PhraseInput(onUnlocked = onFrictionPassed, modifier = Modifier.fillMaxWidth())
            TextButton(onClick = onCancelAccess) { Text(stringResource(R.string.action_cancel)) }
        }
        is AccessRequest.Waiting -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.block_wait_countdown, request.secondsLeft),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            TextButton(onClick = onCancelAccess) { Text(stringResource(R.string.action_cancel)) }
        }
        AccessRequest.WaitDone -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            TextButton(onClick = onFrictionPassed) {
                Text(stringResource(R.string.block_open_for_minutes, EXTENSION_MINUTES))
            }
            TextButton(onClick = onCancelAccess) { Text(stringResource(R.string.action_cancel)) }
        }
        AccessRequest.Granted -> Unit
    }
}

private val EXTENSION_MINUTES = (BlockingSettings.EXTENSION_MILLIS / 60_000L).toInt()

@Preview(showBackground = true)
@Composable
private fun BlockScreenPreview() {
    UnscrollTheme {
        BlockScreen(
            state = BlockUiState(
                packageName = "com.instagram.android",
                reason = BlockReason.DAILY_LIMIT_REACHED,
                until = null,
                usedTodayMillis = 47 * 60_000L,
                frictionMode = FrictionMode.WAIT,
                accessRequest = AccessRequest.Waiting(23),
            ),
            onGoHome = {},
            onRequestAccess = {},
            onCancelAccess = {},
            onFrictionPassed = {},
        )
    }
}
