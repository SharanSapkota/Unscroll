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
import com.unscroll.app.domain.time.Clock
import com.unscroll.app.util.InstalledTrackedApps
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
)

data class AppsUiState(
    val isLoading: Boolean = true,
    val apps: List<AppRowState> = emptyList(),
) {
    /** "Block all" turns into "Unblock all" once every app is blocked. */
    val allBlocked: Boolean get() = apps.isNotEmpty() && apps.all { it.settings.blockedAlways }
}

/** The Apps tab: a control list with one Block switch per app. Everything else is in App detail. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AppsViewModel @Inject constructor(
    private val installedApps: InstalledTrackedApps,
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
        usage.observeChanges(),
        ticks,
    ) { stored, _, _ -> stored }
        .mapLatest { build(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppsUiState())

    /** Changes apply at once (no cooldown). */
    fun setBlocked(packageName: String, blocked: Boolean) {
        viewModelScope.launch { limits.updateLimit(packageName) { it.copy(blockedAlways = blocked) } }
    }

    /** "Block all" / "Unblock all" for every tracked app shown. */
    fun setAllBlocked(blocked: Boolean) {
        viewModelScope.launch {
            installedApps.packages().forEach { packageName ->
                limits.updateLimit(packageName) { it.copy(blockedAlways = blocked) }
            }
        }
    }

    private suspend fun build(stored: Map<String, AppLimit>): AppsUiState {
        val now = clock.now()
        val zone = ZoneId.systemDefault()
        val todayStart = startOfDay(localDate(now, zone), zone)
        val usedToday = usage.appTotals(TimeRange(todayStart, now), now)
        val rows = installedApps.packages().map { packageName ->
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
                ),
                todayMillis = used,
            )
        }
        return AppsUiState(isLoading = false, apps = rows)
    }

    private companion object {
        const val TICK_MILLIS = 15_000L
    }
}
