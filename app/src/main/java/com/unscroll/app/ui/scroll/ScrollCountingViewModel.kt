package com.unscroll.app.ui.scroll

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unscroll.app.data.scroll.ScrollCountingRepository
import com.unscroll.app.domain.scroll.ScrollConsent
import com.unscroll.app.domain.scroll.ScrollCountingStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Status and actions for the optional scroll counting: Settings, disclosure and Dashboard banner. */
@HiltViewModel
class ScrollCountingViewModel @Inject constructor(
    private val repository: ScrollCountingRepository,
) : ViewModel() {

    val status: StateFlow<ScrollCountingStatus> = repository.status
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScrollCountingStatus.OFF)

    /** "I agree" on the disclosure screen. The caller then opens Accessibility settings. */
    fun agree() {
        viewModelScope.launch { repository.setConsent(ScrollConsent.AGREED) }
    }

    /** "No thanks": the app keeps working exactly as before. */
    fun decline() {
        viewModelScope.launch { repository.setConsent(ScrollConsent.DECLINED) }
    }

    /** Withdraws consent and switches the service off if it is running. */
    fun turnOff() {
        viewModelScope.launch { repository.turnOff() }
    }

    /** Accessibility settings give no callback; re-check when the user comes back. */
    fun refresh() = repository.refresh()
}
