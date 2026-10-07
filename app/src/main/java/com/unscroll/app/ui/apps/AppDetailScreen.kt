package com.unscroll.app.ui.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.unscroll.app.R
import com.unscroll.app.domain.blocking.BlockDecision
import com.unscroll.app.domain.blocking.LimitSettings
import com.unscroll.app.domain.insights.DayUsage
import com.unscroll.app.domain.tracking.TrackedApps
import com.unscroll.app.ui.components.AppIcon
import com.unscroll.app.ui.components.LoadingPlaceholder
import com.unscroll.app.ui.components.MiniBarChart
import com.unscroll.app.ui.components.SectionHeader
import com.unscroll.app.ui.components.StatValue
import com.unscroll.app.ui.components.UnscrollCard
import com.unscroll.app.ui.components.rememberAppLabel
import com.unscroll.app.ui.durationText
import com.unscroll.app.ui.theme.Dimens
import com.unscroll.app.ui.theme.UnscrollTheme
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.flow.collectLatest

@Composable
fun AppDetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AppDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val savedText = stringResource(R.string.detail_saved)
    LaunchedEffect(viewModel) {
        // A brief confirmation per change; a newer change replaces the one showing.
        viewModel.saved.collectLatest { snackbar.showSnackbar(savedText, duration = SnackbarDuration.Short) }
    }
    Box(modifier = modifier.fillMaxSize()) {
        AppDetailContent(
            state = state,
            actions = DetailActions(
                onDailyLimit = viewModel::setDailyLimit,
                onSwipeLimit = viewModel::setSwipeLimit,
                onSwipeScope = viewModel::setSwipeLimitScope,
                onSwipeGap = viewModel::setSwipeSessionGap,
                onSwipeAccess = viewModel::setSwipeAccessAllowed,
                onBlocked = viewModel::setBlockedAlways,
                onSchedule = viewModel::setScheduleEnabled,
                onScheduleDay = viewModel::toggleScheduleDay,
                onScheduleStart = viewModel::setScheduleStart,
                onScheduleEnd = viewModel::setScheduleEnd,
                onPillShown = viewModel::setPillShown,
                onSwipesShown = viewModel::setSwipesShown,
                onFriction = viewModel::updateFriction,
                onResetFriction = viewModel::resetFriction,
            ),
            onBack = onBack,
        )
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(Dimens.spaceL),
        )
    }
}

@Composable
internal fun AppDetailContent(
    state: AppDetailUiState,
    actions: DetailActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        IconButton(onClick = onBack, modifier = Modifier.padding(start = Dimens.spaceXs)) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_back),
                contentDescription = stringResource(R.string.cd_back),
            )
        }
        if (state.isLoading) {
            LoadingPlaceholder()
            return@Column
        }
        LazyColumn(
            contentPadding = PaddingValues(
                start = Dimens.screenPadding,
                end = Dimens.screenPadding,
                bottom = Dimens.spaceHuge + Dimens.spaceXxl,
            ),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceL),
        ) {
            item(key = "header") { Header(state) }
            item(key = "limits") { LimitsSection(state, actions) }
            item(key = "blocking") { BlockingSection(state.settings, actions) }
            item(key = "pill") { TimerAndNudgesSection(state, actions) }
            item(key = "stats") { MiniStats(state) }
        }
    }
}

@Composable
private fun Header(state: AppDetailUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceS)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.spaceL)) {
            AppIcon(state.packageName, size = Dimens.appIconLarge)
            Text(
                text = rememberAppLabel(state.packageName),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
        }
        Text(text = durationText(state.todayMillis), style = MaterialTheme.typography.displayMedium)
        Text(
            text = stringResource(R.string.detail_today_status, limitStatusText(state.decision, state.settings)),
            style = MaterialTheme.typography.titleSmall,
            color = limitStatusColor(state.decision, state.settings),
        )
    }
}

@Composable
private fun MiniStats(state: AppDetailUiState) {
    val locale = Locale.getDefault()
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceS)) {
        SectionHeader(title = stringResource(R.string.detail_stats_title))
        UnscrollCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(Dimens.spaceXl),
                verticalArrangement = Arrangement.spacedBy(Dimens.spaceL),
            ) {
                MiniBarChart(
                    values = state.week.map { it.totalMillis },
                    labels = state.week.map { it.date.dayOfWeek.getDisplayName(TextStyle.NARROW, locale) },
                    description = stringResource(
                        R.string.detail_stats_description,
                        durationText(state.week.sumOf { it.totalMillis }),
                    ),
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.spaceL)) {
                    StatValue(
                        value = state.weekOpens.toString(),
                        label = stringResource(R.string.detail_stats_opens),
                        modifier = Modifier.weight(1f),
                    )
                    StatValue(
                        value = durationText(state.weekAverageMillis),
                        label = stringResource(R.string.detail_stats_average),
                        modifier = Modifier.weight(1f),
                    )
                    StatValue(
                        value = durationText(state.weekLongestMillis),
                        label = stringResource(R.string.stats_longest),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun AppDetailPreview() {
    val today = LocalDate.of(2026, 10, 6)
    UnscrollTheme {
        AppDetailContent(
            state = AppDetailUiState(
                packageName = TrackedApps.INSTAGRAM,
                isLoading = false,
                settings = LimitSettings(dailyLimitMinutes = 60, swipeLimit = 100),
                decision = BlockDecision.Allowed(remainingMillis = 18 * 60_000L),
                todayMillis = 42 * 60_000L,
                scrollCountingActive = true,
                week = List(7) { DayUsage(today.minusDays(6L - it), (20 + it * 7) * 60_000L) },
                weekOpens = 61,
                weekAverageMillis = 6 * 60_000L,
            ),
            actions = DetailActions(),
            onBack = {},
        )
    }
}
