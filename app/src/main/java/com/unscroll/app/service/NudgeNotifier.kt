package com.unscroll.app.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.unscroll.app.MainActivity
import com.unscroll.app.R
import com.unscroll.app.util.appLabel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Nudge notifications. Open counts and limit warnings use the "Nudges" channel; break reminders
 * (only when the overlay can't show them) use a high-importance channel so they appear heads-up.
 * Silently does nothing without the notification permission.
 */
@Singleton
class NudgeNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun notifyOpens(packageName: String, opens: Int) = post(
        channel = CHANNEL_NUDGES,
        id = notificationId(packageName, KIND_OPENS),
        text = context.getString(R.string.nudge_opens, appName(packageName), opens),
    )

    fun notifyLimit(packageName: String, percent: Int) = post(
        channel = CHANNEL_NUDGES,
        id = notificationId(packageName, KIND_LIMIT),
        text = if (percent >= 100) {
            context.getString(R.string.overlay_limit_reached, appName(packageName))
        } else {
            context.getString(R.string.overlay_limit_warning, appName(packageName))
        },
    )

    fun notifyBreak(packageName: String, minutes: Int) = post(
        channel = CHANNEL_BREAKS,
        id = notificationId(packageName, KIND_BREAK),
        text = context.getString(R.string.overlay_break_message, minutes, appName(packageName)),
        highPriority = true,
    )

    @SuppressLint("MissingPermission") // Checked in canNotify().
    private fun post(channel: String, id: Int, text: String, highPriority: Boolean = false) {
        if (!canNotify()) return
        createChannels()
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setPriority(if (highPriority) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
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

    private fun createChannels() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_NUDGES,
                context.getString(R.string.nudge_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = context.getString(R.string.nudge_channel_description) },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_BREAKS,
                context.getString(R.string.break_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = context.getString(R.string.break_channel_description) },
        )
    }

    private fun appName(packageName: String) = context.packageManager.appLabel(packageName)

    /** Stable per app and kind, so a newer nudge replaces the previous one of the same kind. */
    private fun notificationId(packageName: String, kind: Int): Int = 1_000 + (packageName.hashCode() and 0xFFFF) * 4 + kind

    private companion object {
        const val CHANNEL_NUDGES = "nudges"
        const val CHANNEL_BREAKS = "breaks"
        const val KIND_OPENS = 0
        const val KIND_LIMIT = 1
        const val KIND_BREAK = 2
    }
}
