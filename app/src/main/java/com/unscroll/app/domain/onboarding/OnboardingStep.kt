package com.unscroll.app.domain.onboarding

/** Onboarding pages in the order they are shown. */
enum class OnboardingStep {
    WELCOME,

    /** Usage access and overlay (required), notifications and battery (optional), each with "Grant". */
    PERMISSIONS,

    /**
     * Optional: "Count your swipes", the scroll-counting disclosure with "Enable" (agree, then
     * Accessibility settings) and "Skip". Never blocks finishing.
     */
    SWIPES,
}
