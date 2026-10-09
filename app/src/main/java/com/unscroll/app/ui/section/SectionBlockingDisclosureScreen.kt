package com.unscroll.app.ui.section

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.hilt.navigation.compose.hiltViewModel
import com.unscroll.app.R
import com.unscroll.app.ui.scroll.DisclosureBlock
import com.unscroll.app.ui.theme.Dimens
import com.unscroll.app.ui.theme.UnscrollTheme
import com.unscroll.app.util.SystemSettings
import com.unscroll.app.util.openSettings

/**
 * The prominent disclosure for section blocking, shown before the user is ever sent to
 * Accessibility settings for its service. Nothing is read until the user taps "I agree".
 */
@Composable
fun SectionBlockingDisclosureScreen(
    onFinished: () -> Unit,
    onRestrictedHelp: () -> Unit,
    viewModel: SectionBlockingViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    SectionBlockingDisclosureContent(
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
private fun SectionBlockingDisclosureContent(
    onAgree: () -> Unit,
    onDecline: () -> Unit,
    onRestrictedHelp: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Dimens.spaceXl),
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceL),
    ) {
        Text(
            text = stringResource(R.string.section_disclosure_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        Text(text = stringResource(R.string.section_disclosure_intro), style = MaterialTheme.typography.bodyLarge)
        DisclosureBlock(
            title = stringResource(R.string.section_disclosure_does_title),
            body = stringResource(R.string.section_disclosure_does_body),
        )
        DisclosureBlock(
            title = stringResource(R.string.section_disclosure_does_not_title),
            body = stringResource(R.string.section_disclosure_does_not_body),
            highlight = true,
        )
        DisclosureBlock(
            title = stringResource(R.string.section_disclosure_optional_title),
            body = stringResource(R.string.section_disclosure_optional_body),
        )
        Text(
            text = stringResource(R.string.section_disclosure_next),
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

@PreviewLightDark
@Composable
private fun SectionBlockingDisclosurePreview() {
    UnscrollTheme {
        Surface { SectionBlockingDisclosureContent(onAgree = {}, onDecline = {}, onRestrictedHelp = {}) }
    }
}
