package com.unscroll.app.ui.plus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.unscroll.app.R
import com.unscroll.app.domain.fox.FoxMood
import com.unscroll.app.domain.tracking.DefaultTrackedApps
import com.unscroll.app.ui.components.AppIcon
import com.unscroll.app.ui.components.LoadingPlaceholder
import com.unscroll.app.ui.components.UnscrollCard
import com.unscroll.app.ui.components.rememberAppLabel
import com.unscroll.app.ui.components.rememberHaptics
import com.unscroll.app.ui.durationText
import com.unscroll.app.ui.fox.FoxMascot
import com.unscroll.app.ui.fox.LocalFoxSettings
import com.unscroll.app.ui.theme.Dimens
import com.unscroll.app.ui.theme.UnscrollTheme

/**
 * "Pick your free app": the installed tracked apps with their icons, most used preselected. The
 * picked app is tracked fully; the others are paused, never deleted. Plus tracks them all.
 */
@Composable
fun PickAppsScreen(
    onDone: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PickAppsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PickAppsContent(
        state = state,
        onToggle = viewModel::toggle,
        onConfirm = { viewModel.confirm(onDone) },
        onPlus = onPlus,
        modifier = modifier,
    )
}

@Composable
private fun PickAppsContent(
    state: PickAppsUiState,
    onToggle: (String) -> Unit,
    onConfirm: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.isLoading) {
        LoadingPlaceholder(modifier = modifier.fillMaxSize())
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Dimens.screenPadding, vertical = Dimens.spaceXl),
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceM),
    ) {
        item(key = "header") {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceS)) {
                if (LocalFoxSettings.current.showFox) {
                    FoxMascot(mood = FoxMood.HAPPY, showTail = false, modifier = Modifier.size(Dimens.foxHome))
                }
                Text(
                    text = pluralStringResource(R.plurals.pick_title, state.freeApps, state.freeApps),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = pluralStringResource(R.plurals.pick_line, state.freeApps, state.freeApps),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(state.apps, key = { it.packageName }) { app ->
            PickRow(
                app = app,
                selected = app.packageName in state.selection,
                single = state.freeApps == 1,
                onToggle = { onToggle(app.packageName) },
            )
        }
        item(key = "actions") {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = Dimens.spaceS),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Dimens.spaceS),
            ) {
                Button(
                    onClick = onConfirm,
                    enabled = state.canConfirm,
                    modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.touchTarget),
                ) {
                    Text(stringResource(R.string.pick_continue))
                }
                Text(
                    text = stringResource(R.string.pick_paused_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onPlus) { Text(stringResource(R.string.pick_all_with_plus)) }
            }
        }
    }
}

@Composable
private fun PickRow(app: PickApp, selected: Boolean, single: Boolean, onToggle: () -> Unit) {
    val haptics = rememberHaptics()
    UnscrollCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = selected,
                    role = if (single) Role.RadioButton else Role.Checkbox,
                    onValueChange = {
                        haptics.toggle(it)
                        onToggle()
                    },
                )
                .heightIn(min = Dimens.touchTarget + Dimens.spaceL)
                .padding(horizontal = Dimens.spaceL, vertical = Dimens.spaceM),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.spaceL),
        ) {
            AppIcon(app.packageName)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = rememberAppLabel(app.packageName),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.pick_recent, durationText(app.recentMillis)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // The row handles the tap; these only show the state.
            if (single) RadioButton(selected = selected, onClick = null) else Checkbox(checked = selected, onCheckedChange = null)
        }
    }
}

@PreviewLightDark
@Composable
private fun PickAppsPreview() {
    UnscrollTheme {
        Surface {
            PickAppsContent(
                state = PickAppsUiState(
                    isLoading = false,
                    apps = listOf(
                        PickApp(DefaultTrackedApps.INSTAGRAM, 12 * 3_600_000L),
                        PickApp(DefaultTrackedApps.TIKTOK, 4 * 3_600_000L),
                        PickApp(DefaultTrackedApps.FACEBOOK, 0),
                    ),
                    selection = listOf(DefaultTrackedApps.INSTAGRAM),
                ),
                onToggle = {},
                onConfirm = {},
                onPlus = {},
            )
        }
    }
}
