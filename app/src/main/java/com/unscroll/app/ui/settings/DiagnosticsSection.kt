package com.unscroll.app.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import com.unscroll.app.R
import com.unscroll.app.ui.components.SettingRow
import com.unscroll.app.ui.components.SettingsGroup
import com.unscroll.app.ui.theme.Dimens
import com.unscroll.app.util.AppLog

/** Settings › Help: "Copy diagnostics". Nothing leaves the phone unless the user shares it. */
@Composable
internal fun DiagnosticsGroup(onPrepare: () -> Unit) {
    SettingsGroup {
        SettingRow(
            title = stringResource(R.string.diagnostics_copy),
            subtitle = stringResource(R.string.diagnostics_copy_hint),
            icon = R.drawable.ic_bug,
            onClick = onPrepare,
        )
    }
}

/** The report exactly as it would be shared; "Share" opens the system share sheet. */
@Composable
internal fun DiagnosticsPreviewDialog(report: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.diagnostics_preview_title)) },
        text = {
            Text(
                text = report,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .heightIn(max = Dimens.diagnosticsPreviewMaxHeight)
                    .verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    shareText(context, report)
                    onDismiss()
                },
            ) { Text(stringResource(R.string.diagnostics_share)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.diagnostics_subject))
        .putExtra(Intent.EXTRA_TEXT, text)
    try {
        context.startActivity(Intent.createChooser(send, context.getString(R.string.diagnostics_share)))
    } catch (e: ActivityNotFoundException) {
        AppLog.w("Diagnostics", "No app to share with", e)
    }
}
