package com.unscroll.app.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unscroll.app.data.goals.GoalPreferences
import com.unscroll.app.data.history.HistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The result of the last export or delete, shown under the buttons. */
sealed interface DataActionResult {
    data class Exported(val sessions: Int) : DataActionResult
    data object ExportFailed : DataActionResult
    data class Deleted(val sessions: Int) : DataActionResult
}

/** Settings › Daily goal and Your data (M8). */
@HiltViewModel
class YourDataViewModel @Inject constructor(
    private val goalPreferences: GoalPreferences,
    private val history: HistoryRepository,
) : ViewModel() {

    val dailyGoalMinutes: StateFlow<Int?> = goalPreferences.dailyGoalMinutes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _result = MutableStateFlow<DataActionResult?>(null)
    val result: StateFlow<DataActionResult?> = _result.asStateFlow()

    fun setDailyGoal(minutes: Int?) {
        viewModelScope.launch { goalPreferences.setDailyGoal(minutes) }
    }

    /** [uri] comes from the system "save as" picker, or null if the user cancelled. */
    fun export(uri: Uri?) {
        if (uri == null) return
        launchOnce {
            _result.value = try {
                DataActionResult.Exported(history.exportSessions(uri))
            } catch (e: IOException) {
                DataActionResult.ExportFailed
            } catch (e: SecurityException) {
                DataActionResult.ExportFailed
            }
        }
    }

    fun deleteUsageHistory() {
        launchOnce { _result.value = DataActionResult.Deleted(history.deleteUsageHistory()) }
    }

    private fun launchOnce(block: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                block()
            } finally {
                _busy.value = false
            }
        }
    }
}
