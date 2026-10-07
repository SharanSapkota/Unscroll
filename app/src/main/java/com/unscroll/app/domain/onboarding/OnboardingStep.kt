package com.unscroll.app.domain.onboarding

/** Onboarding pages in the order they are shown: one welcome page, one permissions page. */
enum class OnboardingStep {
    WELCOME,

    /** Usage access and overlay (required), notifications and battery (optional), each with "Grant". */
    PERMISSIONS,
}
