package com.unscroll.app.ui.section

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
import com.unscroll.app.domain.section.SectionBlockingRules
import com.unscroll.app.ui.components.UnscrollCard
import com.unscroll.app.ui.theme.Dimens
import com.unscroll.app.ui.theme.UnscrollTheme
import com.unscroll.app.util.SystemSettings
import com.unscroll.app.util.openSettings

/** Settings value for the "Section blocking" row. */
internal fun sectionValueRes(status: ScrollCountingStatus, turnedOff: Boolean): Int = when (status) {
    ScrollCountingStatus.OFF -> R.string.value_off
    ScrollCountingStatus.NEEDS_CONSENT -> R.string.scroll_value_review
    ScrollCountingStatus.NEEDS_ENABLING -> R.string.scroll_value_setup
    ScrollCountingStatus.ACTIVE -> if (turnedOff) R.string.value_off else R.string.value_on
    ScrollCountingStatus.NEEDS_REENABLE -> R.string.scroll_value_reenable
}

/**
 * Settings › Section blocking (in a sheet): one status line and the actions that fit it,
 * including the global kill switch ("Turn off section blocking") and withdrawing consent.
 */
@Composable
fun SectionBlockingPanel(
    onSetUp: () -> Unit,
    onRestrictedHelp: () -> Unit,
    viewModel: SectionBlockingViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val status by viewModel.status.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val openAccessibility = { context.openSettings(SystemSettings.accessibility()) }

    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceM)) {
        Text(
            text = stringResource(
                when {
                    status == ScrollCountingStatus.ACTIVE && settings.turnedOff -> R.string.section_status_turned_off
                    status == ScrollCountingStatus.ACTIVE && settings.blockedApps.isEmpty() -> R.string.section_status_no_apps
                    else -> when (status) {
                        ScrollCountingStatus.OFF -> R.string.section_status_off
                        ScrollCountingStatus.NEEDS_CONSENT -> R.string.section_status_needs_consent
                        ScrollCountingStatus.NEEDS_ENABLING -> R.string.section_status_needs_enabling
                        ScrollCountingStatus.ACTIVE -> R.string.section_status_active
                        ScrollCountingStatus.NEEDS_REENABLE -> R.string.section_status_needs_reenable
                    }
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
                    TextButton(onClick = viewModel::withdraw) { Text(stringResource(R.string.section_withdraw)) }
                }
            }
            ScrollCountingStatus.ACTIVE -> {
                // The global kill switch: everything stops at once; consent and the service stay.
                if (settings.turnedOff) {
                    Button(onClick = { viewModel.setTurnedOff(false) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.section_turn_on))
                    }
                } else {
                    OutlinedButton(onClick = { viewModel.setTurnedOff(true) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.section_turn_off))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceS)) {
                    TextButton(onClick = openAccessibility) { Text(stringResource(R.string.scroll_open_accessibility)) }
                    TextButton(onClick = viewModel::withdraw) { Text(stringResource(R.string.section_withdraw)) }
                }
            }
        }
        Text(
            text = stringResource(R.string.section_panel_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Home banner when the system switched the section-blocking service off after it had been working. */
@Composable
fun SectionBlockingBanner(
    modifier: Modifier = Modifier,
    viewModel: SectionBlockingViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val status by viewModel.status.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val isPlus by viewModel.isPlus.collectAsStateWithLifecycle()
    // Only worth a banner while the user still wants it: on, Plus, and at least one app chosen.
    val wanted = SectionBlockingRules.isActive(settings, isPlus) && settings.blockedApps.isNotEmpty()
    if (status != ScrollCountingStatus.NEEDS_REENABLE || !wanted) return
    UnscrollCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Dimens.spaceXl),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceS),
        ) {
            Text(
                text = stringResource(R.string.section_banner_title),
                style = MaterialTheme.typography.titleMedium,
                color = UnscrollTheme.status.danger,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.spaceS)) {
                Button(onClick = { context.openSettings(SystemSettings.accessibility()) }) {
                    Text(stringResource(R.string.scroll_banner_action))
                }
                TextButton(onClick = { viewModel.setTurnedOff(true) }) { Text(stringResource(R.string.section_turn_off)) }
            }
        }
    }
}
