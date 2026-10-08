package com.unscroll.app.domain.scroll

import com.unscroll.app.domain.time.Clock

/**
 * Turns raw scroll events into swipes. One fling makes a burst of TYPE_VIEW_SCROLLED events; every
 * event that comes within [burstGapMillis] of the previous one belongs to the same swipe.
 *
 * Only the event's package name and time are used. Events while the screen is off, or from apps
 * that aren't counted ([isCounted]: tracked, active, with "Count swipes" on), are ignored and end
 * the current burst. Not thread-safe: the accessibility service calls it from the main thread only.
 */
class SwipeDetector(
    private val clock: Clock,
    private val isCounted: (String) -> Boolean,
    private val burstGapMillis: Long = SWIPE_BURST_GAP_MILLIS,
) {
    private var lastPackage: String? = null
    private var lastEventAt: Long? = null

    /** Returns true if this scroll event starts a new swipe. */
    fun onScrollEvent(packageName: String?, screenOn: Boolean): Boolean {
        if (!screenOn || packageName == null || !isCounted(packageName)) {
            reset()
            return false
        }
        val now = clock.now()
        val previous = lastEventAt
        val sameBurst = packageName == lastPackage && previous != null && now - previous in 0..burstGapMillis
        lastPackage = packageName
        lastEventAt = now
        return !sameBurst
    }

    /** A different window came up (or the screen turned off): the next scroll is a new swipe. */
    fun reset() {
        lastPackage = null
        lastEventAt = null
    }

    companion object {
        /** Scroll events closer together than this are one swipe. */
        const val SWIPE_BURST_GAP_MILLIS = 300L
    }
}
