package com.unscroll.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.unscroll.app.BuildConfig
import com.unscroll.app.R
import com.unscroll.app.ui.theme.UnscrollTheme

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val trackingEnabled by viewModel.trackingEnabled.collectAsStateWithLifecycle()
    val sampleSessionsAdded by viewModel.sampleSessionsAdded.collectAsStateWithLifecycle()
    SettingsContent(
        trackingEnabled = trackingEnabled,
        onTrackingEnabledChange = viewModel::setTrackingEnabled,
        showDebugTools = BuildConfig.DEBUG,
        sampleSessionsAdded = sampleSessionsAdded,
        onInsertSampleData = viewModel::insertSampleData,
        modifier = modifier,
    )
}

@Composable
private fun SettingsContent(
    trackingEnabled: Boolean,
    onTrackingEnabledChange: (Boolean) -> Unit,
    showDebugTools: Boolean,
    sampleSessionsAdded: Int?,
    onInsertSampleData: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(vertical = 16.dp),
    ) {
        Text(
            text = stringResource(R.string.nav_settings),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = trackingEnabled,
                    role = Role.Switch,
                    onValueChange = onTrackingEnabledChange,
                )
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_tracking_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.settings_tracking_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // The whole row handles the toggle, so the switch itself is not clickable.
            Switch(checked = trackingEnabled, onCheckedChange = null)
        }
        if (showDebugTools) {
            DebugTools(
                sampleSessionsAdded = sampleSessionsAdded,
                onInsertSampleData = onInsertSampleData,
            )
        }
    }
}

@Composable
private fun DebugTools(sampleSessionsAdded: Int?, onInsertSampleData: () -> Unit) {
    Column(
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_debug_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.settings_debug_sample_data_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = onInsertSampleData) {
            Text(stringResource(R.string.settings_debug_sample_data))
        }
        sampleSessionsAdded?.let { count ->
            Text(
                text = pluralStringResource(R.plurals.settings_debug_sample_data_added, count, count),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    UnscrollTheme {
        SettingsContent(
            trackingEnabled = true,
            onTrackingEnabledChange = {},
            showDebugTools = true,
            sampleSessionsAdded = 214,
            onInsertSampleData = {},
        )
    }
}
