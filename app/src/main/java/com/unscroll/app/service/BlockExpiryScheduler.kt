package com.unscroll.app.service

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.unscroll.app.data.blocking.LimitRepository
import com.unscroll.app.domain.ApplicationScope
import com.unscroll.app.domain.time.Clock
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Turns timed quick blocks ("Block entire app" / "Block reels only" for 15 min to 2 hours) off
 * when they run out, even if Unscroll is closed.
 *
 * Enforcement never depends on this: BlockEvaluator compares every block with the clock, so a
 * block ends on time after process death too. This keeps the stored state and the UI in step:
 * [sync] clears blocks that ran out and sets one alarm for the next end. It runs on every change
 * to the stored limits ([start]), when the app comes to the foreground, on screen-on (while
 * tracking runs), after a reboot or update, and when the alarm fires.
 *
 * The alarm is exact where Android allows it without asking the user (below Android 12, or when
 * exact alarms are already permitted); otherwise it is an inexact allow-while-idle alarm, which
 * Android may delay a little. No permission is added for it.
 */
@Singleton
class BlockExpiryScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val limits: LimitRepository,
    private val clock: Clock,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val mutex = Mutex()

    /** Follows every change to the stored limits. Call once, from the Application. */
    fun start() {
        scope.launch { limits.observeLimits().collect { sync() } }
    }

    fun syncAsync() {
        scope.launch { sync() }
    }

    suspend fun sync() = mutex.withLock {
        val next = try {
            limits.clearExpiredBlocks(clock.now())
        } catch (e: RuntimeException) {
            // A database error must never crash a receiver; the next sync tries again.
            Log.w(TAG, "Could not check timed blocks", e)
            return@withLock
        }
        schedule(next)
    }

    @SuppressLint("MissingPermission", "ScheduleExactAlarm")
    private fun schedule(at: Long?) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, BlockExpiryReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (at == null) {
            alarms.cancel(intent)
            return
        }
        val exact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) alarms.canScheduleExactAlarms() else true
        try {
            if (exact) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
            } else {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
            }
        } catch (e: SecurityException) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
        }
    }

    private companion object {
        const val TAG = "BlockExpiry"
        const val REQUEST_CODE = 9
    }
}

/** The alarm set by [BlockExpiryScheduler]: a timed block ran out. */
class BlockExpiryReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface BlockExpiryEntryPoint {
        fun blockExpiryScheduler(): BlockExpiryScheduler

        @ApplicationScope
        fun applicationScope(): CoroutineScope
    }

    override fun onReceive(context: Context, intent: Intent) {
        val entryPoint = EntryPointAccessors.fromApplication(context.applicationContext, BlockExpiryEntryPoint::class.java)
        val pendingResult = goAsync()
        entryPoint.applicationScope().launch {
            try {
                entryPoint.blockExpiryScheduler().sync()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
