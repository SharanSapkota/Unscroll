package com.unscroll.app.ui.block

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unscroll.app.data.blocking.LimitRepository
import com.unscroll.app.data.blocking.SwipeLimitRepository
import com.unscroll.app.domain.blocking.AccessExtension
import com.unscroll.app.domain.blocking.BlockEvaluator
import com.unscroll.app.domain.blocking.BlockReason
import com.unscroll.app.domain.blocking.BlockScreenRules
import com.unscroll.app.domain.blocking.LimitSettings
import com.unscroll.app.domain.insights.TimeRange
import com.unscroll.app.domain.insights.UsageDataSource
import com.unscroll.app.domain.insights.localDate
import com.unscroll.app.domain.insights.startOfDay
import com.unscroll.app.domain.time.Clock
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BlockUiState(
    val packageName: String,
    val reason: BlockReason,
    val until: Long?,
    val usedTodayMillis: Long = 0,
    /** "I need access" was tapped and the extension saved: the activity should reopen the app. */
    val accessGranted: Boolean = false,
    /** The app isn't blocked any more (the user changed its limits): the activity should close. */
    val unblocked: Boolean = false,
) {
    /** Extra time is only offered for the daily limit, never for "Block completely" or schedules. */
    val canRequestAccess: Boolean get() = reason == BlockReason.DAILY_LIMIT_REACHED
}

@HiltViewModel
class BlockViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val limits: LimitRepository,
    private val swipeLimits: SwipeLimitRepository,
    private val usage: UsageDataSource,
    private val evaluator: BlockEvaluator,
    private val clock: Clock,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        BlockUiState(
            packageName = savedStateHandle.get<String>(BlockActivity.EXTRA_PACKAGE).orEmpty(),
            reason = savedStateHandle.get<String>(BlockActivity.EXTRA_REASON)
                ?.let { name -> BlockReason.entries.firstOrNull { it.name == name } }
                ?: BlockReason.BLOCKED_ALWAYS,
            until = savedStateHandle.get<Long>(BlockActivity.EXTRA_UNTIL)?.takeIf { it > 0 },
        ),
    )
    val uiState: StateFlow<BlockUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val now = clock.now()
            _uiState.update { it.copy(usedTodayMillis = usedToday(now, ZoneId.systemDefault())) }
        }
        // Changes in the Apps tab apply at once. Each one after the screen opened is checked here,
        // so unblocking the app (or raising its limit, or turning off its schedule) closes the
        // screen even while it sits in the background.
        viewModelScope.launch {
            val packageName = _uiState.value.packageName
            limits.observeLimits()
                .map { it[packageName]?.settings ?: LimitSettings.NONE }
                .distinctUntilChanged()
                .drop(1)
                .collect { recheck(it) }
        }
    }

    /** One tap: grants [AccessExtension.MILLIS] and logs it in `block_overrides`. */
    fun requestAccess() {
        val state = _uiState.value
        if (!state.canRequestAccess || state.accessGranted) return
        viewModelScope.launch {
            limits.grantExtension(state.packageName, clock.now(), AccessExtension.MILLIS)
            _uiState.update { it.copy(accessGranted = true) }
        }
    }

    private suspend fun recheck(settings: LimitSettings) {
        val state = _uiState.value
        val now = clock.now()
        val zone = ZoneId.systemDefault()
        val decision = evaluator.evaluate(
            settings = settings,
            usedTodayMillis = usedToday(now, zone),
            extensionUntil = limits.activeExtensionUntil(state.packageName, now),
            now = now,
            zone = zone,
        )
        val swipeLimitReached = state.reason == BlockReason.SWIPE_LIMIT_REACHED &&
            swipeLimits.status(state.packageName, settings, now, zone)?.reached == true
        val blocked = BlockScreenRules.current(state.reason, decision, swipeLimitReached)
        _uiState.update {
            if (blocked == null) it.copy(unblocked = true) else it.copy(reason = blocked.reason, until = blocked.until)
        }
    }

    private suspend fun usedToday(now: Long, zone: ZoneId): Long {
        val todayStart = startOfDay(localDate(now, zone), zone)
        return usage.appTotals(TimeRange(todayStart, now), now)[_uiState.value.packageName] ?: 0L
    }
}
