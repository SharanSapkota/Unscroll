package com.unscroll.app.ui.scroll

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unscroll.app.data.scroll.ScrollCountingRepository
import com.unscroll.app.domain.scroll.ScrollConsent
import com.unscroll.app.domain.scroll.ScrollCountingRules
import com.unscroll.app.domain.scroll.ScrollCountingStatus
import com.unscroll.app.domain.scroll.SwipeBanner
import com.unscroll.app.domain.time.Clock
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Status and actions for the optional scroll counting: Settings, the disclosure, the onboarding
 * step and Home's banners.
 */
@HiltViewModel
class ScrollCountingViewModel @Inject constructor(
    private val repository: ScrollCountingRepository,
    private val clock: Clock,
) : ViewModel() {

    val status: StateFlow<ScrollCountingStatus> = repository.status
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScrollCountingStatus.OFF)

    val consent: StateFlow<ScrollConsent> = repository.record.map { it.consent }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScrollConsent.NOT_ASKED)

    /** Home's banner: set up (never enabled), finish consent, or switched off by the phone. */
    val banner: StateFlow<SwipeBanner> = combine(repository.status, repository.record) { status, record ->
        ScrollCountingRules.banner(
            status = status,
            everEnabled = record.countingSince != null,
            dismissedAt = record.setupBannerDismissedAt,
            now = clock.now(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SwipeBanner.NONE)

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

    /** "Not now" on Home's set-up banner: hidden for 7 days. */
    fun dismissBanner() {
        viewModelScope.launch { repository.dismissSetupBanner() }
    }

    /** Accessibility settings give no callback; re-check when the user comes back. */
    fun refresh() = repository.refresh()
}
