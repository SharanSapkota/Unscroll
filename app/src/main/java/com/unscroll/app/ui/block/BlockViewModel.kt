package com.unscroll.app.ui.block

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unscroll.app.data.blocking.BlockingPreferences
import com.unscroll.app.data.blocking.LimitRepository
import com.unscroll.app.domain.blocking.BlockReason
import com.unscroll.app.domain.blocking.BlockingSettings
import com.unscroll.app.domain.blocking.FrictionMode
import com.unscroll.app.domain.insights.TimeRange
import com.unscroll.app.domain.insights.UsageDataSource
import com.unscroll.app.domain.insights.localDate
import com.unscroll.app.domain.insights.startOfDay
import com.unscroll.app.domain.time.Clock
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.ZoneId
import javax.inject.Inject
import kotlin.random.Random
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where the "I need access" flow is. */
sealed interface AccessRequest {
    data object NotStarted : AccessRequest
    data object TypingPhrase : AccessRequest
    data class Waiting(val secondsLeft: Int) : AccessRequest
    data object WaitDone : AccessRequest
    /** Extension saved: the activity should reopen the app. */
    data object Granted : AccessRequest
}

data class BlockUiState(
    val packageName: String,
    val reason: BlockReason,
    val until: Long?,
    val usedTodayMillis: Long = 0,
    val messageIndex: Int = 0,
    val frictionMode: FrictionMode = FrictionMode.WAIT,
    val accessRequest: AccessRequest = AccessRequest.NotStarted,
) {
    /** Extra time is only offered for the daily limit, never for "Block completely" or schedules. */
    val canRequestAccess: Boolean get() = reason == BlockReason.DAILY_LIMIT_REACHED
}

@HiltViewModel
class BlockViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val limits: LimitRepository,
    private val usage: UsageDataSource,
    private val blockingPreferences: BlockingPreferences,
    private val clock: Clock,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        BlockUiState(
            packageName = savedStateHandle.get<String>(BlockActivity.EXTRA_PACKAGE).orEmpty(),
            reason = savedStateHandle.get<String>(BlockActivity.EXTRA_REASON)
                ?.let { name -> BlockReason.entries.firstOrNull { it.name == name } }
                ?: BlockReason.BLOCKED_ALWAYS,
            until = savedStateHandle.get<Long>(BlockActivity.EXTRA_UNTIL)?.takeIf { it > 0 },
            messageIndex = savedStateHandle.get<Int>(KEY_MESSAGE) ?: Random.nextInt(MESSAGE_COUNT).also {
                savedStateHandle[KEY_MESSAGE] = it
            },
        ),
    )
    val uiState: StateFlow<BlockUiState> = _uiState.asStateFlow()

    private var waitJob: Job? = null

    init {
        viewModelScope.launch {
            val now = clock.now()
            val zone = ZoneId.systemDefault()
            val todayStart = startOfDay(localDate(now, zone), zone)
            val used = usage.appTotals(TimeRange(todayStart, now), now)[_uiState.value.packageName] ?: 0L
            val mode = blockingPreferences.current(now).frictionMode
            _uiState.update { it.copy(usedTodayMillis = used, frictionMode = mode) }
        }
    }

    fun requestAccess() {
        val state = _uiState.value
        if (!state.canRequestAccess || state.accessRequest != AccessRequest.NotStarted) return
        when (state.frictionMode) {
            FrictionMode.TYPE_PHRASE -> _uiState.update { it.copy(accessRequest = AccessRequest.TypingPhrase) }
            FrictionMode.WAIT -> startWait()
        }
    }

    fun cancelAccessRequest() {
        waitJob?.cancel()
        _uiState.update { it.copy(accessRequest = AccessRequest.NotStarted) }
    }

    /** The phrase was typed correctly, or the wait finished and the user confirmed. */
    fun frictionPassed() {
        val state = _uiState.value
        val passed = state.accessRequest == AccessRequest.TypingPhrase ||
            state.accessRequest == AccessRequest.WaitDone
        if (!state.canRequestAccess || !passed) return
        viewModelScope.launch {
            limits.grantExtension(
                packageName = state.packageName,
                now = clock.now(),
                durationMillis = BlockingSettings.EXTENSION_MILLIS,
                method = state.frictionMode,
            )
            _uiState.update { it.copy(accessRequest = AccessRequest.Granted) }
        }
    }

    private fun startWait() {
        waitJob?.cancel()
        waitJob = viewModelScope.launch {
            for (left in BlockingSettings.BLOCK_SCREEN_WAIT_SECONDS downTo 1) {
                _uiState.update { it.copy(accessRequest = AccessRequest.Waiting(left)) }
                delay(1_000)
            }
            _uiState.update { it.copy(accessRequest = AccessRequest.WaitDone) }
        }
    }

    companion object {
        /** Must match the number of entries in R.array.block_messages. */
        const val MESSAGE_COUNT = 8
        private const val KEY_MESSAGE = "block_message_index"
    }
}
