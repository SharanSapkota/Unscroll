package com.unscroll.app.ui.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.unscroll.app.R
import com.unscroll.app.ui.components.AppIcon
import com.unscroll.app.ui.components.LoadingPlaceholder
import com.unscroll.app.ui.components.SectionHeader
import com.unscroll.app.ui.components.UnscrollCard
import com.unscroll.app.ui.components.rememberHaptics
import com.unscroll.app.ui.theme.Dimens
import com.unscroll.app.ui.theme.UnscrollTheme

/**
 * "Add apps": search at the top, a "Popular" row of installed popular apps, then every launchable
 * app by name with "Add" (a check once added). Excluded apps (Unscroll, home screens, Settings,
 * the phone, emergency apps, the Play Store) never appear.
 */
@Composable
fun AddAppsScreen(
    onBack: () -> Unit,
    onOpenPlus: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AddAppsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.openPaywall.collect { onOpenPlus() } }
    AddAppsContent(
        state = state,
        onQuery = viewModel::setQuery,
        onAdd = viewModel::add,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
private fun AddAppsContent(
    state: AddAppsUiState,
    onQuery: (String) -> Unit,
    onAdd: (PickerApp) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.padding(start = Dimens.spaceXs)) {
                Icon(painter = painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.cd_back))
            }
            Text(
                text = stringResource(R.string.add_apps_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
        }
        OutlinedTextField(
            value = state.query,
            onValueChange = onQuery,
            singleLine = true,
            placeholder = { Text(stringResource(R.string.add_apps_search)) },
            leadingIcon = { Icon(painter = painterResource(R.drawable.ic_search), contentDescription = null) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.screenPadding, vertical = Dimens.spaceS),
        )
        if (state.isLoading) {
            LoadingPlaceholder()
            return@Column
        }
        LazyColumn(
            contentPadding = PaddingValues(
                start = Dimens.screenPadding,
                end = Dimens.screenPadding,
                bottom = Dimens.spaceHuge,
            ),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceS),
        ) {
            if (state.popular.isNotEmpty()) {
                item(key = "popularHeader") { SectionHeader(title = stringResource(R.string.add_apps_popular)) }
                item(key = "popular") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceM)) {
                        items(state.popular, key = { it.packageName }) { app -> PopularTile(app, onAdd) }
                    }
                }
            }
            item(key = "allHeader") { SectionHeader(title = stringResource(R.string.add_apps_all)) }
            if (state.all.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = stringResource(R.string.add_apps_none),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(Dimens.spaceXl),
                    )
                }
            }
            items(state.all, key = { it.packageName }) { app -> PickerRow(app, onAdd) }
        }
    }
}

/** Icon, name and "Add"; "Added" with a check once it is in the list. */
@Composable
private fun PickerRow(app: PickerApp, onAdd: (PickerApp) -> Unit) {
    UnscrollCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Dimens.touchTarget + Dimens.spaceS)
                .padding(horizontal = Dimens.spaceL, vertical = Dimens.spaceS),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.spaceL),
        ) {
            AppIcon(app.packageName)
            Text(
                text = app.label,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            AddButton(app, onAdd)
        }
    }
}

@Composable
private fun PopularTile(app: PickerApp, onAdd: (PickerApp) -> Unit) {
    Column(
        modifier = Modifier.width(Dimens.tileWidth / 2 + Dimens.spaceXl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs),
    ) {
        AppIcon(app.packageName, size = Dimens.appIconLarge)
        Text(
            text = app.label,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        AddButton(app, onAdd)
    }
}

@Composable
private fun AddButton(app: PickerApp, onAdd: (PickerApp) -> Unit) {
    val haptics = rememberHaptics()
    if (app.added) {
        Row(
            modifier = Modifier.heightIn(min = Dimens.touchTarget),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.spaceXs),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                tint = UnscrollTheme.status.good,
                modifier = Modifier.size(Dimens.iconSmall),
            )
            Text(
                text = stringResource(R.string.add_apps_added),
                style = MaterialTheme.typography.labelLarge,
                color = UnscrollTheme.status.good,
            )
        }
    } else {
        FilledTonalButton(
            onClick = {
                haptics.tick()
                onAdd(app)
            },
        ) {
            Text(stringResource(R.string.add_apps_add))
        }
    }
}

@PreviewLightDark
@Composable
private fun AddAppsPreview() {
    UnscrollTheme {
        Surface {
            AddAppsContent(
                state = AddAppsUiState(
                    isLoading = false,
                    popular = listOf(
                        PickerApp("com.google.android.youtube", "YouTube", added = false),
                        PickerApp("com.reddit.frontpage", "Reddit", added = true),
                    ),
                    all = listOf(
                        PickerApp("com.example.notes", "Notes", added = false),
                        PickerApp("com.reddit.frontpage", "Reddit", added = true),
                        PickerApp("com.google.android.youtube", "YouTube", added = false),
                    ),
                ),
                onQuery = {},
                onAdd = {},
                onBack = {},
            )
        }
    }
}
