package com.unscroll.app.domain.scroll

import com.unscroll.app.domain.time.Clock

/**
 * The numeric position values of one TYPE_VIEW_SCROLLED event (AccessibilityEvent.fromIndex and
 * toIndex: the first and last item the scrolled view shows), or -1 where the view reports none.
 * Numbers only: nothing from the screen's content.
 */
data class ScrollPosition(val fromIndex: Int = NONE, val toIndex: Int = NONE) {
    /**
     * The page a full-screen pager (Reels, TikTok, Shorts) rests on: exactly one item fills the
     * view. Null while two pages are on screen (mid-swipe), or for lists showing several items.
     */
    val settledPage: Int? get() = fromIndex.takeIf { it >= 0 && it == toIndex }

    companion object {
        const val NONE = -1
        val UNKNOWN = ScrollPosition()
    }
}

/**
 * Turns raw scroll events into swipes. One fling makes a burst of TYPE_VIEW_SCROLLED events; every
 * event that comes within [burstGapMillis] of the previous one belongs to the same swipe.
 *
 * Full-screen video pagers (Instagram Reels, TikTok, YouTube Shorts) need one more rule: people
 * swipe again the moment a video lands, so the scroll events of several swipes can run together
 * without a pause. There, each time the pager lands on another page within a burst counts as a new
 * swipe ([ScrollPosition.settledPage]); the first page a burst lands on belongs to the swipe that
 * started the burst.
 *
 * Only the event's package name, time and position numbers are used. Events while the screen is
 * off, or from apps that aren't counted ([isCounted]: tracked, active, with "Count swipes" on), are
 * ignored and end the current burst. Not thread-safe: the accessibility service calls it from the
 * main thread only.
 */
class SwipeDetector(
    private val clock: Clock,
    private val isCounted: (String) -> Boolean,
    private val burstGapMillis: Long = SWIPE_BURST_GAP_MILLIS,
) {
    private var lastPackage: String? = null
    private var lastEventAt: Long? = null

    /** The last page this burst landed on, or null before it landed on one. */
    private var burstPage: Int? = null

    /** Returns true if this scroll event starts a new swipe. */
    fun onScrollEvent(
        packageName: String?,
        screenOn: Boolean,
        position: ScrollPosition = ScrollPosition.UNKNOWN,
    ): Boolean {
        if (!screenOn || packageName == null || !isCounted(packageName)) {
            reset()
            return false
        }
        val now = clock.now()
        val previous = lastEventAt
        val sameBurst = packageName == lastPackage && previous != null && now - previous in 0..burstGapMillis
        lastPackage = packageName
        lastEventAt = now
        val page = position.settledPage
        if (!sameBurst) {
            burstPage = page
            return true
        }
        if (page == null) return false
        val landedBefore = burstPage
        burstPage = page
        // Landed on another page without a pause: the next swipe in a run of quick swipes.
        return landedBefore != null && page != landedBefore
    }

    /** A different window came up (or the screen turned off): the next scroll is a new swipe. */
    fun reset() {
        lastPackage = null
        lastEventAt = null
        burstPage = null
    }

    companion object {
        /** Scroll events closer together than this are one swipe. */
        const val SWIPE_BURST_GAP_MILLIS = 300L
    }
}
