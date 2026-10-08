package com.unscroll.app.ui.plus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unscroll.app.data.plus.TrackedAppsRepository
import com.unscroll.app.domain.insights.TimeRange
import com.unscroll.app.domain.insights.UsageDataSource
import com.unscroll.app.domain.plus.FreeTier
import com.unscroll.app.domain.plus.FreeTierRules
import com.unscroll.app.domain.time.Clock
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One app on the pick screen, with its time in the last 30 days. */
data class PickApp(val packageName: String, val recentMillis: Long)

data class PickAppsUiState(
    val isLoading: Boolean = true,
    val apps: List<PickApp> = emptyList(),
    val selection: List<String> = emptyList(),
    val freeApps: Int = FreeTier.FREE_APPS,
) {
    val canConfirm: Boolean get() = FreeTierRules.canConfirm(selection, apps.map { it.packageName }, freeApps)
}

/** "Pick your free app": first launch, more apps than the free tier, or Settings › Change. */
@HiltViewModel
class PickAppsViewModel @Inject constructor(
    private val trackedApps: TrackedAppsRepository,
    private val usage: UsageDataSource,
    private val clock: Clock,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PickAppsUiState())
    val uiState: StateFlow<PickAppsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val state = trackedApps.state.first()
            val now = clock.now()
            val recent = usage.appTotals(TimeRange(now - RECENT_MILLIS, now), now)
            val mostUsed = trackedApps.mostUsed()
            // Already picked (Settings › Change): start from the current choice; else the most used.
            val selection = if (state.needsPick || state.isPlus) {
                FreeTierRules.suggested(state.apps, mostUsed)
            } else {
                state.apps.filter { it in state.active }
            }
            _uiState.value = PickAppsUiState(
                isLoading = false,
                apps = state.apps.map { PickApp(it, recent[it] ?: 0L) },
                selection = selection,
            )
        }
    }

    fun toggle(packageName: String) {
        _uiState.update { it.copy(selection = FreeTierRules.toggle(it.selection, packageName, it.freeApps)) }
    }

    /** Saves the choice; the others are paused (history and settings kept). */
    fun confirm(onDone: () -> Unit) {
        val state = _uiState.value
        if (!state.canConfirm) return
        viewModelScope.launch {
            trackedApps.pick(state.selection)
            onDone()
        }
    }

    private companion object {
        const val RECENT_MILLIS = 30 * 24 * 60 * 60 * 1000L
    }
}
