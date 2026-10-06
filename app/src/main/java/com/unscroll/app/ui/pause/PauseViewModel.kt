package com.unscroll.app.ui.pause

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unscroll.app.data.friction.FrictionRepository
import com.unscroll.app.data.friction.PauseOutcome
import com.unscroll.app.domain.friction.FrictionSettings
import com.unscroll.app.service.FrictionCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.random.Random
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PauseUiState(
    val packageName: String,
    val secondsLeft: Int,
    val promptIndex: Int,
    /** Set once the user decided; the activity then leaves. */
    val outcome: PauseOutcome? = null,
) {
    /** "Continue" and "Never mind" only appear once the countdown is over. */
    val canDecide: Boolean get() = secondsLeft <= 0 && outcome == null
}

@HiltViewModel
class PauseViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val friction: FrictionRepository,
    private val coordinator: FrictionCoordinator,
) : ViewModel() {

    private val packageName: String = savedStateHandle.get<String>(PauseActivity.EXTRA_PACKAGE).orEmpty()
    private val shownAt: Long = savedStateHandle.get<Long>(PauseActivity.EXTRA_SHOWN_AT) ?: 0L

    private val _uiState = MutableStateFlow(
        PauseUiState(
            packageName = packageName,
            secondsLeft = savedStateHandle.get<Int>(KEY_SECONDS_LEFT)
                ?: (savedStateHandle.get<Int>(PauseActivity.EXTRA_SECONDS) ?: FrictionSettings.DEFAULT_PAUSE_SECONDS)
                    .coerceIn(FrictionSettings.MIN_PAUSE_SECONDS, FrictionSettings.MAX_PAUSE_SECONDS),
            promptIndex = savedStateHandle.get<Int>(KEY_PROMPT) ?: Random.nextInt(PROMPT_COUNT).also {
                savedStateHandle[KEY_PROMPT] = it
            },
        ),
    )
    val uiState: StateFlow<PauseUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            while (_uiState.value.secondsLeft > 0) {
                delay(1_000)
                _uiState.update { it.copy(secondsLeft = it.secondsLeft - 1) }
                savedStateHandle[KEY_SECONDS_LEFT] = _uiState.value.secondsLeft
            }
        }
    }

    fun continueToApp() {
        if (!_uiState.value.canDecide) return
        coordinator.onPauseContinued(packageName)
        decide(PauseOutcome.CONTINUED)
    }

    fun neverMind() {
        if (!_uiState.value.canDecide) return
        decide(PauseOutcome.ABANDONED)
    }

    /** The user left the pause screen some other way (Home, Recents): that counts as walking away. */
    fun leftWithoutDeciding() {
        if (_uiState.value.outcome != null) return
        decide(PauseOutcome.ABANDONED)
    }

    private fun decide(outcome: PauseOutcome) {
        _uiState.update { it.copy(outcome = outcome) }
        viewModelScope.launch {
            withContext(NonCancellable) { friction.logPauseOutcome(packageName, shownAt, outcome) }
        }
    }

    companion object {
        /** Must match the number of entries in R.array.pause_prompts. */
        const val PROMPT_COUNT = 8
        private const val KEY_PROMPT = "pause_prompt"
        private const val KEY_SECONDS_LEFT = "pause_seconds_left"
    }
}
