package com.unscroll.app.ui.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.unscroll.app.R
import com.unscroll.app.ui.components.RowDivider
import com.unscroll.app.ui.components.SectionHeader
import com.unscroll.app.ui.components.SettingRow
import com.unscroll.app.ui.components.SettingSwitchRow
import com.unscroll.app.ui.components.SettingsGroup
import com.unscroll.app.ui.theme.Dimens
import com.unscroll.app.ui.theme.UnscrollTheme

internal class TrackingActions(
    val onTracked: (Boolean) -> Unit = {},
    val onCountSwipes: (Boolean) -> Unit = {},
    val onRemove: () -> Unit = {},
)

/**
 * The app's place in the tracked list: "Track this app" (pause), "Count swipes" (swipe events
 * differ between apps) and "Remove from Unscroll" (with a confirmation; nothing is deleted).
 */
@Composable
internal fun TrackingSection(tracking: TrackingRowState, actions: TrackingActions) {
    var confirmRemove by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceS)) {
        SectionHeader(
            title = stringResource(R.string.detail_tracking),
            info = stringResource(R.string.detail_tracking_info),
        )
        SettingsGroup {
            SettingSwitchRow(
                title = stringResource(R.string.detail_track_app),
                icon = R.drawable.ic_timer,
                checked = tracking.tracked,
                onCheckedChange = actions.onTracked,
                subtitle = if (tracking.installed) null else stringResource(R.string.apps_not_installed),
                subtitleColor = UnscrollTheme.status.warn,
            )
            SettingSwitchRow(
                title = stringResource(R.string.detail_count_swipes),
                icon = R.drawable.ic_swipe,
                checked = tracking.countSwipes,
                onCheckedChange = actions.onCountSwipes,
            )
            RowDivider()
            SettingRow(
                title = stringResource(R.string.detail_remove),
                icon = R.drawable.ic_delete,
                titleColor = UnscrollTheme.status.danger,
                showChevron = false,
                onClick = { confirmRemove = true },
            )
        }
    }
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(stringResource(R.string.detail_remove_confirm_title)) },
            text = { Text(stringResource(R.string.detail_remove_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    actions.onRemove()
                }) {
                    Text(stringResource(R.string.detail_remove_confirm), color = UnscrollTheme.status.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}
