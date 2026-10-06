package com.unscroll.app.ui.dashboard

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.unscroll.app.R
import com.unscroll.app.domain.session.ActiveSession
import com.unscroll.app.domain.session.Session
import com.unscroll.app.ui.theme.UnscrollTheme
import com.unscroll.app.util.appLabel
import com.unscroll.app.util.formatElapsed
import kotlinx.coroutines.delay

/** Temporary M2 debug dashboard: current session and the last 20 sessions. Replaced in M3. */
@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    DashboardContent(uiState = uiState, modifier = modifier)
}

@Composable
private fun DashboardContent(uiState: DashboardUiState, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.dashboard_debug_note),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!uiState.trackingEnabled) {
            item {
                Text(
                    text = stringResource(R.string.dashboard_tracking_off),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        item { CurrentSessionCard(uiState.currentSession) }
        item {
            Text(
                text = stringResource(R.string.dashboard_recent_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (uiState.recentSessions.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.dashboard_no_sessions),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        items(uiState.recentSessions, key = { it.id }) { session ->
            SessionRow(session)
            HorizontalDivider()
        }
    }
}

@Composable
private fun CurrentSessionCard(session: ActiveSession?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.dashboard_current_title),
                style = MaterialTheme.typography.titleMedium,
            )
            if (session == null) {
                Text(
                    text = stringResource(R.string.dashboard_no_current_session),
                    style = MaterialTheme.typography.bodyLarge,
                )
            } else {
                // Ticks only while this card is on screen.
                val now by produceState(System.currentTimeMillis(), session.id) {
                    while (true) {
                        value = System.currentTimeMillis()
                        delay(1_000)
                    }
                }
                Text(
                    text = rememberAppLabel(session.packageName),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = formatElapsed(now - session.startTime),
                    style = MaterialTheme.typography.displaySmall,
                )
            }
        }
    }
}

@Composable
private fun SessionRow(session: Session) {
    val context = LocalContext.current
    val startedAt = remember(session.startTime) {
        DateUtils.formatDateTime(
            context,
            session.startTime,
            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH,
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = rememberAppLabel(session.packageName),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = startedAt,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = session.endTime?.let { formatElapsed(it - session.startTime) }
                ?: stringResource(R.string.dashboard_session_ongoing),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun rememberAppLabel(packageName: String): String {
    val packageManager = LocalContext.current.packageManager
    return remember(packageName) { packageManager.appLabel(packageName) }
}

@Preview(showBackground = true)
@Composable
private fun DashboardPreview() {
    UnscrollTheme {
        DashboardContent(
            uiState = DashboardUiState(
                trackingEnabled = true,
                currentSession = null,
                recentSessions = listOf(
                    Session(2, "com.instagram.android", 1_700_000_000_000, null, 0),
                    Session(1, "com.zhiliaoapp.musically", 1_699_999_000_000, 1_699_999_600_000, 0),
                ),
            ),
        )
    }
}
