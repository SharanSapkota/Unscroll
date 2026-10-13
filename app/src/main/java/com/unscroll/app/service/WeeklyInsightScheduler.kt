package com.unscroll.app.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.unscroll.app.MainActivity
import com.unscroll.app.R
import com.unscroll.app.data.insights.InsightPreferences
import com.unscroll.app.data.insights.InsightRepository
import com.unscroll.app.domain.ApplicationScope
import com.unscroll.app.domain.insights.InsightSchedule
import com.unscroll.app.domain.time.Clock
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The optional weekly life projection notification (Settings › Insights, off by default): one
 * inexact alarm on the weekly day (Sunday) around 18:00. The receiver posts the same projection as
 * the Home card, at most once a week ([InsightRepository.weeklyNotification]), then sets the next
 * alarm. Nothing is scheduled while the switch is off. No exact-alarm permission is needed.
 */
@Singleton
class WeeklyInsightScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: InsightPreferences,
    private val clock: Clock,
    @ApplicationScope private val scope: CoroutineScope,
) {
    /** Follows the switch. Call once, from the Application. */
    fun start() {
        scope.launch {
            preferences.settings.map { it.enabled && it.weeklyNotification }.distinctUntilChanged().collect { schedule(it) }
        }
    }

    /** After a reboot or when the alarm fired: alarms don't survive either. */
    suspend fun sync() {
        val settings = preferences.settings.first()
        schedule(settings.enabled && settings.weeklyNotification)
    }

    private fun schedule(on: Boolean) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, WeeklyInsightReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (!on) {
            alarms.cancel(intent)
            return
        }
        val at = InsightSchedule.nextNotification(clock.now(), ZoneId.systemDefault())
        alarms.setWindow(AlarmManager.RTC_WAKEUP, at, WINDOW_MILLIS, intent)
    }

    private companion object {
        const val REQUEST_CODE = 11
        const val WINDOW_MILLIS = 60 * 60_000L
    }
}

/** Posts the weekly insight on its own low-importance channel. Silent without the notification permission. */
@Singleton
class InsightNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    @SuppressLint("MissingPermission") // Checked in canNotify().
    fun notifyWeekly(text: String) {
        if (!canNotify()) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                context.getString(R.string.insights_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = context.getString(R.string.insights_channel_description) },
        )
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.insights_weekly_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private fun canNotify(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private companion object {
        const val CHANNEL = "insights"
        const val NOTIFICATION_ID = 900
    }
}

/** The weekly alarm set by [WeeklyInsightScheduler]. */
class WeeklyInsightReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WeeklyInsightEntryPoint {
        fun insightRepository(): InsightRepository

        fun insightNotifier(): InsightNotifier

        fun weeklyInsightScheduler(): WeeklyInsightScheduler

        @ApplicationScope
        fun applicationScope(): CoroutineScope
    }

    override fun onReceive(context: Context, intent: Intent) {
        val entryPoint = EntryPointAccessors.fromApplication(context.applicationContext, WeeklyInsightEntryPoint::class.java)
        val pendingResult = goAsync()
        entryPoint.applicationScope().launch {
            try {
                entryPoint.insightRepository().weeklyNotification()?.let { entryPoint.insightNotifier().notifyWeekly(it) }
            } catch (e: RuntimeException) {
                // A database error must never crash a receiver; next week tries again.
                Log.w(TAG, "Could not post the weekly insight", e)
            } finally {
                entryPoint.weeklyInsightScheduler().sync()
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "Insights"
    }
}
