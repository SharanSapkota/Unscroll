package com.unscroll.app.ui.scroll

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.unscroll.app.R
import com.unscroll.app.ui.theme.UnscrollTheme
import com.unscroll.app.util.SystemSettings
import com.unscroll.app.util.openSettings

/**
 * The prominent disclosure for the accessibility service, shown before the user is ever sent to
 * Accessibility settings. Nothing is counted until the user taps "I agree" here.
 */
@Composable
fun AccessibilityDisclosureScreen(
    onFinished: () -> Unit,
    onRestrictedHelp: () -> Unit,
    viewModel: ScrollCountingViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    AccessibilityDisclosureContent(
        onAgree = {
            viewModel.agree()
            context.openSettings(SystemSettings.accessibility())
            onFinished()
        },
        onDecline = {
            viewModel.decline()
            onFinished()
        },
        onRestrictedHelp = onRestrictedHelp,
    )
}

@Composable
private fun AccessibilityDisclosureContent(
    onAgree: () -> Unit,
    onDecline: () -> Unit,
    onRestrictedHelp: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.scroll_disclosure_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.scroll_disclosure_intro),
            style = MaterialTheme.typography.bodyLarge,
        )
        DisclosureBlock(
            title = stringResource(R.string.scroll_disclosure_does_title),
            body = stringResource(R.string.scroll_disclosure_does_body),
        )
        DisclosureBlock(
            title = stringResource(R.string.scroll_disclosure_does_not_title),
            body = stringResource(R.string.scroll_disclosure_does_not_body),
            highlight = true,
        )
        DisclosureBlock(
            title = stringResource(R.string.scroll_disclosure_optional_title),
            body = stringResource(R.string.scroll_disclosure_optional_body),
        )
        Text(
            text = stringResource(R.string.scroll_disclosure_next),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onAgree, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.scroll_disclosure_agree))
        }
        OutlinedButton(onClick = onDecline, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.scroll_disclosure_decline))
        }
        TextButton(onClick = onRestrictedHelp) {
            Text(stringResource(R.string.scroll_restricted_link))
        }
    }
}

@Composable
private fun DisclosureBlock(title: String, body: String, highlight: Boolean = false) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (highlight) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            contentColor = if (highlight) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(text = body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Preview(showBackground = true, heightDp = 1200)
@Composable
private fun AccessibilityDisclosurePreview() {
    UnscrollTheme {
        AccessibilityDisclosureContent(onAgree = {}, onDecline = {}, onRestrictedHelp = {})
    }
}
