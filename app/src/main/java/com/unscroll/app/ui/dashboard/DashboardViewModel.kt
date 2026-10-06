package com.unscroll.app.ui.dashboard

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unscroll.app.data.friction.FrictionRepository
import com.unscroll.app.data.friction.PauseStat
import com.unscroll.app.data.scroll.ScrollCountingRepository
import com.unscroll.app.data.tracking.TrackingPreferences
import com.unscroll.app.domain.insights.GetHoursInvestedUseCase
import com.unscroll.app.domain.insights.GetPeriodUsageUseCase
import com.unscroll.app.domain.insights.GetTodayTrendUseCase
import com.unscroll.app.domain.insights.GetWeekComparisonUseCase
import com.unscroll.app.domain.insights.HoursInvested
import com.unscroll.app.domain.insights.PeriodUsage
import com.unscroll.app.domain.insights.Trend
import com.unscroll.app.domain.insights.UsageDataSource
import com.unscroll.app.domain.insights.UsagePeriod
import com.unscroll.app.domain.insights.WeekComparison
import com.unscroll.app.domain.insights.localDate
import com.unscroll.app.domain.insights.startOfDay
import com.unscroll.app.domain.scroll.GetScrollStatsUseCase
import com.unscroll.app.domain.scroll.ScrollStats
import com.unscroll.app.domain.time.Clock
import com.unscroll.app.service.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

data class DashboardUiState(
    val isLoading: Boolean = true,
    val period: UsagePeriod = UsagePeriod.TODAY,
    val trackingEnabled: Boolean = true,
    val todayTrend: Trend = Trend(0, 0),
    val periodUsage: PeriodUsage? = null,
    val weekComparison: WeekComparison? = null,
    val hoursInvested: HoursInvested = GetHoursInvestedUseCase.calculate(0),
    /** Today's pause screens per app, most skipped first. */
    val pauseStats: List<PauseStat> = emptyList(),
    /** Swipe stats for the period, or null if scroll counting was never switched on (cards hidden). */
    val scrollStats: ScrollStats? = null,
) {
    /** False until the first session has been logged. */
    val hasAnyData: Boolean get() = hoursInvested.totalMillis > 0
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val getPeriodUsage: GetPeriodUsageUseCase,
    private val getTodayTrend: GetTodayTrendUseCase,
    private val getWeekComparison: GetWeekComparisonUseCase,
    private val getHoursInvested: GetHoursInvestedUseCase,
    usageDataSource: UsageDataSource,
    sessionManager: SessionManager,
    trackingPreferences: TrackingPreferences,
    private val friction: FrictionRepository,
    private val getScrollStats: GetScrollStatsUseCase,
    scrollCounting: ScrollCountingRepository,
    private val clock: Clock,
) : ViewModel() {

    private val selectedPeriod = savedStateHandle
        .getStateFlow(KEY_PERIOD, UsagePeriod.TODAY.name)
        .map { UsagePeriod.valueOf(it) }

    /**
     * Refresh once per second while a session is running, so totals tick live. Otherwise once a
     * minute, which is enough to roll over at midnight. Only runs while the UI collects uiState.
     */
    private val refreshTicks: Flow<Unit> = sessionManager.currentSession
        .map { it != null }
        .distinctUntilChanged()
        .flatMapLatest { sessionOpen ->
            ticker(if (sessionOpen) LIVE_REFRESH_MILLIS else IDLE_REFRESH_MILLIS)
        }

    val uiState: StateFlow<DashboardUiState> = combine(
        selectedPeriod,
        trackingPreferences.trackingEnabled,
        usageDataSource.observeChanges(),
        refreshTicks,
        scrollCounting.countingSince,
    ) { period, trackingEnabled, _, _, countingSince -> LoadRequest(period, trackingEnabled, countingSince) }
        .mapLatest { load(it.period, it.trackingEnabled, it.countingSince) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    fun selectPeriod(period: UsagePeriod) {
        savedStateHandle[KEY_PERIOD] = period.name
    }

    private suspend fun load(period: UsagePeriod, trackingEnabled: Boolean, countingSince: Long?): DashboardUiState {
        val now = clock.now()
        val zone = ZoneId.systemDefault()
        return DashboardUiState(
            isLoading = false,
            period = period,
            trackingEnabled = trackingEnabled,
            todayTrend = getTodayTrend(now, zone),
            periodUsage = getPeriodUsage(period, now, zone),
            weekComparison = getWeekComparison(now, zone),
            hoursInvested = getHoursInvested(now),
            pauseStats = friction.observePauseStatsSince(startOfDay(localDate(now, zone), zone)).first(),
            scrollStats = countingSince?.let { getScrollStats(period, it, now, zone) },
        )
    }

    private data class LoadRequest(val period: UsagePeriod, val trackingEnabled: Boolean, val countingSince: Long?)

    private fun ticker(intervalMillis: Long): Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(intervalMillis)
        }
    }

    private companion object {
        const val KEY_PERIOD = "dashboard_period"
        const val LIVE_REFRESH_MILLIS = 1_000L
        const val IDLE_REFRESH_MILLIS = 60_000L
    }
}
