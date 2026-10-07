package com.unscroll.app.domain.tracking

import com.unscroll.app.domain.permission.AppPermission
import com.unscroll.app.domain.permission.PermissionState

/** What the user sees about tracking: on, off on purpose, or on but waiting for a permission. */
enum class TrackingStatus {
    /** On, with Usage access and Overlay granted. */
    ACTIVE,

    /** On, but a required permission is missing: nothing can be tracked until it is granted. */
    PAUSED,

    /** The user turned tracking off. */
    OFF,
}

/** When tracking runs and when the service should be started. Pure. */
object TrackingStartRules {

    /** Tracking is on unless the user turned it off: a fresh install (never set) means on. */
    const val ENABLED_BY_DEFAULT = true

    /** The stored switch, or the default when the user never touched it. */
    fun enabled(stored: Boolean?): Boolean = stored ?: ENABLED_BY_DEFAULT

    fun status(enabled: Boolean, permissions: PermissionState): TrackingStatus = when {
        !enabled -> TrackingStatus.OFF
        !permissions.requiredGranted -> TrackingStatus.PAUSED
        else -> TrackingStatus.ACTIVE
    }

    /**
     * Start the service only after onboarding, with tracking on and every required permission, and
     * only if it isn't already running.
     */
    fun shouldStart(
        onboardingCompleted: Boolean,
        enabled: Boolean,
        permissions: PermissionState,
        serviceRunning: Boolean,
    ): Boolean = onboardingCompleted && !serviceRunning && status(enabled, permissions) == TrackingStatus.ACTIVE

    /** The permission the "fix permissions" button opens first, or null when nothing is missing. */
    fun permissionToFix(permissions: PermissionState): AppPermission? = permissions.firstMissingRequired
}
