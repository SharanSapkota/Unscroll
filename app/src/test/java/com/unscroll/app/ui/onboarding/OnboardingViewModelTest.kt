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
    fun returningUserWithRevokedOverlay_startsAtOverlayStep() = runTest {
        onboardingRepository.completed.value = true
        grant(permissions(AppPermission.USAGE_ACCESS))

        val viewModel = createViewModel()

        assertEquals(OnboardingStep.OVERLAY, viewModel.step())
    }

    @Test
    fun restoredStep_isKeptAfterProcessDeath() = runTest {
        val viewModel = createViewModel(
            SavedStateHandle(mapOf("onboarding_step" to OnboardingStep.NOTIFICATIONS.name)),
        )

        assertEquals(OnboardingStep.NOTIFICATIONS, viewModel.step())
    }

    @Test
    fun usageAccessStep_blocksUntilGranted_thenContinues() = runTest {
        val viewModel = createViewModel()
        viewModel.onContinue()
        assertEquals(OnboardingStep.USAGE_ACCESS, viewModel.step())
        assertFalse(viewModel.uiState.value!!.canContinue)

        viewModel.onContinue()
        assertEquals(OnboardingStep.USAGE_ACCESS, viewModel.step())

        // User grants access in Settings and comes back; MainActivity refreshes.
        grant(permissions(AppPermission.USAGE_ACCESS))
        assertTrue(viewModel.uiState.value!!.canContinue)
        assertTrue(viewModel.uiState.value!!.isGranted)

        viewModel.onContinue()
        assertEquals(OnboardingStep.OVERLAY, viewModel.step())
    }

    @Test
    fun fullFlow_skippingOptionalSteps_completesOnboarding() = runTest {
        grant(REQUIRED_ONLY)
        val viewModel = createViewModel()

        viewModel.onContinue() // welcome -> usage access
        viewModel.onContinue() // -> overlay
        viewModel.onContinue() // -> notifications
        assertEquals(OnboardingStep.NOTIFICATIONS, viewModel.step())
        assertEquals(PrimaryAction.SKIP, viewModel.uiState.value?.primaryAction)

        viewModel.onContinue() // skip -> battery
        assertEquals(OnboardingStep.BATTERY, viewModel.step())
        assertFalse(onboardingRepository.completed.value)

        viewModel.onContinue() // finish
        assertTrue(onboardingRepository.completed.value)
    }

    @Test
    fun finish_withRevokedRequiredPermission_goesBackInsteadOfCompleting() = runTest {
        val viewModel = createViewModel(
            SavedStateHandle(mapOf("onboarding_step" to OnboardingStep.BATTERY.name)),
        )
        grant(permissions(AppPermission.OVERLAY))

        viewModel.onContinue()

        assertEquals(OnboardingStep.USAGE_ACCESS, viewModel.step())
        assertFalse(onboardingRepository.completed.value)
    }

    @Test
    fun onBack_goesToPreviousStep_andStopsAtWelcome() = runTest {
        val viewModel = createViewModel(
            SavedStateHandle(mapOf("onboarding_step" to OnboardingStep.OVERLAY.name)),
        )

        viewModel.onBack()
        assertEquals(OnboardingStep.USAGE_ACCESS, viewModel.step())
        viewModel.onBack()
        assertEquals(OnboardingStep.WELCOME, viewModel.step())
        viewModel.onBack()
        assertEquals(OnboardingStep.WELCOME, viewModel.step())
    }

    @Test
    fun onPermissionResult_refreshesPermissions() = runTest {
        val viewModel = createViewModel(
            SavedStateHandle(mapOf("onboarding_step" to OnboardingStep.NOTIFICATIONS.name)),
        )
        assertFalse(viewModel.uiState.value!!.isGranted)

        checker.state = permissions(AppPermission.NOTIFICATIONS)
        viewModel.onPermissionResult()

        assertTrue(viewModel.uiState.value!!.isGranted)
        assertEquals(PrimaryAction.CONTINUE, viewModel.uiState.value?.primaryAction)
    }
}
