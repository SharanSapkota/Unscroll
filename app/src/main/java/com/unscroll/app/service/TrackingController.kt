package com.unscroll.app.service

import android.content.Context
import com.unscroll.app.data.tracking.TrackingPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/** Turns tracking on and off: saves the choice and starts or stops [TrackingService]. */
@Singleton
class TrackingController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: TrackingPreferences,
    private val sessionManager: SessionManager,
) {
    val trackingEnabled: Flow<Boolean> = preferences.trackingEnabled

    suspend fun setTrackingEnabled(enabled: Boolean) {
        preferences.setTrackingEnabled(enabled)
        if (enabled) {
            TrackingService.start(context)
        } else {
            TrackingService.stop(context)
            sessionManager.endCurrentSession()
        }
    }

    /** Restarts the service if tracking is on, e.g. after the app was force-stopped. */
    suspend fun startIfEnabled() {
        if (preferences.trackingEnabled.first()) TrackingService.start(context)
    }
}
