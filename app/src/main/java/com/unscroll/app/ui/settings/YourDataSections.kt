package com.unscroll.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.unscroll.app.R
import com.unscroll.app.domain.export.SessionCsv
import com.unscroll.app.ui.components.RowDivider
import com.unscroll.app.ui.components.SettingRow
import com.unscroll.app.ui.components.SettingsGroup
import com.unscroll.app.ui.components.TrustPromise
import com.unscroll.app.ui.theme.Dimens
import com.unscroll.app.ui.theme.UnscrollTheme
import java.time.LocalDate

/**
 * Settings › Data & privacy: the privacy promise, CSV export, deleting the usage history (M8) and
 * deleting all data (everything, like a fresh install).
 */
@Composable
internal fun DataPrivacyGroup(viewModel: YourDataViewModel = hiltViewModel()) {
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val result by viewModel.result.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var confirmDeleteAll by rememberSaveable { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(SessionCsv.MIME_TYPE),
        viewModel::export,
    )
    val resultText = result?.let {
        when (it) {
            is DataActionResult.Exported -> pluralStringResource(R.plurals.data_exported, it.sessions, it.sessions)
            DataActionResult.ExportFailed -> stringResource(R.string.data_export_failed)
            is DataActionResult.Deleted -> pluralStringResource(R.plurals.data_deleted, it.sessions, it.sessions)
        }
    }

    SettingsGroup {
        TrustPromise(modifier = Modifier.padding(Dimens.spaceL))
        RowDivider()
        SettingRow(
            title = stringResource(R.string.data_export),
            icon = R.drawable.ic_download,
            subtitle = resultText.takeIf { result !is DataActionResult.Deleted },
            subtitleColor = if (result == DataActionResult.ExportFailed) {
                UnscrollTheme.status.danger
            } else {
                UnscrollTheme.status.good
            },
            onClick = { if (!busy) exportLauncher.launch(SessionCsv.fileName(LocalDate.now())) },
        )
        SettingRow(
            title = stringResource(R.string.data_delete),
            icon = R.drawable.ic_delete,
            titleColor = UnscrollTheme.status.danger,
            subtitle = resultText.takeIf { result is DataActionResult.Deleted },
            showChevron = false,
            onClick = { if (!busy) confirmDelete = true },
        )
        SettingRow(
            title = stringResource(R.string.data_delete_all),
            icon = R.drawable.ic_delete,
            titleColor = UnscrollTheme.status.danger,
            showChevron = false,
            onClick = { if (!busy) confirmDeleteAll = true },
        )
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
                    Text(stringResource(R.string.data_delete_confirm), color = UnscrollTheme.status.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text(stringResource(R.string.data_delete_all_confirm_title)) },
            text = { Text(stringResource(R.string.data_delete_all_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteAll = false
                    viewModel.deleteAllData()
                }) {
                    Text(stringResource(R.string.data_delete_all_confirm), color = UnscrollTheme.status.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteAll = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}
