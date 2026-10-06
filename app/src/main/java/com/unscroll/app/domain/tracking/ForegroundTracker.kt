package com.unscroll.app.domain.tracking

enum class ForegroundEventType { RESUMED, PAUSED }

/** An activity moving to or from the foreground, as reported by UsageStatsManager. */
data class ForegroundEvent(
    val packageName: String,
    val type: ForegroundEventType,
    val timestamp: Long,
)

/**
 * Works out the foreground app from a stream of usage events.
 *
 * Usage events can show up a little after they happen, so callers query overlapping time windows.
 * Events older than the last one already processed are skipped so the overlap is harmless.
 */
class ForegroundTracker {

    var foregroundPackage: String? = null
        private set

    private var lastEventTime = Long.MIN_VALUE

    /** Applies [events] (oldest first) and returns the resulting foreground package. */
    fun process(events: List<ForegroundEvent>): String? {
        for (event in events) {
            if (event.timestamp < lastEventTime) continue
            lastEventTime = event.timestamp
            foregroundPackage = when (event.type) {
                ForegroundEventType.RESUMED -> event.packageName
                // A pause is only meaningful for the app we think is in front. Moving between two
                // apps pauses the old one after the new one resumes on some versions.
                ForegroundEventType.PAUSED ->
                    if (event.packageName == foregroundPackage) null else foregroundPackage
            }
        }
        return foregroundPackage
    }

    fun reset() {
        foregroundPackage = null
        lastEventTime = Long.MIN_VALUE
    }
}
