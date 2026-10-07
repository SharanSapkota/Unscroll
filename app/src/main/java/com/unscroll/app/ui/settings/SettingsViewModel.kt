package com.unscroll.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unscroll.app.data.appearance.AppearancePreferences
import com.unscroll.app.data.friction.QuietHoursPreferences
import com.unscroll.app.data.overlay.OverlayPreferences
import com.unscroll.app.data.permission.PermissionRepository
import com.unscroll.app.data.sample.SampleDataSeeder
import com.unscroll.app.data.scroll.ScrollCountingRepository
import com.unscroll.app.domain.fox.FoxSettings
import com.unscroll.app.domain.friction.QuietHours
import com.unscroll.app.domain.overlay.ColorThresholds
import com.unscroll.app.domain.overlay.OverlaySettings
import com.unscroll.app.domain.overlay.PillSize
import com.unscroll.app.domain.overlay.SwipeColorThresholds
import com.unscroll.app.domain.permission.HealthItem
import com.unscroll.app.domain.permission.PermissionHealth
import com.unscroll.app.domain.permission.PermissionState
import com.unscroll.app.domain.scroll.ScrollCountingStatus
import com.unscroll.app.service.TrackingController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The permission checklist: each line and whether it is fine. */
data class PermissionHealthState(val checklist: List<Pair<HealthItem, Boolean>> = emptyList()) {
    val issues: Int get() = checklist.count { !it.second }
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val trackingController: TrackingController,
    private val overlayPreferences: OverlayPreferences,
    private val sampleDataSeeder: SampleDataSeeder,
    private val quietHoursPreferences: QuietHoursPreferences,
    private val appearancePreferences: AppearancePreferences,
    private val permissionRepository: PermissionRepository,
    scrollCounting: ScrollCountingRepository,
) : ViewModel() {

    val quietHours: StateFlow<QuietHours> = quietHoursPreferences.quietHours
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), QuietHours())

    val trackingEnabled: StateFlow<Boolean> = trackingController.trackingEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val overlaySettings: StateFlow<OverlaySettings> = overlayPreferences.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OverlaySettings())

    val dynamicColor: StateFlow<Boolean> = appearancePreferences.dynamicColor
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val fox: StateFlow<FoxSettings> = appearancePreferences.fox
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FoxSettings())

    val permissions: StateFlow<PermissionState> = permissionRepository.permissions

    /** "All set" or "Fix 2 issues", and the checklist behind it. */
    val health: StateFlow<PermissionHealthState> = combine(
        permissionRepository.permissions,
        scrollCounting.status,
    ) { permissions, scroll -> PermissionHealthState(PermissionHealth.checklist(permissions, scroll)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PermissionHealthState())

    val scrollStatus: StateFlow<ScrollCountingStatus> = scrollCounting.status
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScrollCountingStatus.OFF)

    private val _sampleSessionsAdded = MutableStateFlow<Int?>(null)

    /** How many sample sessions the last "Insert sample data" tap added, if any. */
    val sampleSessionsAdded: StateFlow<Int?> = _sampleSessionsAdded.asStateFlow()

    private val _positionReset = MutableStateFlow(false)

    /** True after "Reset position" was tapped, to confirm it in the UI. */
    val positionReset: StateFlow<Boolean> = _positionReset.asStateFlow()

    fun setQuietHours(transform: (QuietHours) -> QuietHours) {
        viewModelScope.launch { quietHoursPreferences.save(transform(quietHoursPreferences.current())) }
    }

    fun setTrackingEnabled(enabled: Boolean) {
        viewModelScope.launch { trackingController.setTrackingEnabled(enabled) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { appearancePreferences.setDynamicColor(enabled) }
    }

    fun setShowFox(show: Boolean) {
        viewModelScope.launch { appearancePreferences.setShowFox(show) }
    }

    fun setFoxMessages(enabled: Boolean) {
        viewModelScope.launch { appearancePreferences.setFoxMessages(enabled) }
    }

    /** After the user comes back from a system settings screen. */
    fun refreshPermissions() = permissionRepository.refresh()

    fun setOverlayEnabled(enabled: Boolean) {
        viewModelScope.launch { overlayPreferences.setEnabled(enabled) }
    }

    fun setShowSessionTime(show: Boolean) {
        viewModelScope.launch { overlayPreferences.setShowSessionTime(show) }
    }

    fun setShowSwipes(show: Boolean) {
        viewModelScope.launch { overlayPreferences.setShowSwipes(show) }
    }

    /** Both swipe color thresholds at once (the range slider); normalized when saved. */
    fun setSwipeThresholds(warning: Int, danger: Int) {
        viewModelScope.launch { overlayPreferences.setSwipeThresholds(SwipeColorThresholds(warning, danger)) }
    }

    /** Both time color thresholds at once (the range slider); normalized when saved. */
    fun setTimeThresholds(warningMinutes: Int, dangerMinutes: Int) {
        viewModelScope.launch { overlayPreferences.setThresholds(ColorThresholds(warningMinutes, dangerMinutes)) }
    }

    fun setPillSize(size: PillSize) {
        viewModelScope.launch { overlayPreferences.setSize(size) }
    }

    fun setOpacity(opacity: Float) {
        viewModelScope.launch { overlayPreferences.setOpacity(opacity) }
    }

    fun resetPosition() {
        viewModelScope.launch {
            overlayPreferences.resetPositions()
            _positionReset.value = true
        }
    }

    /** Debug builds only. */
    fun insertSampleData() {
        viewModelScope.launch { _sampleSessionsAdded.value = sampleDataSeeder.seed() }
    }
}
