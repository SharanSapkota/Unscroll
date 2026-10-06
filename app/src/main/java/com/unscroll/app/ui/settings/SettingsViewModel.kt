package com.unscroll.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unscroll.app.data.blocking.BlockingPreferences
import com.unscroll.app.data.friction.QuietHoursPreferences
import com.unscroll.app.data.overlay.OverlayPreferences
import com.unscroll.app.data.permission.PermissionRepository
import com.unscroll.app.data.sample.SampleDataSeeder
import com.unscroll.app.domain.blocking.BlockingSettings
import com.unscroll.app.domain.blocking.FrictionMode
import com.unscroll.app.domain.friction.QuietHours
import com.unscroll.app.domain.overlay.ColorThresholds
import com.unscroll.app.domain.overlay.OverlaySettings
import com.unscroll.app.domain.overlay.PillSize
import com.unscroll.app.domain.permission.AppPermission
import com.unscroll.app.domain.time.Clock
import com.unscroll.app.service.TrackingController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val trackingController: TrackingController,
    private val overlayPreferences: OverlayPreferences,
    private val sampleDataSeeder: SampleDataSeeder,
    private val blockingPreferences: BlockingPreferences,
    private val clock: Clock,
    private val quietHoursPreferences: QuietHoursPreferences,
    permissionRepository: PermissionRepository,
) : ViewModel() {

    val quietHours: StateFlow<QuietHours> = quietHoursPreferences.quietHours
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), QuietHours())

    fun setQuietHours(transform: (QuietHours) -> QuietHours) {
        viewModelScope.launch { quietHoursPreferences.save(transform(quietHoursPreferences.current())) }
    }

    /** Friction settings, with a due pending change applied first. */
    val blockingSettings: StateFlow<BlockingSettings> = blockingPreferences.settings
        .map { blockingPreferences.current(clock.now()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BlockingSettings())

    /** Weaker friction waits out the current cooldown (see FrictionPolicy). */
    fun setFrictionMode(mode: FrictionMode) {
        viewModelScope.launch {
            val now = clock.now()
            blockingPreferences.request(mode, blockingPreferences.current(now).cooldownMinutes, now)
        }
    }

    fun setCooldownMinutes(minutes: Int) {
        viewModelScope.launch {
            val now = clock.now()
            blockingPreferences.request(blockingPreferences.current(now).frictionMode, minutes, now)
        }
    }

    fun cancelPendingFriction() {
        viewModelScope.launch { blockingPreferences.cancelPending(clock.now()) }
    }

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

    fun setShowSwipes(show: Boolean) {
        viewModelScope.launch { overlayPreferences.setShowSwipes(show) }
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
