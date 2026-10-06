package com.unscroll.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unscroll.app.service.TrackingController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val trackingController: TrackingController,
) : ViewModel() {

    val trackingEnabled: StateFlow<Boolean> = trackingController.trackingEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setTrackingEnabled(enabled: Boolean) {
        viewModelScope.launch { trackingController.setTrackingEnabled(enabled) }
    }
}
