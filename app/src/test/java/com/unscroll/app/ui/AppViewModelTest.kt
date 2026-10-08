package com.unscroll.app.ui

import com.unscroll.app.domain.onboarding.AppDestination
import com.unscroll.app.testing.FakeOnboardingRepository
import com.unscroll.app.testing.FakeTrackedAppsSource
import com.unscroll.app.testing.MainDispatcherRule
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AppViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val onboardingRepository = FakeOnboardingRepository()
    private val trackedApps = FakeTrackedAppsSource()

    @Test
    fun gate_followsOnboarding() = runTest {
        val viewModel = AppViewModel(onboardingRepository, trackedApps)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.destination.collect {} }

        assertEquals(AppDestination.ONBOARDING, viewModel.destination.value)

        onboardingRepository.completed.value = true
        assertEquals(AppDestination.MAIN, viewModel.destination.value)
    }

    @Test
    fun gate_showsThePickScreen_untilTheFreeAppIsPicked() = runTest {
        onboardingRepository.completed.value = true
        trackedApps.current.value = trackedApps.current.value.copy(isPlus = false, needsPick = true)
        val viewModel = AppViewModel(onboardingRepository, trackedApps)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.destination.collect {} }

        assertEquals(AppDestination.PICK_APPS, viewModel.destination.value)

        trackedApps.current.value = trackedApps.current.value.copy(needsPick = false)
        assertEquals(AppDestination.MAIN, viewModel.destination.value)
    }
}
