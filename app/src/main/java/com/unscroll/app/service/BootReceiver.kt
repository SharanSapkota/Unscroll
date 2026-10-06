package com.unscroll.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.unscroll.app.domain.ApplicationScope
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Restarts tracking after a reboot or an app update if the user had it turned on. Both broadcasts
 * are allowed to start a foreground service from the background.
 */
class BootReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface BootReceiverEntryPoint {
        fun trackingController(): TrackingController

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
                entryPoint.trackingController().startIfEnabled()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
