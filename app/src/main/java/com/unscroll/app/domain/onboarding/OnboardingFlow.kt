package com.unscroll.app.domain.onboarding

import com.unscroll.app.domain.permission.PermissionState

/** Where the user goes after pressing the primary button on a step. */
sealed interface OnboardingAdvance {
    data class ToStep(val step: OnboardingStep) : OnboardingAdvance
    data object Finish : OnboardingAdvance
}

/** The label the primary button shows on a step. */
enum class PrimaryAction { GET_STARTED, CONTINUE, SKIP, FINISH }

/** Pure navigation rules for the onboarding flow. */
object OnboardingFlow {

    private val steps = OnboardingStep.entries

    val stepCount: Int get() = steps.size

    /**
     * First-time users start at the welcome screen. Users who finished onboarding before but have
     * since revoked a required permission go straight to the first missing one.
     */
    fun startStep(onboardingCompleted: Boolean, permissions: PermissionState): OnboardingStep {
        if (!onboardingCompleted) return OnboardingStep.WELCOME
        val missing = permissions.firstMissingRequired ?: return OnboardingStep.WELCOME
        return OnboardingStep.forPermission(missing)
    }

    /** Required steps block until their permission is granted. Everything else can move on. */
    fun canContinue(step: OnboardingStep, permissions: PermissionState): Boolean {
        val permission = step.permission ?: return true
        return !permission.required || permissions.isGranted(permission)
    }

    fun previous(step: OnboardingStep): OnboardingStep? = steps.getOrNull(step.ordinal - 1)

    fun advance(step: OnboardingStep, permissions: PermissionState): OnboardingAdvance {
        if (!canContinue(step, permissions)) return OnboardingAdvance.ToStep(step)
        val next = steps.getOrNull(step.ordinal + 1)
        if (next != null) return OnboardingAdvance.ToStep(next)
        // Last step: a required permission may have been revoked while the user was further along.
        val missing = permissions.firstMissingRequired
            ?: return OnboardingAdvance.Finish
        return OnboardingAdvance.ToStep(OnboardingStep.forPermission(missing))
    }

    fun primaryAction(step: OnboardingStep, permissions: PermissionState): PrimaryAction {
        val permission = step.permission
        return when {
            step == OnboardingStep.WELCOME -> PrimaryAction.GET_STARTED
            step.ordinal == steps.lastIndex -> PrimaryAction.FINISH
            permission != null && step.isOptional && !permissions.isGranted(permission) ->
                PrimaryAction.SKIP
            else -> PrimaryAction.CONTINUE
        }
    }
}
