package com.unscroll.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unscroll.app.data.overlay.OverlayPreferences
import com.unscroll.app.data.permission.PermissionRepository
import com.unscroll.app.data.sample.SampleDataSeeder
import com.unscroll.app.domain.overlay.ColorThresholds
import com.unscroll.app.domain.overlay.OverlaySettings
import com.unscroll.app.domain.overlay.PillSize
import com.unscroll.app.domain.permission.AppPermission
import com.unscroll.app.service.TrackingController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val trackingController: TrackingController,
    private val overlayPreferences: OverlayPreferences,
    private val sampleDataSeeder: SampleDataSeeder,
    permissionRepository: PermissionRepository,
) : ViewModel() {

    val trackingEnabled: StateFlow<Boolean> = trackingController.trackingEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val overlaySettings: StateFlow<OverlaySettings> = overlayPreferences.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OverlaySettings())

    /** False if "Display over other apps" was revoked; the pill can't show without it. */
    val canDrawOverlays: StateFlow<Boolean> = permissionRepository.isGranted(AppPermission.OVERLAY)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    private val _sampleSessionsAdded = MutableStateFlow<Int?>(null)

    /** How many sample sessions the last "Insert sample data" tap added, if any. */
    val sampleSessionsAdded: StateFlow<Int?> = _sampleSessionsAdded.asStateFlow()

    private val _positionReset = MutableStateFlow(false)

    /** True after "Reset position" was tapped, to confirm it in the UI. */
    val positionReset: StateFlow<Boolean> = _positionReset.asStateFlow()

    fun setTrackingEnabled(enabled: Boolean) {
        viewModelScope.launch { trackingController.setTrackingEnabled(enabled) }
    }

    fun setOverlayEnabled(enabled: Boolean) {
        viewModelScope.launch { overlayPreferences.setEnabled(enabled) }
    }

    fun setShowTodayTotal(show: Boolean) {
        viewModelScope.launch { overlayPreferences.setShowTodayTotal(show) }
    }

    fun setWarningMinutes(minutes: Int) {
        val current = overlaySettings.value.thresholds
        // Pushing warning past danger drags danger along with it.
        val danger = maxOf(current.dangerAfterMinutes, minutes + 1)
        viewModelScope.launch { overlayPreferences.setThresholds(ColorThresholds(minutes, danger)) }
    }

    fun setDangerMinutes(minutes: Int) {
        val current = overlaySettings.value.thresholds
        val warning = minOf(current.warningAfterMinutes, minutes - 1)
        viewModelScope.launch { overlayPreferences.setThresholds(ColorThresholds(warning, minutes)) }
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
