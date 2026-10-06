package com.unscroll.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.unscroll.app.R
import com.unscroll.app.domain.export.SessionCsv
import com.unscroll.app.domain.goals.DailyGoal
import java.time.LocalDate

/** Settings › Daily goal: off, or one of [DailyGoal.PRESETS] minutes across tracked apps. */
@Composable
fun DailyGoalSection(viewModel: YourDataViewModel = hiltViewModel()) {
    val goal by viewModel.dailyGoalMinutes.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.goal_settings_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.goal_settings_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = goal == null,
                onClick = { viewModel.setDailyGoal(null) },
                label = { Text(stringResource(R.string.goal_off)) },
            )
            DailyGoal.PRESETS.forEach { minutes ->
                FilterChip(
                    selected = goal == minutes,
                    onClick = { viewModel.setDailyGoal(minutes) },
                    label = { Text(stringResource(R.string.apps_limit_minutes, minutes)) },
                )
            }
        }
    }
}

/** Settings › Your data: CSV export and deleting the usage history. */
@Composable
fun YourDataSection(viewModel: YourDataViewModel = hiltViewModel()) {
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val result by viewModel.result.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(SessionCsv.MIME_TYPE),
        viewModel::export,
    )

    Column(
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.data_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.data_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = { exportLauncher.launch(SessionCsv.fileName(LocalDate.now())) },
            enabled = !busy,
        ) {
            Text(stringResource(R.string.data_export))
        }
        OutlinedButton(onClick = { confirmDelete = true }, enabled = !busy) {
            Text(stringResource(R.string.data_delete), color = MaterialTheme.colorScheme.error)
        }
        result?.let {
            Text(
                text = when (it) {
                    is DataActionResult.Exported ->
                        pluralStringResource(R.plurals.data_exported, it.sessions, it.sessions)
                    DataActionResult.ExportFailed -> stringResource(R.string.data_export_failed)
                    is DataActionResult.Deleted ->
                        pluralStringResource(R.plurals.data_deleted, it.sessions, it.sessions)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (it == DataActionResult.ExportFailed) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.data_delete_confirm_title)) },
            text = { Text(stringResource(R.string.data_delete_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.deleteUsageHistory()
                }) {
                    Text(stringResource(R.string.data_delete_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.data_delete_cancel))
                }
            },
        )
    }
}
