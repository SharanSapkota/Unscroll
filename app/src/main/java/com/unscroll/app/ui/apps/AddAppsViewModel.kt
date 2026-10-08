package com.unscroll.app.ui.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unscroll.app.data.apps.InstalledApps
import com.unscroll.app.data.plus.DebugBuild
import com.unscroll.app.data.plus.TrackedAppsRepository
import com.unscroll.app.domain.apps.AddResult
import com.unscroll.app.domain.apps.AppPicker
import com.unscroll.app.domain.apps.ExcludedApps
import com.unscroll.app.domain.apps.LaunchableApp
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One app in the picker. [added]: already in the tracked list. */
data class PickerApp(val packageName: String, val label: String, val added: Boolean)

data class AddAppsUiState(
    val isLoading: Boolean = true,
    val query: String = "",
    /** Installed popular apps matching the search. */
    val popular: List<PickerApp> = emptyList(),
    /** Every launchable app matching the search, by name. */
    val all: List<PickerApp> = emptyList(),
)

/**
 * The "+" picker: every launchable app on the phone except the excluded ones (AppExclusions),
 * loaded off the main thread. Adding goes through [TrackedAppsRepository.add], which enforces the
 * exclusions and the free-tier limit again; at the limit it asks for the paywall instead.
 */
@HiltViewModel
class AddAppsViewModel @Inject constructor(
    private val trackedApps: TrackedAppsRepository,
    private val installedApps: InstalledApps,
    private val excludedApps: ExcludedApps,
    @DebugBuild private val debugBuild: Boolean,
) : ViewModel() {

    private val candidates = MutableStateFlow<List<LaunchableApp>?>(null)
    private val query = MutableStateFlow("")

    private val _openPaywall = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emits when a free user at the limit taps "Add". */
    val openPaywall: SharedFlow<Unit> = _openPaywall.asSharedFlow()

    val uiState: StateFlow<AddAppsUiState> = combine(
        candidates,
        query,
        trackedApps.trackedApps.map { apps -> apps.mapTo(mutableSetOf()) { it.packageName } },
    ) { candidates, query, tracked ->
        if (candidates == null) return@combine AddAppsUiState(query = query)
        fun List<LaunchableApp>.toPicker() = map { PickerApp(it.packageName, it.label, it.packageName in tracked) }
        AddAppsUiState(
            isLoading = false,
            query = query,
            popular = AppPicker.popularRow(candidates, AppPicker.popular(debugBuild), query).toPicker(),
            all = AppPicker.search(candidates, query).toPicker(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AddAppsUiState())

    init {
        viewModelScope.launch {
            candidates.value = withContext(Dispatchers.Default) {
                AppPicker.candidates(installedApps.launchable(), excludedApps::isExcluded)
            }
        }
    }

    fun setQuery(value: String) {
        query.value = value
    }

    fun add(app: PickerApp) {
        viewModelScope.launch {
            if (trackedApps.add(app.packageName, app.label) == AddResult.NEEDS_PLUS) _openPaywall.tryEmit(Unit)
        }
    }
}
