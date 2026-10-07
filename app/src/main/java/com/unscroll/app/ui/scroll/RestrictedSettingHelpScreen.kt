package com.unscroll.app.ui.scroll

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.unscroll.app.ui.theme.Dimens
import com.unscroll.app.R
import com.unscroll.app.ui.theme.UnscrollTheme
import com.unscroll.app.util.SystemSettings
import com.unscroll.app.util.openSettings

/**
 * Help for "Restricted setting" on Android 13+: apps installed outside an app store can't turn on an
 * accessibility service until the user allows it in App info.
 */
@Composable
fun RestrictedSettingHelpScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val steps = stringArrayResource(R.array.scroll_restricted_steps)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Dimens.spaceXl),
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceL),
    ) {
        Text(
            text = stringResource(R.string.scroll_restricted_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.scroll_restricted_intro),
            style = MaterialTheme.typography.bodyLarge,
        )
        steps.forEachIndexed { index, step ->
            Text(
                text = stringResource(R.string.scroll_restricted_step, index + 1, step),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        Text(
            text = stringResource(R.string.scroll_restricted_note),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = { context.openSettings(SystemSettings.appDetails(context)) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.scroll_open_app_info))
        }
        OutlinedButton(
            onClick = { context.openSettings(SystemSettings.accessibility()) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.scroll_open_accessibility))
        }
        TextButton(onClick = onBack) {
            Text(stringResource(R.string.scroll_back))
        }
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun RestrictedSettingHelpPreview() {
    UnscrollTheme { RestrictedSettingHelpScreen(onBack = {}) }
}
