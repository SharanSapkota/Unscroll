package com.unscroll.app.ui.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unscroll.app.data.blocking.LimitRepository
import com.unscroll.app.domain.blocking.AppLimit
import com.unscroll.app.domain.blocking.BlockDecision
import com.unscroll.app.domain.blocking.BlockEvaluator
import com.unscroll.app.domain.blocking.LimitSettings
import com.unscroll.app.domain.insights.TimeRange
import com.unscroll.app.domain.insights.UsageDataSource
import com.unscroll.app.domain.insights.localDate
import com.unscroll.app.domain.insights.startOfDay
import com.unscroll.app.domain.plus.AppTrackingStatus
import com.unscroll.app.domain.plus.TrackedAppsSource
import com.unscroll.app.domain.plus.TrackedAppsState
import com.unscroll.app.domain.time.Clock
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One row on the Apps tab. */
data class AppRowState(
    val packageName: String,
    val settings: LimitSettings,
    val decision: BlockDecision,
    val todayMillis: Long,
    /**
     * ACTIVE, or not tracked (history and settings kept), greyed out: LOCKED (free tier: a lock,
     * tap opens Plus), PAUSED (by the user) or NOT_INSTALLED.
     */
    val status: AppTrackingStatus = AppTrackingStatus.ACTIVE,
    /** The stored name, for apps that aren't installed any more. */
    val label: String? = null,
) {
    val tracked: Boolean get() = status == AppTrackingStatus.ACTIVE
}

data class AppsUiState(
    val isLoading: Boolean = true,
    val apps: List<AppRowState> = emptyList(),
) {
    private val activeApps: List<AppRowState> get() = apps.filter { it.tracked }

    /** "Block all" turns into "Unblock all" once every active app is blocked. */
    val allBlocked: Boolean get() = activeApps.isNotEmpty() && activeApps.all { it.settings.blockedAlways }
}

/** The Apps tab: a control list with one Block switch per app. Everything else is in App detail. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AppsViewModel @Inject constructor(
    private val trackedApps: TrackedAppsSource,
    private val limits: LimitRepository,
    private val usage: UsageDataSource,
    private val evaluator: BlockEvaluator,
    private val clock: Clock,
) : ViewModel() {

    /** Ticks while the tab is visible, so "min left" stays current. */
    private val ticks = flow {
        while (true) {
            emit(Unit)
            delay(TICK_MILLIS)
        }
    }

    val uiState: StateFlow<AppsUiState> = combine(
        limits.observeLimits(),
        trackedApps.state,
        usage.observeChanges(),
        ticks,
    ) { stored, apps, _, _ -> stored to apps }
        .mapLatest { (stored, apps) -> build(stored, apps) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppsUiState())

    /** Changes apply at once (no cooldown). */
    fun setBlocked(packageName: String, blocked: Boolean) {
        viewModelScope.launch { limits.updateLimit(packageName) { it.copy(blockedAlways = blocked) } }
    }

    /** "Block all" / "Unblock all" for every active app shown (paused apps keep their settings). */
    fun setAllBlocked(blocked: Boolean) {
        viewModelScope.launch {
            trackedApps.state.first().active.forEach { packageName ->
                limits.updateLimit(packageName) { it.copy(blockedAlways = blocked) }
            }
        }
    }

    private suspend fun build(stored: Map<String, AppLimit>, apps: TrackedAppsState): AppsUiState {
        val now = clock.now()
        val zone = ZoneId.systemDefault()
        val todayStart = startOfDay(localDate(now, zone), zone)
        val usedToday = usage.appTotals(TimeRange(todayStart, now), now)
        val rows = apps.entries.map { entry ->
            val packageName = entry.packageName
            val settings = (stored[packageName] ?: AppLimit(packageName)).settings
            val used = usedToday[packageName] ?: 0L
            AppRowState(
                packageName = packageName,
                settings = settings,
                decision = evaluator.evaluate(
                    settings = settings,
                    usedTodayMillis = used,
                    extensionUntil = limits.activeExtensionUntil(packageName, now),
                    now = now,
                    zone = zone,
                    packageName = packageName,
                ),
                todayMillis = used,
                status = apps.statusOf(packageName),
                label = entry.label,
            )
        }
        return AppsUiState(isLoading = false, apps = rows)
    }

    private companion object {
        const val TICK_MILLIS = 15_000L
    }
}
