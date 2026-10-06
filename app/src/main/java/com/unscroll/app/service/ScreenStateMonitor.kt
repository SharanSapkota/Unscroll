package com.unscroll.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.unscroll.app.domain.tracking.ScreenStateSource
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** Screen on/off from ACTION_SCREEN_ON/OFF, which can only be received by a registered receiver. */
@Singleton
class ScreenStateMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
) : ScreenStateSource {

    override val isScreenOn: Flow<Boolean> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_ON -> trySend(true)
                    Intent.ACTION_SCREEN_OFF -> trySend(false)
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        // Protected system broadcasts are still delivered to non-exported receivers.
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        val powerManager = context.getSystemService(PowerManager::class.java)
        trySend(powerManager?.isInteractive ?: true)
        awaitClose { context.unregisterReceiver(receiver) }
    }.distinctUntilChanged()
}
