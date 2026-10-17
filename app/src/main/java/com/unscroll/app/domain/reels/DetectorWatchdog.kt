package com.unscroll.app.domain.reels

/** What the screen-check watchdog should do on a tick. */
enum class WatchdogAction {
    NONE,

    /** No check for a while (events stopped coming): read the screen once. */
    RECHECK,

    /**
     * No reading for [DetectorWatchdog.STALE_MILLIS] although a state is confirmed (checks keep
     * being skipped or failing): forget the state. The app reads as "no reading yet", which shows
     * no cover (fail open), until a fresh reading confirms a state again.
     */
    RESET,
}

/**
 * Keeps the Reels detector honest while Instagram or TikTok is on screen (pure, times passed in).
 * Accessibility events are what normally trigger screen checks; if they stop (an OEM throttling
 * the service, a stuck window), a confirmed state could otherwise stay forever, and a cover with
 * it. ScrollAccessibilityService ticks it every [TICK_MILLIS] for the app on screen.
 */
object DetectorWatchdog {
    const val TICK_MILLIS = 1_000L

    /** No reading for this long: the confirmed state is no longer trusted. */
    const val STALE_MILLIS = 3_000L

    fun action(lastCheckAt: Long?, lastReadingAt: Long?, hasConfirmed: Boolean, now: Long): WatchdogAction = when {
        hasConfirmed && (lastReadingAt == null || now - lastReadingAt >= STALE_MILLIS) -> WatchdogAction.RESET
        lastCheckAt == null || now - lastCheckAt >= TICK_MILLIS -> WatchdogAction.RECHECK
        else -> WatchdogAction.NONE
    }
}
