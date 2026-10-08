package com.unscroll.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unscroll.app.data.onboarding.OnboardingRepository
import com.unscroll.app.domain.onboarding.AppDestination
import com.unscroll.app.domain.onboarding.AppGate
import com.unscroll.app.domain.plus.TrackedAppsSource
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Decides between onboarding, the free-app pick and the main app. Null until the saved state has loaded. */
@HiltViewModel
class AppViewModel @Inject constructor(
    onboardingRepository: OnboardingRepository,
    trackedApps: TrackedAppsSource,
) : ViewModel() {

    val destination: StateFlow<AppDestination?> = combine(
        onboardingRepository.onboardingCompleted,
        trackedApps.state.map { it.needsPick }.distinctUntilChanged(),
    ) { onboarded, needsPick -> AppGate.resolve(onboarded, needsPick) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
