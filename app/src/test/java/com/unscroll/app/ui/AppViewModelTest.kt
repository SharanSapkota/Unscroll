package com.unscroll.app.ui

import com.unscroll.app.domain.onboarding.AppDestination
import com.unscroll.app.testing.FakeOnboardingRepository
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

    @Test
    fun gate_followsOnboarding() = runTest {
        val viewModel = AppViewModel(onboardingRepository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.destination.collect {} }

        assertEquals(AppDestination.ONBOARDING, viewModel.destination.value)

        onboardingRepository.completed.value = true
        assertEquals(AppDestination.MAIN, viewModel.destination.value)
    }
}
