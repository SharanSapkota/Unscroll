package com.unscroll.app.domain.onboarding

import com.unscroll.app.domain.permission.PermissionState

enum class AppDestination { ONBOARDING, MAIN }

/** Decides whether the user sees onboarding or the main app (dashboard). */
object AppGate {
    fun resolve(onboardingCompleted: Boolean, permissions: PermissionState): AppDestination =
        if (onboardingCompleted && permissions.requiredGranted) {
            AppDestination.MAIN
        } else {
            AppDestination.ONBOARDING
        }
}
