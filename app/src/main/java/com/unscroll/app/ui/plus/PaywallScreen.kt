package com.unscroll.app.ui.plus

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.unscroll.app.R
import com.unscroll.app.domain.fox.FoxMood
import com.unscroll.app.domain.plus.FreeTier
import com.unscroll.app.domain.plus.PlusOffer
import com.unscroll.app.ui.components.UnscrollCard
import com.unscroll.app.ui.fox.FoxMascot
import com.unscroll.app.ui.fox.LocalFoxSettings
import com.unscroll.app.ui.theme.Dimens
import com.unscroll.app.ui.theme.UnscrollTheme
import com.unscroll.app.util.openUrl

/**
 * Unscroll Plus. Calm and honest: the price Play returns (never a hardcoded one), what Plus adds,
 * and an easy way out. No countdowns, no fake urgency, nothing pre-selected.
 */
@Composable
fun PaywallScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PaywallViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    PaywallContent(
        state = state,
        onContinue = { activity?.let(viewModel::buy) },
        onRestore = viewModel::restore,
        onRetry = viewModel::loadPrice,
        onClose = onClose,
        modifier = modifier,
    )
}

@Composable
private fun PaywallContent(
    state: PaywallUiState,
    onContinue: () -> Unit,
    onRestore: () -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Dimens.screenPadding, vertical = Dimens.spaceXl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceL),
    ) {
        if (LocalFoxSettings.current.showFox) {
            FoxMascot(mood = FoxMood.HAPPY, modifier = Modifier.size(Dimens.foxLarge))
        }
        Text(
            text = stringResource(R.string.plus_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() },
        )
        PriceLine(state.price, onRetry)

        UnscrollCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(Dimens.spaceL),
                verticalArrangement = Arrangement.spacedBy(Dimens.spaceM),
            ) {
                Bullet(pluralStringResource(R.plurals.plus_bullet_apps, FreeTier.FREE_APPS, FreeTier.FREE_APPS))
                Bullet(stringResource(R.string.plus_bullet_support))
                Bullet(stringResource(R.string.plus_bullet_cancel))
            }
        }

        Text(
            text = stringResource(R.string.plus_why),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        if (state.isPlus) {
            Text(
                text = stringResource(R.string.plus_thanks),
                style = MaterialTheme.typography.titleMedium,
                color = UnscrollTheme.status.good,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onClose, modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.touchTarget)) {
                Text(stringResource(R.string.action_done))
            }
        } else {
            Button(
                onClick = onContinue,
                enabled = state.price is PriceState.Available && !state.busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.touchTarget),
            ) {
                Text(stringResource(R.string.plus_continue))
            }
            Text(
                text = stringResource(R.string.plus_renewal_terms),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        val message = state.message ?: PaywallMessage.PENDING.takeIf { state.pending && !state.isPlus }
        if (message != null) {
            Text(
                text = stringResource(message.textRes),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }

        if (!state.isPlus) {
            TextButton(onClick = onRestore, enabled = !state.busy) {
                Text(stringResource(R.string.plus_restore))
            }
            TextButton(onClick = onClose) { Text(stringResource(R.string.plus_not_now)) }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceS)) {
            val privacyUrl = stringResource(R.string.url_privacy_policy)
            val termsUrl = stringResource(R.string.url_terms)
            TextButton(onClick = { context.openUrl(privacyUrl) }) {
                Text(stringResource(R.string.plus_privacy_policy), style = MaterialTheme.typography.labelSmall)
            }
            TextButton(onClick = { context.openUrl(termsUrl) }) {
                Text(stringResource(R.string.plus_terms), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun PriceLine(price: PriceState, onRetry: () -> Unit) {
    when (price) {
        PriceState.Loading -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.spaceS),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(Dimens.iconSmall), strokeWidth = Dimens.spaceXxs)
            Text(
                text = stringResource(R.string.plus_price_loading),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        is PriceState.Available -> Text(
            text = stringResource(R.string.plus_price_monthly, price.offer.formattedPrice),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        PriceState.Unavailable -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.plus_billing_unavailable),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            TextButton(onClick = onRetry) { Text(stringResource(R.string.plus_try_again)) }
        }
    }
}

@Composable
private fun Bullet(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceM), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(R.drawable.ic_check),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(Dimens.icon),
        )
        Text(text = text, style = MaterialTheme.typography.bodyLarge)
    }
}

/** The line shown for each message (paywall and Settings). */
internal val PaywallMessage.textRes: Int
    get() = when (this) {
        PaywallMessage.RESTORED -> R.string.plus_message_restored
        PaywallMessage.NOTHING_TO_RESTORE -> R.string.plus_message_nothing
        PaywallMessage.PENDING -> R.string.plus_message_pending
        PaywallMessage.UNAVAILABLE -> R.string.plus_billing_unavailable
        PaywallMessage.PURCHASE_FAILED -> R.string.plus_message_failed
    }

@PreviewLightDark
@Composable
private fun PaywallPreview() {
    UnscrollTheme {
        Surface {
            PaywallContent(
                state = PaywallUiState(price = PriceState.Available(PlusOffer("€0.67", "token"))),
                onContinue = {},
                onRestore = {},
                onRetry = {},
                onClose = {},
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun PaywallUnavailablePreview() {
    UnscrollTheme {
        Surface {
            PaywallContent(
                state = PaywallUiState(price = PriceState.Unavailable, message = PaywallMessage.NOTHING_TO_RESTORE),
                onContinue = {},
                onRestore = {},
                onRetry = {},
                onClose = {},
            )
        }
    }
}
