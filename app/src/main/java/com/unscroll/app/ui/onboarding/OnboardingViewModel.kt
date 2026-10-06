package com.unscroll.app.ui.onboarding

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unscroll.app.data.onboarding.OnboardingRepository
import com.unscroll.app.data.permission.PermissionRepository
import com.unscroll.app.domain.onboarding.OnboardingAdvance
import com.unscroll.app.domain.onboarding.OnboardingFlow
import com.unscroll.app.domain.onboarding.OnboardingStep
import com.unscroll.app.domain.onboarding.PrimaryAction
import com.unscroll.app.domain.permission.PermissionState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class OnboardingUiState(
    val step: OnboardingStep,
    val permissions: PermissionState,
) {
    val stepNumber: Int get() = step.ordinal + 1
    val stepCount: Int get() = OnboardingFlow.stepCount
    val isGranted: Boolean get() = step.permission?.let(permissions::isGranted) ?: false
    val canContinue: Boolean get() = OnboardingFlow.canContinue(step, permissions)
    val canGoBack: Boolean get() = OnboardingFlow.previous(step) != null
    val primaryAction: PrimaryAction get() = OnboardingFlow.primaryAction(step, permissions)
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val permissionRepository: PermissionRepository,
    private val onboardingRepository: OnboardingRepository,
) : ViewModel() {

    // Stored by name so the current step survives process death.
    private val stepName = savedStateHandle.getStateFlow<String?>(KEY_STEP, null)

    /** Null until the start step is known. */
    val uiState: StateFlow<OnboardingUiState?> = combine(
        stepName,
        permissionRepository.permissions,
    ) { name, permissions ->
        name?.let { OnboardingUiState(OnboardingStep.valueOf(it), permissions) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        if (savedStateHandle.get<String>(KEY_STEP) == null) {
            viewModelScope.launch {
                val completed = onboardingRepository.onboardingCompleted.first()
                setStep(OnboardingFlow.startStep(completed, permissionRepository.permissions.value))
            }
        }
    }

    fun onContinue() {
        val step = currentStep() ?: return
        when (val advance = OnboardingFlow.advance(step, permissionRepository.permissions.value)) {
            is OnboardingAdvance.ToStep -> setStep(advance.step)
            OnboardingAdvance.Finish -> viewModelScope.launch {
                onboardingRepository.setOnboardingCompleted(true)
            }
        }
    }

    fun onBack() {
        val previous = currentStep()?.let(OnboardingFlow::previous) ?: return
        setStep(previous)
    }

    /** Called after a runtime permission dialog closes. */
    fun onPermissionResult() {
        permissionRepository.refresh()
    }

    private fun currentStep(): OnboardingStep? =
        savedStateHandle.get<String>(KEY_STEP)?.let(OnboardingStep::valueOf)

    private fun setStep(step: OnboardingStep) {
        savedStateHandle[KEY_STEP] = step.name
    }

    private companion object {
        const val KEY_STEP = "onboarding_step"
    }
}
