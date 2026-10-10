package com.unscroll.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.unscroll.app.data.permission.PermissionRepository
import com.unscroll.app.domain.ApplicationScope
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Starts tracking after a reboot or an app update, unless the user turned it off (tracking is on
 * by default). Both broadcasts are allowed to start a foreground service from the background;
 * a refusal is logged by [TrackingService.start], and the app tries again when it opens.
 */
class BootReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface BootReceiverEntryPoint {
        fun trackingController(): TrackingController

        fun permissionRepository(): PermissionRepository

        fun blockExpiryScheduler(): BlockExpiryScheduler

        @ApplicationScope
        fun applicationScope(): CoroutineScope
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            BootReceiverEntryPoint::class.java,
        )
        val pendingResult = goAsync()
        entryPoint.applicationScope().launch {
            try {
                // Permissions may have changed while the phone was off or the app was updated.
                entryPoint.permissionRepository().refresh()
                entryPoint.trackingController().startIfReady()
                // Alarms don't survive a reboot: turn off blocks that ran out, set the next one.
                entryPoint.blockExpiryScheduler().sync()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
