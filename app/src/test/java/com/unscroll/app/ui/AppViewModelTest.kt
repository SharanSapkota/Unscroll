package com.unscroll.app.ui

import com.unscroll.app.data.permission.PermissionRepository
import com.unscroll.app.domain.onboarding.AppDestination
import com.unscroll.app.domain.permission.AppPermission
import com.unscroll.app.testing.FakeOnboardingRepository
import com.unscroll.app.testing.FakePermissionChecker
import com.unscroll.app.testing.MainDispatcherRule
import com.unscroll.app.testing.REQUIRED_ONLY
import com.unscroll.app.testing.permissions
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AppViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val checker = FakePermissionChecker()
    private val permissionRepository = PermissionRepository(checker)
    private val onboardingRepository = FakeOnboardingRepository()

    @Test
    fun gate_followsOnboardingAndPermissionChanges() = runTest {
        val viewModel = AppViewModel(onboardingRepository, permissionRepository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.destination.collect {} }

        assertEquals(AppDestination.ONBOARDING, viewModel.destination.value)

        checker.state = REQUIRED_ONLY
        permissionRepository.refresh()
        assertEquals(AppDestination.ONBOARDING, viewModel.destination.value)

        onboardingRepository.completed.value = true
        assertEquals(AppDestination.MAIN, viewModel.destination.value)

        // User revokes usage access in Settings, then returns to the app.
        checker.state = permissions(AppPermission.OVERLAY)
        permissionRepository.refresh()
        assertEquals(AppDestination.ONBOARDING, viewModel.destination.value)
    }
}
