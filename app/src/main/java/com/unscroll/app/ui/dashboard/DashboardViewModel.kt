package com.unscroll.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unscroll.app.data.session.SessionRepository
import com.unscroll.app.data.tracking.TrackingPreferences
import com.unscroll.app.domain.session.ActiveSession
import com.unscroll.app.domain.session.Session
import com.unscroll.app.service.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class DashboardUiState(
    val trackingEnabled: Boolean = false,
    val currentSession: ActiveSession? = null,
    val recentSessions: List<Session> = emptyList(),
)

/** Backs the temporary M2 debug dashboard. Replaced by the real dashboard in M3. */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    sessionManager: SessionManager,
    sessionRepository: SessionRepository,
    trackingPreferences: TrackingPreferences,
) : ViewModel() {

    val uiState: StateFlow<DashboardUiState> = combine(
        trackingPreferences.trackingEnabled,
        sessionManager.currentSession,
        sessionRepository.observeRecentSessions(RECENT_SESSION_COUNT),
    ) { trackingEnabled, currentSession, recentSessions ->
        DashboardUiState(trackingEnabled, currentSession, recentSessions)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    private companion object {
        const val RECENT_SESSION_COUNT = 20
    }
}
