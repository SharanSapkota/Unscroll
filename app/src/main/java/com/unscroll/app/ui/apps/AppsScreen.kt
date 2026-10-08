package com.unscroll.app.ui.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.unscroll.app.R
import com.unscroll.app.domain.blocking.BlockDecision
import com.unscroll.app.domain.blocking.BlockReason
import com.unscroll.app.domain.blocking.LimitSettings
import com.unscroll.app.domain.plus.AppTrackingStatus
import com.unscroll.app.domain.tracking.DefaultTrackedApps
import com.unscroll.app.ui.components.AppIcon
import com.unscroll.app.ui.components.LoadingPlaceholder
import com.unscroll.app.ui.components.UnscrollCard
import com.unscroll.app.ui.components.rememberAppLabel
import com.unscroll.app.ui.components.rememberHaptics
import com.unscroll.app.ui.durationText
import com.unscroll.app.ui.fox.FoxCorner
import com.unscroll.app.ui.plus.PlusBadge
import com.unscroll.app.ui.theme.Dimens
import com.unscroll.app.ui.theme.UnscrollTheme

@Composable
fun AppsScreen(
    onOpenApp: (String) -> Unit,
    onOpenPlus: () -> Unit,
    onAddApps: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AppsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    AppsContent(
        state = uiState,
        onOpenApp = onOpenApp,
        onOpenPlus = onOpenPlus,
        onAddApps = onAddApps,
        onBlock = viewModel::setBlocked,
        onBlockAll = viewModel::setAllBlocked,
        modifier = modifier,
        fox = { FoxCorner(size = Dimens.foxSmall, showTail = false) },
    )
}

@Composable
private fun AppsContent(
    state: AppsUiState,
    onOpenApp: (String) -> Unit,
    onOpenPlus: () -> Unit,
    onAddApps: () -> Unit,
    onBlock: (String, Boolean) -> Unit,
    onBlockAll: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    fox: @Composable () -> Unit = {},
) {
    if (state.isLoading) {
        LoadingPlaceholder(modifier = modifier.fillMaxSize())
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Dimens.screenPadding, vertical = Dimens.spaceL),
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceM),
    ) {
        item(key = "title") {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.nav_apps),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { heading() },
                )
                // A small fox peeking in next to the title.
                fox()
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { onBlockAll(!state.allBlocked) }) {
                    Text(stringResource(if (state.allBlocked) R.string.apps_unblock_all else R.string.apps_block_all))
                }
                IconButton(onClick = onAddApps) {
                    Icon(painter = painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.apps_add))
                }
            }
        }
        items(state.apps, key = { it.packageName }) { app ->
            when (app.status) {
                AppTrackingStatus.ACTIVE -> AppRow(
                    app = app,
                    onClick = { onOpenApp(app.packageName) },
                    onBlock = { onBlock(app.packageName, it) },
                )
                // Over the free tier: Plus tracks it.
                AppTrackingStatus.LOCKED -> PausedAppRow(app = app, onClick = onOpenPlus)
                // Paused by the user, or uninstalled: App detail can resume or remove it.
                AppTrackingStatus.PAUSED, AppTrackingStatus.NOT_INSTALLED ->
                    PausedAppRow(app = app, onClick = { onOpenApp(app.packageName) })
            }
        }
    }
}

/** Icon, name, today's time, a status line in color, and the Block switch. Tap opens App detail. */
@Composable
private fun AppRow(app: AppRowState, onClick: () -> Unit, onBlock: (Boolean) -> Unit) {
    val haptics = rememberHaptics()
    val blockLabel = stringResource(R.string.apps_block)
    UnscrollCard(onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Dimens.touchTarget + Dimens.spaceL)
                .padding(horizontal = Dimens.spaceL, vertical = Dimens.spaceM),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.spaceL),
        ) {
            AppIcon(app.packageName)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.label ?: rememberAppLabel(app.packageName),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = limitStatusText(app.decision, app.settings),
                    style = MaterialTheme.typography.bodySmall,
                    color = limitStatusColor(app.decision, app.settings),
                    maxLines = 1,
                )
            }
            Text(text = durationText(app.todayMillis), style = MaterialTheme.typography.titleMedium)
            Switch(
                checked = app.settings.blockedAlways,
                onCheckedChange = {
                    haptics.toggle(it)
                    onBlock(it)
                },
                modifier = Modifier.semantics { contentDescription = blockLabel },
            )
        }
    }
}

/**
 * An app that isn't tracked right now, greyed out; its history and settings are kept. Free tier
 * (LOCKED): a lock and a "Plus" badge, tapping opens Unscroll Plus. Paused by the user or not
 * installed: the reason, tapping opens App detail.
 */
@Composable
private fun PausedAppRow(app: AppRowState, onClick: () -> Unit) {
    val locked = app.status == AppTrackingStatus.LOCKED
    val statusText = when (app.status) {
        AppTrackingStatus.NOT_INSTALLED -> stringResource(R.string.apps_not_installed)
        AppTrackingStatus.PAUSED -> stringResource(R.string.apps_paused_by_you)
        else -> stringResource(R.string.apps_paused)
    }
    // Locked rows read as "Paused. Unscroll Plus tracks this app."; the others read their texts.
    val lockedLabel = stringResource(R.string.apps_paused_description)
    val semantics = if (locked) Modifier.semantics { contentDescription = lockedLabel } else Modifier
    UnscrollCard(onClick = onClick, modifier = semantics) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Dimens.touchTarget + Dimens.spaceL)
                .padding(horizontal = Dimens.spaceL, vertical = Dimens.spaceM),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.spaceL),
        ) {
            AppIcon(app.packageName, modifier = Modifier.alpha(PAUSED_ALPHA))
            Column(modifier = Modifier.weight(1f).alpha(PAUSED_ALPHA)) {
                Text(
                    text = app.label ?: rememberAppLabel(app.packageName),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            if (locked) {
                Icon(
                    painter = painterResource(R.drawable.ic_lock),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(Dimens.iconSmall),
                )
                PlusBadge()
            }
        }
    }
}

private const val PAUSED_ALPHA = 0.5f

@PreviewLightDark
@Composable
private fun AppsPreview() {
    UnscrollTheme {
        AppsContent(
            state = AppsUiState(
                isLoading = false,
                apps = listOf(
                    AppRowState(
                        DefaultTrackedApps.INSTAGRAM,
                        LimitSettings(dailyLimitMinutes = 60),
                        BlockDecision.Allowed(32 * 60_000L),
                        28 * 60_000L,
                    ),
                    AppRowState(
                        DefaultTrackedApps.TIKTOK,
                        LimitSettings(blockedAlways = true),
                        BlockDecision.Blocked(BlockReason.BLOCKED_ALWAYS, null),
                        0,
                    ),
                    AppRowState(
                        DefaultTrackedApps.FACEBOOK,
                        LimitSettings(),
                        BlockDecision.Allowed(null),
                        0,
                        status = AppTrackingStatus.LOCKED,
                    ),
                    AppRowState(
                        "com.example.video",
                        LimitSettings(),
                        BlockDecision.Allowed(null),
                        0,
                        status = AppTrackingStatus.NOT_INSTALLED,
                        label = "Video",
                    ),
                ),
            ),
            onOpenApp = {},
            onOpenPlus = {},
            onAddApps = {},
            onBlock = { _, _ -> },
            onBlockAll = {},
        )
    }
}
