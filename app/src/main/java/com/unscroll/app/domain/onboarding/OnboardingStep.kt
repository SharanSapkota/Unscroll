package com.unscroll.app.domain.onboarding

import com.unscroll.app.domain.permission.AppPermission

/** Onboarding screens in the order they are shown. */
enum class OnboardingStep(val permission: AppPermission?) {
    WELCOME(permission = null),
    USAGE_ACCESS(permission = AppPermission.USAGE_ACCESS),
    OVERLAY(permission = AppPermission.OVERLAY),
    NOTIFICATIONS(permission = AppPermission.NOTIFICATIONS),
    BATTERY(permission = AppPermission.IGNORE_BATTERY_OPTIMIZATIONS),
    ;

    val isOptional: Boolean
        get() = permission?.required == false

    companion object {
        fun forPermission(permission: AppPermission): OnboardingStep =
            entries.first { it.permission == permission }
    }
}
