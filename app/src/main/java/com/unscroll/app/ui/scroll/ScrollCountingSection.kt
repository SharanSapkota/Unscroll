package com.unscroll.app.ui.scroll

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.unscroll.app.R
import com.unscroll.app.domain.scroll.ScrollConsent
import com.unscroll.app.domain.scroll.ScrollCountingRules
import com.unscroll.app.domain.scroll.ScrollCountingStatus
import com.unscroll.app.domain.scroll.SwipeBanner
import com.unscroll.app.ui.components.UnscrollCard
import com.unscroll.app.ui.theme.Dimens
import com.unscroll.app.ui.theme.UnscrollTheme
import com.unscroll.app.util.SystemSettings
import com.unscroll.app.util.openAccessibilitySettings
import com.unscroll.app.util.openSettings

/** Settings › Scroll counting (in a sheet): one status line and the actions that fit it. */
@Composable
fun ScrollCountingPanel(
    onSetUp: () -> Unit,
    onRestrictedHelp: () -> Unit,
    viewModel: ScrollCountingViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val status by viewModel.status.collectAsStateWithLifecycle()
    val openAccessibility = { context.openAccessibilitySettings() }

    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceM)) {
        Text(
            text = stringResource(
                when (status) {
                    ScrollCountingStatus.OFF -> R.string.scroll_status_off
                    ScrollCountingStatus.NEEDS_CONSENT -> R.string.scroll_status_needs_consent
                    ScrollCountingStatus.NEEDS_ENABLING -> R.string.scroll_status_needs_enabling
                    ScrollCountingStatus.ACTIVE -> R.string.scroll_status_active
                    ScrollCountingStatus.NEEDS_REENABLE -> R.string.scroll_status_needs_reenable
                },
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = if (status == ScrollCountingStatus.NEEDS_REENABLE) {
                UnscrollTheme.status.danger
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        when (status) {
            ScrollCountingStatus.OFF -> Button(onClick = onSetUp, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.scroll_set_up))
            }
            ScrollCountingStatus.NEEDS_CONSENT -> {
                Button(onClick = onSetUp, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.scroll_review))
                }
                OutlinedButton(onClick = openAccessibility, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.scroll_open_accessibility))
                }
            }
            ScrollCountingStatus.NEEDS_ENABLING, ScrollCountingStatus.NEEDS_REENABLE -> {
                Button(onClick = openAccessibility, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.scroll_open_accessibility))
                }
                RestrictedSettingHint(status = status, onHelp = onRestrictedHelp)
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceS)) {
                    TextButton(onClick = onRestrictedHelp) { Text(stringResource(R.string.scroll_restricted_link)) }
                    TextButton(onClick = viewModel::turnOff) { Text(stringResource(R.string.scroll_turn_off)) }
                }
            }
            ScrollCountingStatus.ACTIVE -> {
                OutlinedButton(onClick = viewModel::turnOff, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.scroll_turn_off))
                }
                TextButton(onClick = openAccessibility) { Text(stringResource(R.string.scroll_open_accessibility)) }
            }
        }
    }
}

/**
 * Android 13+: shown when the user agreed, went to Accessibility settings and came back without
 * the service on. Apps installed outside an app store must first be allowed restricted settings:
 * App info › ⋮ › Allow restricted settings. Nothing otherwise.
 */
@Composable
fun RestrictedSettingHint(status: ScrollCountingStatus, onHelp: () -> Unit, modifier: Modifier = Modifier) {
    if (!ScrollCountingRules.showRestrictedHelp(status, Build.VERSION.SDK_INT)) return
    val context = LocalContext.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs)) {
        Text(
            text = stringResource(R.string.scroll_restricted_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceS)) {
            TextButton(onClick = { context.openSettings(SystemSettings.appDetails(context)) }) {
                Text(stringResource(R.string.scroll_open_app_info))
            }
            TextButton(onClick = onHelp) { Text(stringResource(R.string.scroll_restricted_how)) }
        }
    }
}

/**
 * Home's swipe-counting banner, one at a time ([ScrollCountingRules.banner]):
 * - never enabled: a small "Turn on swipe counting" card with "Turn on" and "Not now" (hidden for
 *   7 days); "Turn on" shows the disclosure first unless the user already agreed;
 * - switched on in Android without agreeing in Unscroll: "Finish setting up", to the disclosure;
 * - was counting, then the phone switched it off: "Swipe counting stopped" with "Turn back on".
 */
@Composable
fun ScrollCountingBanner(
    onSetUp: () -> Unit,
    onRestrictedHelp: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ScrollCountingViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val banner by viewModel.banner.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val consent by viewModel.consent.collectAsStateWithLifecycle()
    ScrollCountingBannerContent(
        banner = banner,
        status = status,
        onTurnOn = {
            if (consent == ScrollConsent.AGREED) context.openAccessibilitySettings() else onSetUp()
        },
        onReview = onSetUp,
        onDismiss = viewModel::dismissBanner,
        onTurnOff = viewModel::turnOff,
        onRestrictedHelp = onRestrictedHelp,
        modifier = modifier,
    )
}

@Composable
private fun ScrollCountingBannerContent(
    banner: SwipeBanner,
    status: ScrollCountingStatus,
    onTurnOn: () -> Unit,
    onReview: () -> Unit,
    onDismiss: () -> Unit,
    onTurnOff: () -> Unit,
    onRestrictedHelp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (banner == SwipeBanner.NONE) return
    UnscrollCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Dimens.spaceL),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs),
        ) {
            Text(
                text = stringResource(
                    when (banner) {
                        SwipeBanner.SET_UP, SwipeBanner.NONE -> R.string.scroll_setup_banner_title
                        SwipeBanner.FINISH_CONSENT -> R.string.scroll_consent_banner_title
                        SwipeBanner.NEEDS_REENABLE -> R.string.scroll_banner_title
                    },
                ),
                style = MaterialTheme.typography.titleMedium,
                color = if (banner == SwipeBanner.NEEDS_REENABLE) UnscrollTheme.status.danger else MaterialTheme.colorScheme.onSurface,
            )
            if (banner != SwipeBanner.NEEDS_REENABLE) {
                Text(
                    text = stringResource(
                        if (banner == SwipeBanner.FINISH_CONSENT) R.string.scroll_consent_banner_body else R.string.scroll_setup_banner_body,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (banner == SwipeBanner.SET_UP) RestrictedSettingHint(status = status, onHelp = onRestrictedHelp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.spaceS)) {
                when (banner) {
                    SwipeBanner.SET_UP, SwipeBanner.NONE -> {
                        Button(onClick = onTurnOn) { Text(stringResource(R.string.scroll_setup_banner_action)) }
                        TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_not_now)) }
                    }
                    SwipeBanner.FINISH_CONSENT -> {
                        Button(onClick = onReview) { Text(stringResource(R.string.scroll_review)) }
                        TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_not_now)) }
                    }
                    SwipeBanner.NEEDS_REENABLE -> {
                        Button(onClick = onTurnOn) { Text(stringResource(R.string.scroll_banner_action)) }
                        TextButton(onClick = onTurnOff) { Text(stringResource(R.string.scroll_turn_off)) }
                    }
                }
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun ScrollCountingBannerPreview() {
    UnscrollTheme {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceL)) {
            listOf(SwipeBanner.SET_UP, SwipeBanner.FINISH_CONSENT, SwipeBanner.NEEDS_REENABLE).forEach {
                ScrollCountingBannerContent(it, ScrollCountingStatus.OFF, {}, {}, {}, {}, {})
            }
        }
    }
}
