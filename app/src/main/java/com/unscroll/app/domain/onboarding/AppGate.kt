package com.unscroll.app.domain.onboarding

enum class AppDestination { ONBOARDING, PICK_APPS, MAIN }

/**
 * Decides whether the user sees onboarding, the "pick your free app" screen or the main app.
 * Onboarding can only be finished with the required permissions, and after that the main app
 * always opens: a permission revoked later pauses tracking and Home shows a "fix permissions"
 * banner, instead of sending the user back through onboarding. A free user with more tracked apps
 * than the free tier picks theirs first (once).
 */
object AppGate {
    fun resolve(onboardingCompleted: Boolean, needsPick: Boolean = false): AppDestination = when {
        !onboardingCompleted -> AppDestination.ONBOARDING
        needsPick -> AppDestination.PICK_APPS
        else -> AppDestination.MAIN
    }
}
