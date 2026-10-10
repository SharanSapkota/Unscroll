package com.unscroll.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.unscroll.app.MainActivity
import com.unscroll.app.R
import com.unscroll.app.data.tracking.TrackingPreferences
import com.unscroll.app.domain.tracking.ScreenStateSource
import com.unscroll.app.overlay.OverlayTimerManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Foreground service (type specialUse) that keeps session tracking alive while the app is in the
 * background. It wires [AppDetector] to [SessionManager] and owns the overlay timer's lifecycle
 * ([OverlayTimerManager]) and blocking ([BlockEnforcer]); all the logic lives in those classes.
 *
 * START_STICKY: if the system kills the process, Android restarts the service with a null intent,
 * and [SessionManager.run] first closes the session the dead process left open.
 */
@AndroidEntryPoint
class TrackingService : Service() {

    @Inject
    lateinit var sessionManager: SessionManager

    @Inject
    lateinit var trackingPreferences: TrackingPreferences

    @Inject
    lateinit var overlayTimerManager: OverlayTimerManager

    @Inject
    lateinit var blockEnforcer: BlockEnforcer

    @Inject
    lateinit var frictionCoordinator: FrictionCoordinator

    @Inject
    lateinit var swipeLimitEnforcer: SwipeLimitEnforcer

    @Inject
    lateinit var screenState: ScreenStateSource

    @Inject
    lateinit var blockExpiry: BlockExpiryScheduler

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var trackingJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must be called promptly after startForegroundService(), even if we stop right away.
        // If the system refuses (e.g. a sticky restart from the background on Android 12+), stop
        // quietly: the app starts tracking again the next time it opens.
        if (!startInForeground()) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (trackingJob?.isActive != true) {
            trackingJob = scope.launch {
                // A sticky restart after the user turned tracking off should not resume tracking.
                if (!trackingPreferences.trackingEnabled.first()) {
                    stopTracking()
                    return@launch
                }
                launch { overlayTimerManager.run() }
                launch { blockEnforcer.run() }
                launch { frictionCoordinator.run() }
                launch { swipeLimitEnforcer.run() }
                // Timed blocks that ran out while the screen was off turn off on screen-on.
                launch { screenState.isScreenOn.filter { it }.collect { blockExpiry.sync() } }
                sessionManager.run()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        // Remove the overlay synchronously so it can never outlive the service.
        overlayTimerManager.hide()
        frictionCoordinator.hideNow()
        swipeLimitEnforcer.hideNow()
        // Cancelling run() closes the open session.
        scope.cancel()
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        overlayTimerManager.onConfigurationChanged()
    }

    private fun stopTracking() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startInForeground(): Boolean {
        createNotificationChannel(this)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        return try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type)
            true
        } catch (e: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException (Android 12+) is an IllegalStateException.
            Log.w(TAG, "Not allowed to run in the foreground right now", e)
            false
        } catch (e: SecurityException) {
            Log.w(TAG, "Not allowed to run in the foreground", e)
            false
        }
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.tracking_notification_title))
            .setContentText(getString(R.string.tracking_notification_text))
            .setContentIntent(openApp)
            .setOngoing(true)
            .setShowWhen(false)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        private const val TAG = "TrackingService"
        private const val CHANNEL_ID = "tracking"
        private const val NOTIFICATION_ID = 1

        /** True between onCreate and onDestroy in this process. A dead process reads false. */
        @Volatile
        var isRunning: Boolean = false
            private set

        /**
         * Starts the service. Call it only from a context Android allows (a visible activity, the
         * boot or package-replaced broadcast). Never throws: returns false when Android refuses
         * (ForegroundServiceStartNotAllowedException and friends), and the caller tries again the
         * next time the app opens.
         */
        fun start(context: Context): Boolean = try {
            ContextCompat.startForegroundService(
                context,
                Intent(context, TrackingService::class.java),
            )
            true
        } catch (e: IllegalStateException) {
            // Includes ForegroundServiceStartNotAllowedException (Android 12+).
            Log.w(TAG, "Tracking not started: not allowed from here", e)
            false
        } catch (e: SecurityException) {
            Log.w(TAG, "Tracking not started", e)
            false
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TrackingService::class.java))
        }

        private fun createNotificationChannel(context: Context) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.tracking_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.tracking_channel_description)
                setShowBadge(false)
            }
            context.getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(channel)
        }
    }
}
