package com.unscroll.app.service

import android.content.Context
import com.unscroll.app.data.onboarding.OnboardingRepository
import com.unscroll.app.data.permission.PermissionRepository
import com.unscroll.app.data.tracking.TrackingPreferences
import com.unscroll.app.domain.tracking.TrackingStartRules
import com.unscroll.app.domain.tracking.TrackingStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first

/**
 * Turns tracking on and off (the user's explicit choice) and starts [TrackingService] whenever it
 * should run: after onboarding, when the app opens, when a missing permission is granted, and
 * after a reboot or an app update ([TrackingStartRules]).
 */
@Singleton
class TrackingController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: TrackingPreferences,
    private val permissions: PermissionRepository,
    private val onboarding: OnboardingRepository,
    private val sessionManager: SessionManager,
) {
    val trackingEnabled: Flow<Boolean> = preferences.trackingEnabled

    /** On, paused for a missing permission, or off. */
    val status: Flow<TrackingStatus> = combine(preferences.trackingEnabled, permissions.permissions) { enabled, granted ->
        TrackingStartRules.status(enabled, granted)
    }.distinctUntilChanged()

    /** Everything that can make the service startable; MainActivity collects it while resumed. */
    val startTriggers: Flow<Unit> = combine(
        onboarding.onboardingCompleted,
        preferences.trackingEnabled,
        permissions.permissions,
    ) { _, _, _ -> }

    suspend fun setTrackingEnabled(enabled: Boolean) {
        preferences.setTrackingEnabled(enabled)
        if (enabled) {
            // Sent even if an old instance is still shutting down; a running service ignores it.
            startIfReady(evenIfRunning = true)
        } else {
            TrackingService.stop(context)
            sessionManager.endCurrentSession()
        }
    }

    /**
     * Starts the service if it should run and isn't running yet. Returns true if it was started.
     * Only call it from a visible activity or the boot receiver; a refusal is logged, not thrown.
     */
    suspend fun startIfReady(evenIfRunning: Boolean = false): Boolean {
        val start = TrackingStartRules.shouldStart(
            onboardingCompleted = onboarding.onboardingCompleted.first(),
            enabled = preferences.trackingEnabled.first(),
            permissions = permissions.permissions.value,
            serviceRunning = TrackingService.isRunning && !evenIfRunning,
        )
        return start && TrackingService.start(context)
    }
}
