package com.unscroll.app.domain.onboarding

import com.unscroll.app.domain.permission.PermissionState

/** Where the user goes after pressing the primary button on a page. */
sealed interface OnboardingAdvance {
    data class ToStep(val step: OnboardingStep) : OnboardingAdvance
    data object Finish : OnboardingAdvance
}

/** The label the primary button shows on a page. */
enum class PrimaryAction { GET_STARTED, NEXT, FINISH }

/** Pure navigation rules for the onboarding: welcome, permissions, then optional swipe counting. */
object OnboardingFlow {

    private val steps = OnboardingStep.entries

    val stepCount: Int get() = steps.size

    /**
     * First-time users start at the welcome page. Users who finished onboarding before but have
     * since revoked a required permission go straight to the permissions page.
     */
    fun startStep(onboardingCompleted: Boolean, permissions: PermissionState): OnboardingStep =
        if (onboardingCompleted && !permissions.requiredGranted) OnboardingStep.PERMISSIONS else OnboardingStep.WELCOME

    /**
     * The permissions page blocks until both required permissions are granted, and so does the
     * last page (swiped to early): onboarding can only finish with them. Swipe counting never
     * blocks.
     */
    fun canContinue(step: OnboardingStep, permissions: PermissionState): Boolean =
        step == OnboardingStep.WELCOME || permissions.requiredGranted

    fun previous(step: OnboardingStep): OnboardingStep? = steps.getOrNull(step.ordinal - 1)

    fun advance(step: OnboardingStep, permissions: PermissionState): OnboardingAdvance = when {
        step == OnboardingStep.WELCOME -> OnboardingAdvance.ToStep(OnboardingStep.PERMISSIONS)
        !permissions.requiredGranted -> OnboardingAdvance.ToStep(OnboardingStep.PERMISSIONS)
        step == OnboardingStep.PERMISSIONS -> OnboardingAdvance.ToStep(OnboardingStep.SWIPES)
        else -> OnboardingAdvance.Finish
    }

    fun primaryAction(step: OnboardingStep): PrimaryAction = when (step) {
        OnboardingStep.WELCOME -> PrimaryAction.GET_STARTED
        OnboardingStep.PERMISSIONS -> PrimaryAction.NEXT
        OnboardingStep.SWIPES -> PrimaryAction.FINISH
    }
}
