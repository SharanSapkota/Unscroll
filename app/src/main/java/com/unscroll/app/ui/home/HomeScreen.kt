package com.unscroll.app.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.unscroll.app.R
import com.unscroll.app.domain.fox.FoxMood
import com.unscroll.app.domain.goals.Streak
import com.unscroll.app.domain.insights.AppUsage
import com.unscroll.app.domain.insights.DayUsage
import com.unscroll.app.domain.insights.GetHoursInvestedUseCase
import com.unscroll.app.domain.insights.PeriodUsage
import com.unscroll.app.domain.insights.Trend
import com.unscroll.app.domain.insights.UsagePeriod
import com.unscroll.app.domain.insights.UsageSummary
import com.unscroll.app.domain.insights.WeekComparison
import com.unscroll.app.domain.permission.AppPermission
import com.unscroll.app.domain.tracking.DefaultTrackedApps
import com.unscroll.app.domain.tracking.TrackingStatus
import com.unscroll.app.ui.components.AppTile
import com.unscroll.app.ui.components.EmptyState
import com.unscroll.app.ui.components.LoadingPlaceholder
import com.unscroll.app.ui.components.ProgressLevel
import com.unscroll.app.ui.components.ProgressPill
import com.unscroll.app.ui.components.SectionHeader
import com.unscroll.app.ui.components.SegmentedControl
import com.unscroll.app.ui.durationText
import com.unscroll.app.ui.fox.FoxCorner
import com.unscroll.app.ui.plus.PlusEndedBanner
import com.unscroll.app.ui.scroll.ScrollCountingBanner
import com.unscroll.app.ui.theme.Dimens
import com.unscroll.app.ui.theme.Motion
import com.unscroll.app.ui.theme.UnscrollTheme
import com.unscroll.app.util.openSettings
import java.time.LocalDate

@Composable
fun HomeScreen(
    onOpenApp: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    HomeContent(
        state = uiState,
        actions = HomeActions(
            onPeriodSelected = viewModel::selectPeriod,
            onTrackingToggle = viewModel::setTrackingEnabled,
            onOpenApp = onOpenApp,
            onFixPermission = { context.openSettings(it.settingsIntent(context)) },
        ),
        modifier = modifier,
        banner = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceL)) {
                PlusEndedBanner()
                ScrollCountingBanner()
            }
        },
        // The fox peeks in from the top corner; tap it for a message.
        fox = { FoxCorner(size = Dimens.foxHome) },
    )
}

internal class HomeActions(
    val onPeriodSelected: (UsagePeriod) -> Unit = {},
    val onTrackingToggle: (Boolean) -> Unit = {},
    val onOpenApp: (String) -> Unit = {},
    val onFixPermission: (AppPermission) -> Unit = {},
)

@Composable
internal fun HomeContent(
    state: HomeUiState,
    actions: HomeActions,
    modifier: Modifier = Modifier,
    banner: @Composable () -> Unit = {},
    fox: @Composable () -> Unit = {},
) {
    if (state.isLoading) {
        LoadingPlaceholder(modifier = modifier.fillMaxSize())
        return
    }
    var insightsOpen by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Dimens.screenPadding, vertical = Dimens.spaceL),
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceXl),
    ) {
        item(key = "top") { TopRow(state, actions) }
        // Tracking is on but can't run: say so, with one tap to the missing permission.
        state.permissionToFix?.let { permission ->
            item(key = "paused") { TrackingPausedBanner(permission, actions.onFixPermission) }
        }
        // Only shows when the system switched scroll counting off after it had been working.
        item(key = "banner") { banner() }
        if (!state.hasAnyData) {
            item(key = "empty") {
                EmptyState(
                    icon = R.drawable.ic_hourglass,
                    text = stringResource(if (state.trackingEnabled) R.string.home_empty else R.string.home_empty_off),
                    mood = if (state.trackingEnabled) FoxMood.HAPPY else FoxMood.SLEEPY,
                )
            }
            return@LazyColumn
        }
        item(key = "hero") { Hero(state, fox) }
        item(key = "period") {
            SegmentedControl(
                options = UsagePeriod.entries,
                selected = state.period,
                label = { stringResource(it.labelRes) },
                onSelect = actions.onPeriodSelected,
            )
        }
        item(key = "apps") { AppTiles(state, actions.onOpenApp) }
        item(key = "hours") { HoursInvestedCard(state.hoursInvested) }
        item(key = "insights") {
            InsightsHeader(open = insightsOpen, onToggle = { insightsOpen = !insightsOpen })
        }
        item(key = "insightsBody") {
            AnimatedVisibility(
                visible = insightsOpen,
                enter = fadeIn(tween(Motion.MEDIUM)) + expandVertically(tween(Motion.MEDIUM)),
                exit = fadeOut(tween(Motion.SHORT)) + shrinkVertically(tween(Motion.SHORT)),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceL)) {
                    state.periodUsage?.let {
                        SessionsCard(it.combined)
                        HeatmapCard(it.hourly)
                    }
                    state.weekComparison?.let { WeekCard(it) }
                    state.scrollStats?.let { SwipesCard(it) }
                    state.weeklyReport?.let { WeeklyReportCard(it) }
                }
            }
        }
    }
}

@Composable
private fun TopRow(state: HomeUiState, actions: HomeActions) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        // On: tap to turn off. Off: tap to turn back on. Paused: tap to fix the permission.
        when (state.trackingStatus) {
            TrackingStatus.ACTIVE -> ProgressPill(
                text = stringResource(R.string.home_tracking_on),
                level = ProgressLevel.GOOD,
                onClick = { actions.onTrackingToggle(false) },
            )
            TrackingStatus.PAUSED -> ProgressPill(
                text = stringResource(R.string.home_tracking_paused),
                level = ProgressLevel.DANGER,
                onClick = { state.permissionToFix?.let(actions.onFixPermission) },
            )
            TrackingStatus.OFF -> ProgressPill(
                text = stringResource(R.string.home_tracking_off),
                level = ProgressLevel.DANGER,
                onClick = { actions.onTrackingToggle(true) },
            )
        }
    }
}

@Composable
private fun Hero(state: HomeUiState, fox: @Composable () -> Unit) {
    val total = state.periodUsage?.combined?.totalMillis ?: 0L
    Row(verticalAlignment = Alignment.Top) {
        HeroNumbers(state, total, Modifier.weight(1f))
        fox()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeroNumbers(state: HomeUiState, total: Long, modifier: Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Dimens.spaceS)) {
        Text(
            text = stringResource(state.period.heroLabelRes),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = durationText(total), style = MaterialTheme.typography.displayLarge)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Dimens.spaceS),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceS),
        ) {
            val trend = when (state.period) {
                UsagePeriod.TODAY -> state.todayTrend to R.string.home_trend_vs_yesterday
                UsagePeriod.WEEK -> state.weekComparison?.trend?.let { it to R.string.home_trend_vs_week }
                else -> null
            }
            trend?.let { (value, versus) ->
                value.percentChange?.let { TrendPill(percent = it, direction = value.direction, versus = versus) }
            }
            if (state.period == UsagePeriod.TODAY) state.goal?.let { GoalPill(it) }
        }
    }
}

@Composable
private fun AppTiles(state: HomeUiState, onOpenApp: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceS)) {
        SectionHeader(title = stringResource(R.string.home_apps_title))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceM)) {
            items(state.apps, key = { it.packageName }) { tile ->
                AppTile(
                    packageName = tile.packageName,
                    time = durationText(tile.millis),
                    swipes = tile.swipes?.let { pluralStringResource(R.plurals.home_swipes, it, it) },
                    status = if (tile.blocked) stringResource(R.string.status_blocked) else null,
                    limitProgress = tile.limitProgress,
                    onClick = { onOpenApp(tile.packageName) },
                )
            }
        }
    }
}

@Composable
private fun InsightsHeader(open: Boolean, onToggle: () -> Unit) {
    val rotation by animateFloatAsState(if (open) HALF_TURN else 0f, tween(Motion.MEDIUM), label = "chevron")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.touchTarget)
            .clickable(role = Role.Button, onClick = onToggle)
            .padding(horizontal = Dimens.spaceXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.home_insights),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        Icon(
            painter = painterResource(R.drawable.ic_expand_more),
            contentDescription = stringResource(if (open) R.string.cd_collapse else R.string.cd_expand),
            modifier = Modifier
                .size(Dimens.icon)
                .rotate(rotation),
        )
    }
}

private val UsagePeriod.labelRes: Int
    get() = when (this) {
        UsagePeriod.TODAY -> R.string.period_day
        UsagePeriod.WEEK -> R.string.period_week
        UsagePeriod.MONTH -> R.string.period_month
        UsagePeriod.ALL_TIME -> R.string.period_all
    }

private val UsagePeriod.heroLabelRes: Int
    get() = when (this) {
        UsagePeriod.TODAY -> R.string.home_hero_today
        UsagePeriod.WEEK -> R.string.home_hero_week
        UsagePeriod.MONTH -> R.string.home_hero_month
        UsagePeriod.ALL_TIME -> R.string.home_hero_all
    }

private const val HALF_TURN = 180f

@PreviewLightDark
@Composable
private fun HomePreview() {
    val today = LocalDate.of(2026, 10, 6)
    val usage = PeriodUsage(
        period = UsagePeriod.TODAY,
        combined = UsageSummary(3_900_000, 14, 278_000, 1_200_000),
        apps = listOf(
            AppUsage(DefaultTrackedApps.INSTAGRAM, UsageSummary(2_700_000, 9, 300_000, 1_200_000)),
            AppUsage(DefaultTrackedApps.TIKTOK, UsageSummary(1_200_000, 5, 240_000, 600_000)),
        ),
        hourly = List(24) { hour -> if (hour in 8..23) hour * 20_000L else 0L },
    )
    UnscrollTheme {
        HomeContent(
            state = HomeUiState(
                isLoading = false,
                todayTrend = Trend(currentMillis = 3_900_000, previousMillis = 4_700_000),
                periodUsage = usage,
                weekComparison = WeekComparison(
                    thisWeek = List(7) { DayUsage(today.minusDays(6L - it), 3_000_000L + it * 200_000L) },
                    lastWeek = List(7) { DayUsage(today.minusDays(13L - it), 3_600_000L) },
                    trend = Trend(23_100_000, 25_200_000),
                ),
                hoursInvested = GetHoursInvestedUseCase.calculate(212 * 3_600_000L),
                goal = GoalProgress(60, 3_900_000, Streak(current = 3, best = 9, todayOnTrack = false)),
                apps = listOf(
                    AppTileState(DefaultTrackedApps.INSTAGRAM, 2_700_000, swipes = 240, limitProgress = 0.9f, blocked = false),
                    AppTileState(DefaultTrackedApps.TIKTOK, 1_200_000, swipes = 80, limitProgress = null, blocked = false),
                    AppTileState(DefaultTrackedApps.FACEBOOK, 0, swipes = 0, limitProgress = null, blocked = true),
                ),
            ),
            actions = HomeActions(),
        )
    }
}

@PreviewLightDark
@Composable
private fun HomeEmptyPreview() {
    UnscrollTheme {
        HomeContent(state = HomeUiState(isLoading = false), actions = HomeActions())
    }
}
