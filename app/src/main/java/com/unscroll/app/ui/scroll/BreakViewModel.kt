package com.unscroll.app.ui.scroll

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unscroll.app.domain.scroll.SwipeBreakTracker
import com.unscroll.app.service.FrictionCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class BreakOutcome { KEEP_SCROLLING, DONE }

data class BreakUiState(
    val packageName: String,
    val swipes: Int,
    /** Time in the app this session, up to when the break screen opened. */
    val sessionMillis: Long,
    val secondsLeft: Int,
    /** Set once the user decided; the activity then leaves. */
    val outcome: BreakOutcome? = null,
) {
    /** The buttons only appear once the countdown is over. */
    val canDecide: Boolean get() = secondsLeft <= 0 && outcome == null
}

@HiltViewModel
class BreakViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val coordinator: FrictionCoordinator,
) : ViewModel() {

    private val packageName: String = savedStateHandle.get<String>(BreakActivity.EXTRA_PACKAGE).orEmpty()
    private val sessionId: Long = savedStateHandle.get<Long>(BreakActivity.EXTRA_SESSION_ID) ?: 0L
    private val swipes: Int = savedStateHandle.get<Int>(BreakActivity.EXTRA_SWIPES) ?: 0

    private val _uiState = MutableStateFlow(
        BreakUiState(
            packageName = packageName,
            swipes = swipes,
            sessionMillis = savedStateHandle.get<Long>(BreakActivity.EXTRA_SESSION_MILLIS) ?: 0L,
            secondsLeft = savedStateHandle.get<Int>(KEY_SECONDS_LEFT) ?: SwipeBreakTracker.BREAK_SECONDS,
        ),
    )
    val uiState: StateFlow<BreakUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            while (_uiState.value.secondsLeft > 0) {
                delay(1_000)
                _uiState.update { it.copy(secondsLeft = it.secondsLeft - 1) }
                savedStateHandle[KEY_SECONDS_LEFT] = _uiState.value.secondsLeft
            }
        }
    }

    /** "Keep scrolling": back to the app; the next break comes after another N swipes. */
    fun keepScrolling() {
        if (!_uiState.value.canDecide) return
        coordinator.onSwipeBreakKeepScrolling(packageName, sessionId, swipes)
        _uiState.update { it.copy(outcome = BreakOutcome.KEEP_SCROLLING) }
    }

    /** "I'm done": go home. */
    fun done() {
        if (!_uiState.value.canDecide) return
        _uiState.update { it.copy(outcome = BreakOutcome.DONE) }
    }

    /** Left another way (Home, Recents): that's fine, the user is never trapped. */
    fun leftWithoutDeciding() {
        if (_uiState.value.outcome == null) _uiState.update { it.copy(outcome = BreakOutcome.DONE) }
    }

    private companion object {
        const val KEY_SECONDS_LEFT = "break_seconds_left"
    }
}
