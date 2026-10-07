package com.unscroll.app.ui.scroll

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.unscroll.app.R
import com.unscroll.app.domain.scroll.ScrollCountingStatus
import com.unscroll.app.ui.components.UnscrollCard
import com.unscroll.app.ui.theme.Dimens
import com.unscroll.app.ui.theme.UnscrollTheme
import com.unscroll.app.util.SystemSettings
import com.unscroll.app.util.openSettings

/** Settings › Scroll counting (in a sheet): one status line and the actions that fit it. */
@Composable
fun ScrollCountingPanel(
    onSetUp: () -> Unit,
    onRestrictedHelp: () -> Unit,
    viewModel: ScrollCountingViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val status by viewModel.status.collectAsStateWithLifecycle()
    val openAccessibility = { context.openSettings(SystemSettings.accessibility()) }

    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceM)) {
        Text(
            text = stringResource(
                when (status) {
                    ScrollCountingStatus.OFF -> R.string.scroll_status_off
                    ScrollCountingStatus.NEEDS_CONSENT -> R.string.scroll_status_needs_consent
                    ScrollCountingStatus.NEEDS_ENABLING -> R.string.scroll_status_needs_enabling
                    ScrollCountingStatus.ACTIVE -> R.string.scroll_status_active
                    ScrollCountingStatus.NEEDS_REENABLE -> R.string.scroll_status_needs_reenable
                },
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = if (status == ScrollCountingStatus.NEEDS_REENABLE) {
                UnscrollTheme.status.danger
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        when (status) {
            ScrollCountingStatus.OFF -> Button(onClick = onSetUp, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.scroll_set_up))
            }
            ScrollCountingStatus.NEEDS_CONSENT -> {
                Button(onClick = onSetUp, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.scroll_review))
                }
                OutlinedButton(onClick = openAccessibility, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.scroll_open_accessibility))
                }
            }
            ScrollCountingStatus.NEEDS_ENABLING, ScrollCountingStatus.NEEDS_REENABLE -> {
                Button(onClick = openAccessibility, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.scroll_open_accessibility))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceS)) {
                    TextButton(onClick = onRestrictedHelp) { Text(stringResource(R.string.scroll_restricted_link)) }
                    TextButton(onClick = viewModel::turnOff) { Text(stringResource(R.string.scroll_turn_off)) }
                }
            }
            ScrollCountingStatus.ACTIVE -> {
                OutlinedButton(onClick = viewModel::turnOff, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.scroll_turn_off))
                }
                TextButton(onClick = openAccessibility) { Text(stringResource(R.string.scroll_open_accessibility)) }
            }
        }
    }
}

/** Home banner when the system switched the service off after it had been working. */
@Composable
fun ScrollCountingBanner(
    modifier: Modifier = Modifier,
    viewModel: ScrollCountingViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val status by viewModel.status.collectAsStateWithLifecycle()
    if (status != ScrollCountingStatus.NEEDS_REENABLE) return
    UnscrollCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Dimens.spaceXl),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceS),
        ) {
            Text(
                text = stringResource(R.string.scroll_banner_title),
                style = MaterialTheme.typography.titleMedium,
                color = UnscrollTheme.status.danger,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.spaceS)) {
                Button(onClick = { context.openSettings(SystemSettings.accessibility()) }) {
                    Text(stringResource(R.string.scroll_banner_action))
                }
                TextButton(onClick = viewModel::turnOff) { Text(stringResource(R.string.scroll_turn_off)) }
            }
        }
    }
}
