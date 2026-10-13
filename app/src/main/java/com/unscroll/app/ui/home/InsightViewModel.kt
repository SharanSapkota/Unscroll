package com.unscroll.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unscroll.app.data.insights.InsightCardState
import com.unscroll.app.data.insights.InsightPreferences
import com.unscroll.app.data.insights.InsightRepository
import com.unscroll.app.domain.insights.UsageDataSource
import com.unscroll.app.domain.insights.UsagePeriod
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The Insight card on Home and the weekly projection card. Numbers refresh on data changes and
 * once a minute while Home is shown; the message itself only changes with "Next" or a new day.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class InsightViewModel @Inject constructor(
    private val repository: InsightRepository,
    preferences: InsightPreferences,
    usage: UsageDataSource,
) : ViewModel() {

    private val period = MutableStateFlow(UsagePeriod.TODAY)

    /** Bumped after "Next", so the card reloads at once. */
    private val reloads = MutableStateFlow(0)

    private val ticks = flow {
        while (true) {
            emit(Unit)
            delay(REFRESH_MILLIS)
        }
    }

    val state: StateFlow<InsightCardState> = combine(
        period,
        preferences.settings,
        usage.observeChanges(),
        reloads,
        ticks,
    ) { period, _, _, _, _ -> period }
        .mapLatest { repository.card(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InsightCardState())

    /** The insight follows the period Home shows (Day / Week / Month / All). */
    fun setPeriod(value: UsagePeriod) {
        period.value = value
    }

    fun next() {
        viewModelScope.launch {
            repository.next(period.value)
            reloads.value++
        }
    }

    private companion object {
        const val REFRESH_MILLIS = 60_000L
    }
}
