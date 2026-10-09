package com.unscroll.app.ui.section

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unscroll.app.data.section.SectionBlockingRepository
import com.unscroll.app.domain.scroll.ScrollConsent
import com.unscroll.app.domain.scroll.ScrollCountingStatus
import com.unscroll.app.domain.section.SectionBlockingSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Status and actions for section blocking: Settings sheet, disclosure, Home banner and inspector. */
@HiltViewModel
class SectionBlockingViewModel @Inject constructor(
    private val repository: SectionBlockingRepository,
) : ViewModel() {

    val status: StateFlow<ScrollCountingStatus> = repository.status
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScrollCountingStatus.OFF)

    val settings: StateFlow<SectionBlockingSettings> = repository.settings

    val isPlus: StateFlow<Boolean> = repository.isPlus

    /** "I agree" on the disclosure. The caller then opens Accessibility settings. */
    fun agree() {
        viewModelScope.launch { repository.setConsent(ScrollConsent.AGREED) }
    }

    /** "No thanks": everything else keeps working exactly as before. */
    fun decline() {
        viewModelScope.launch { repository.setConsent(ScrollConsent.DECLINED) }
    }

    /** Withdraws consent; the service switches itself off. */
    fun withdraw() {
        viewModelScope.launch { repository.withdraw() }
    }

    /** The kill switch: "Turn off section blocking" / turn it back on. */
    fun setTurnedOff(turnedOff: Boolean) {
        viewModelScope.launch { repository.setTurnedOff(turnedOff) }
    }

    fun setInspector(on: Boolean) {
        viewModelScope.launch { repository.setInspector(on) }
    }

    fun refresh() = repository.refresh()
}
