package com.unscroll.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unscroll.app.data.sample.SampleDataSeeder
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
    private val sampleDataSeeder: SampleDataSeeder,
) : ViewModel() {

    val trackingEnabled: StateFlow<Boolean> = trackingController.trackingEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _sampleSessionsAdded = MutableStateFlow<Int?>(null)

    /** How many sample sessions the last "Insert sample data" tap added, if any. */
    val sampleSessionsAdded: StateFlow<Int?> = _sampleSessionsAdded.asStateFlow()

    fun setTrackingEnabled(enabled: Boolean) {
        viewModelScope.launch { trackingController.setTrackingEnabled(enabled) }
    }

    /** Debug builds only. */
    fun insertSampleData() {
        viewModelScope.launch { _sampleSessionsAdded.value = sampleDataSeeder.seed() }
    }
}
