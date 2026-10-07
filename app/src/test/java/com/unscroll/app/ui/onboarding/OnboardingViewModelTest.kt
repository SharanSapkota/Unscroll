package com.unscroll.app.ui.onboarding

import androidx.lifecycle.SavedStateHandle
import com.unscroll.app.data.permission.PermissionRepository
import com.unscroll.app.domain.onboarding.OnboardingStep
import com.unscroll.app.domain.onboarding.PrimaryAction
import com.unscroll.app.domain.permission.AppPermission
import com.unscroll.app.domain.permission.PermissionState
import com.unscroll.app.testing.FakeOnboardingRepository
import com.unscroll.app.testing.FakePermissionChecker
import com.unscroll.app.testing.MainDispatcherRule
import com.unscroll.app.testing.REQUIRED_ONLY
import com.unscroll.app.testing.permissions
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class OnboardingViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val checker = FakePermissionChecker()
    private val permissionRepository = PermissionRepository(checker)
    private val onboardingRepository = FakeOnboardingRepository()

    private fun TestScope.createViewModel(
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
    ): OnboardingViewModel {
        val viewModel =
            OnboardingViewModel(savedStateHandle, permissionRepository, onboardingRepository)
        // uiState is only active while collected.
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        return viewModel
    }

    private fun OnboardingViewModel.step() = uiState.value?.step

    private fun grant(state: PermissionState) {
        checker.state = state
        permissionRepository.refresh()
    }

    @Test
    fun firstLaunch_startsAtWelcome() = runTest {
        val viewModel = createViewModel()

        assertEquals(OnboardingStep.WELCOME, viewModel.step())
        assertEquals(PrimaryAction.GET_STARTED, viewModel.uiState.value?.primaryAction)
        assertFalse(viewModel.uiState.value!!.canGoBack)
    }

    @Test
    fun returningUserWithRevokedOverlay_startsAtPermissions() = runTest {
        onboardingRepository.completed.value = true
        grant(permissions(AppPermission.USAGE_ACCESS))

        val viewModel = createViewModel()

        assertEquals(OnboardingStep.PERMISSIONS, viewModel.step())
    }

    @Test
    fun restoredPage_isKeptAfterProcessDeath_andOldStepNamesAreIgnored() = runTest {
        val restored = createViewModel(SavedStateHandle(mapOf("onboarding_step" to OnboardingStep.PERMISSIONS.name)))
        assertEquals(OnboardingStep.PERMISSIONS, restored.step())

        // A step saved by the old five-step onboarding starts over at the right page.
        val old = createViewModel(SavedStateHandle(mapOf("onboarding_step" to "BATTERY")))
        assertEquals(OnboardingStep.WELCOME, old.step())
    }

    @Test
    fun permissionsPage_blocksUntilRequiredGranted_thenFinishes() = runTest {
        val viewModel = createViewModel()
        viewModel.onContinue()
        assertEquals(OnboardingStep.PERMISSIONS, viewModel.step())
        assertFalse(viewModel.uiState.value!!.canContinue)

        viewModel.onContinue()
        assertEquals(OnboardingStep.PERMISSIONS, viewModel.step())
        assertFalse(onboardingRepository.completed.value)

        grant(REQUIRED_ONLY)
        assertTrue(viewModel.uiState.value!!.canContinue)
        viewModel.onContinue()
        assertTrue(onboardingRepository.completed.value)
    }

    @Test
    fun backAndSwipe_moveBetweenPages() = runTest {
        val viewModel = createViewModel(SavedStateHandle(mapOf("onboarding_step" to OnboardingStep.PERMISSIONS.name)))

        viewModel.onBack()
        assertEquals(OnboardingStep.WELCOME, viewModel.step())
        viewModel.onBack()
        assertEquals(OnboardingStep.WELCOME, viewModel.step())

        viewModel.onPageChanged(1)
        assertEquals(OnboardingStep.PERMISSIONS, viewModel.step())
        viewModel.onPageChanged(5)
        assertEquals(OnboardingStep.PERMISSIONS, viewModel.step())
    }

    @Test
    fun onPermissionResult_refreshesPermissions() = runTest {
        val viewModel = createViewModel(SavedStateHandle(mapOf("onboarding_step" to OnboardingStep.PERMISSIONS.name)))
        checker.state = PermissionState.ALL

        viewModel.onPermissionResult()

        assertEquals(PermissionState.ALL, viewModel.uiState.value?.permissions)
    }
}
