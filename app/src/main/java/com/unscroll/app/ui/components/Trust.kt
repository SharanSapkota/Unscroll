package com.unscroll.app.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.unscroll.app.R
import com.unscroll.app.domain.fox.FoxMood
import com.unscroll.app.ui.fox.FoxMascot
import com.unscroll.app.ui.fox.LocalFoxSettings
import com.unscroll.app.ui.theme.Dimens
import com.unscroll.app.ui.theme.UnscrollTheme

// The privacy promise. Every sentence here must stay true: the app has no INTERNET permission
// (removed in the manifest, checked on the merged manifest in CI), no analytics or crash-reporting
// SDKs, no accounts, and app backups are off. Unscroll Plus goes through Google Play Billing, which
// talks to the Play Store app on the phone and never gets usage data.

/**
 * "Your data stays on your phone." and one muted line. With [fox] (onboarding), the happy fox sits
 * next to it, unless the user hid the fox.
 */
@Composable
fun TrustPromise(modifier: Modifier = Modifier, fox: Boolean = false) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceL),
    ) {
        if (fox && LocalFoxSettings.current.showFox) {
            FoxMascot(mood = FoxMood.HAPPY, showTail = false, modifier = Modifier.size(Dimens.foxSmall))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs)) {
            Text(
                text = stringResource(R.string.trust_headline),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.trust_line),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** "We're here to get you out of the doomscroll." and the "How we protect your data" link. */
@Composable
fun TrustFooter(onHowWeProtect: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.trust_footer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onHowWeProtect) { Text(stringResource(R.string.trust_how)) }
    }
}

/** The facts behind the promise, in a bottom sheet. */
@Composable
fun TrustSheet(onDismiss: () -> Unit) {
    UnscrollSheet(title = stringResource(R.string.trust_how), onDismiss = onDismiss) {
        TrustBullets()
        TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
            Text(stringResource(R.string.action_got_it))
        }
    }
}

@Composable
private fun TrustBullets() {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceM)) {
        TrustBullet(R.string.trust_bullet_device)
        TrustBullet(R.string.trust_bullet_never)
        TrustBullet(R.string.trust_bullet_purchases)
        TrustBullet(R.string.trust_bullet_delete)
    }
}

@Composable
private fun TrustBullet(@StringRes text: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceM)) {
        Box(
            modifier = Modifier
                .padding(top = Dimens.spaceS)
                .size(Dimens.dot)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
        )
        Text(
            text = stringResource(text),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@PreviewLightDark
@PreviewFontScale
@Composable
private fun TrustPreview() {
    UnscrollTheme {
        Surface {
            Column(modifier = Modifier.padding(Dimens.spaceL), verticalArrangement = Arrangement.spacedBy(Dimens.spaceL)) {
                TrustPromise(fox = true)
                TrustPromise()
                TrustFooter(onHowWeProtect = {})
                TrustBullets()
                Spacer(Modifier.size(Dimens.spaceS))
            }
        }
    }
}
