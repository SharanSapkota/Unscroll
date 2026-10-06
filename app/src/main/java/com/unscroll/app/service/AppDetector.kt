package com.unscroll.app.service

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import com.unscroll.app.domain.time.Clock
import com.unscroll.app.domain.tracking.ForegroundAppDetector
import com.unscroll.app.domain.tracking.ForegroundEvent
import com.unscroll.app.domain.tracking.ForegroundEventType
import com.unscroll.app.domain.tracking.ForegroundTracker
import com.unscroll.app.domain.tracking.ScreenStateSource
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn

/**
 * Detects the foreground app by polling UsageStatsManager events about once per second, and only
 * while the screen is on. Emits null while the screen is off. Reads package names and
 * timestamps only.
 */
@Singleton
class AppDetector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val screenState: ScreenStateSource,
    private val clock: Clock,
) : ForegroundAppDetector {

    @OptIn(ExperimentalCoroutinesApi::class)
    override val foregroundApp: Flow<String?> = screenState.isScreenOn
        .flatMapLatest { isOn -> if (isOn) pollForegroundApp() else flowOf(null) }
        .distinctUntilChanged()

    private fun pollForegroundApp(): Flow<String?> = flow {
        val usageStats = context.getSystemService(UsageStatsManager::class.java)
        val tracker = ForegroundTracker()
        // First query looks back far enough to find the app that was already open.
        var since = clock.now() - INITIAL_LOOKBACK_MILLIS
        while (true) {
            val until = clock.now()
            // Overlap windows because events can be recorded slightly after they happen.
            val events = usageStats?.readForegroundEvents(since - OVERLAP_MILLIS, until).orEmpty()
            emit(tracker.process(events))
            since = until
            delay(POLL_INTERVAL_MILLIS)
        }
    }.flowOn(Dispatchers.IO)

    private fun UsageStatsManager.readForegroundEvents(
        begin: Long,
        end: Long,
    ): List<ForegroundEvent> {
        val usageEvents = queryEvents(begin, end) ?: return emptyList()
        val event = UsageEvents.Event()
        val result = mutableListOf<ForegroundEvent>()
        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            val type = when (event.eventType) {
                RESUMED -> ForegroundEventType.RESUMED
                PAUSED -> ForegroundEventType.PAUSED
                else -> continue
            }
            result += ForegroundEvent(event.packageName, type, event.timeStamp)
        }
        return result
    }

    private companion object {
        const val POLL_INTERVAL_MILLIS = 1_000L
        const val OVERLAP_MILLIS = 2_000L
        const val INITIAL_LOOKBACK_MILLIS = 60 * 60 * 1_000L

        // ACTIVITY_RESUMED/PAUSED (API 29) replaced MOVE_TO_FOREGROUND/BACKGROUND, same values.
        val RESUMED = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            UsageEvents.Event.ACTIVITY_RESUMED
        } else {
            @Suppress("DEPRECATION")
            UsageEvents.Event.MOVE_TO_FOREGROUND
        }
        val PAUSED = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            UsageEvents.Event.ACTIVITY_PAUSED
        } else {
            @Suppress("DEPRECATION")
            UsageEvents.Event.MOVE_TO_BACKGROUND
        }
    }
}
