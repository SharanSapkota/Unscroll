package com.unscroll.app.domain.onboarding

enum class AppDestination { ONBOARDING, MAIN }

/**
 * Decides whether the user sees onboarding or the main app. Onboarding can only be finished with
 * the required permissions, and after that the main app always opens: a permission revoked later
 * pauses tracking and Home shows a "fix permissions" banner, instead of sending the user back
 * through onboarding.
 */
object AppGate {
    fun resolve(onboardingCompleted: Boolean): AppDestination =
        if (onboardingCompleted) AppDestination.MAIN else AppDestination.ONBOARDING
}
