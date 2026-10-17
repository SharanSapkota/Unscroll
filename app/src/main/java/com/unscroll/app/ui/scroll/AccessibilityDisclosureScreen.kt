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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.unscroll.app.ui.theme.Dimens
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.unscroll.app.R
import com.unscroll.app.domain.reels.ReelsOnlyPillConfig
import com.unscroll.app.ui.theme.UnscrollTheme
import com.unscroll.app.util.openAccessibilitySettings

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
            context.openAccessibilitySettings()
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
            .padding(Dimens.spaceXl),
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceL),
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
        ReelsDisclosureBlock()
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

/**
 * The screen checks for the Reels-only timer: the required sentence and how it works. Part of every
 * accessibility disclosure (Settings, onboarding, the updated disclosure for existing users).
 */
@Composable
internal fun ReelsDisclosureBlock() {
    if (!ReelsOnlyPillConfig.ENABLED) return
    DisclosureBlock(
        title = stringResource(R.string.scroll_disclosure_reels_title),
        body = stringResource(R.string.scroll_disclosure_reels_body) + "\n\n" + stringResource(R.string.scroll_disclosure_reels_how),
    )
}

/**
 * The updated disclosure for users who agreed to swipe counting before the Reels-only timer
 * existed: screen checks need their own "I agree". "No thanks" keeps swipe counting and the
 * whole-app timer. If the service isn't running yet, "I agree" also opens Accessibility settings.
 */
@Composable
fun ReelsDisclosureScreen(
    onFinished: () -> Unit,
    viewModel: ScrollCountingViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val connected by viewModel.connected.collectAsStateWithLifecycle()
    ReelsDisclosureContent(
        onAgree = {
            viewModel.answerReels(agreed = true)
            if (!connected) context.openAccessibilitySettings()
            onFinished()
        },
        onDecline = {
            viewModel.answerReels(agreed = false)
            onFinished()
        },
    )
}

@Composable
private fun ReelsDisclosureContent(onAgree: () -> Unit, onDecline: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Dimens.spaceXl),
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceL),
    ) {
        Text(
            text = stringResource(R.string.reels_disclosure_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        Text(text = stringResource(R.string.reels_disclosure_intro), style = MaterialTheme.typography.bodyLarge)
        DisclosureBlock(
            title = stringResource(R.string.scroll_disclosure_does_title),
            body = stringResource(R.string.scroll_disclosure_reels_body) + "\n\n" + stringResource(R.string.scroll_disclosure_reels_how),
        )
        DisclosureBlock(
            title = stringResource(R.string.scroll_disclosure_does_not_title),
            body = stringResource(R.string.reels_disclosure_does_not_body),
            highlight = true,
        )
        DisclosureBlock(
            title = stringResource(R.string.scroll_disclosure_optional_title),
            body = stringResource(R.string.reels_disclosure_optional_body),
        )
        Button(onClick = onAgree, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.scroll_disclosure_agree))
        }
        OutlinedButton(onClick = onDecline, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.scroll_disclosure_decline))
        }
    }
}

@Composable
internal fun DisclosureBlock(title: String, body: String, highlight: Boolean = false) {
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
            modifier = Modifier.padding(Dimens.spaceL),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(text = body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Preview(showBackground = true, heightDp = 1200)
@Composable
private fun ReelsDisclosurePreview() {
    UnscrollTheme { ReelsDisclosureContent(onAgree = {}, onDecline = {}) }
}

@Preview(showBackground = true, heightDp = 1200)
@Composable
private fun AccessibilityDisclosurePreview() {
    UnscrollTheme {
        AccessibilityDisclosureContent(onAgree = {}, onDecline = {}, onRestrictedHelp = {})
    }
}
