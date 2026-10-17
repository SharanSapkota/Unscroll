package com.unscroll.app.domain.reels

/**
 * Debounces the detector's readings for one app so the pill doesn't flicker (pure, times passed
 * in).
 *
 * - The first known reading of a visit (IN_REELS or OTHER) is confirmed at once, so the pill
 *   appears without delay when the app opens on Reels.
 * - A change between known states is confirmed only once the new state has been read for
 *   [CONFIRM_MILLIS] without a different known reading in between.
 * - UNKNOWN readings (a frame mid-transition, a tree that is being rebuilt while scrolling) are
 *   ignored: the confirmed state stays. Only when every reading has been UNKNOWN for longer than
 *   [UNKNOWN_FALLBACK_MILLIS] does the confirmed state become UNKNOWN (the timer falls back).
 *
 * [observe] returns the delay after which the caller should read the screen again to finish a
 * pending confirmation or the UNKNOWN timeout, or null when nothing is pending.
 */
class ReelsHysteresis {

    /** The confirmed state, or null before the first known reading of this visit. */
    var confirmed: ReelsState? = null
        private set

    /** When the confirmed state began: the first reading of it, not the confirmation. */
    var confirmedSince: Long = 0
        private set

    private var candidate: ReelsState? = null
    private var candidateSince: Long = 0
    private var unknownSince: Long? = null

    fun observe(reading: ReelsState, now: Long): Long? {
        if (reading == ReelsState.UNKNOWN) {
            val since = unknownSince ?: now.also { unknownSince = it }
            val waited = now - since
            if (waited >= UNKNOWN_FALLBACK_MILLIS) {
                if (confirmed != ReelsState.UNKNOWN) confirm(ReelsState.UNKNOWN, since)
                return null
            }
            return UNKNOWN_FALLBACK_MILLIS - waited
        }
        unknownSince = null
        val current = confirmed
        if (current == null) {
            // First reading of the visit: nothing on screen yet that could flicker.
            confirm(reading, now)
            return null
        }
        if (reading == current) {
            candidate = null
            return null
        }
        if (candidate != reading) {
            candidate = reading
            candidateSince = now
        }
        val waited = now - candidateSince
        if (waited >= CONFIRM_MILLIS) {
            confirm(reading, candidateSince)
            return null
        }
        return CONFIRM_MILLIS - waited
    }

    /** A new visit: forget everything. */
    fun reset() {
        confirmed = null
        confirmedSince = 0
        candidate = null
        unknownSince = null
    }

    private fun confirm(state: ReelsState, since: Long) {
        confirmed = state
        confirmedSince = since
        candidate = null
    }

    companion object {
        /** A change must be read this long before it counts (within the asked 300–500 ms). */
        const val CONFIRM_MILLIS = 400L

        /** UNKNOWN for longer than this: fall back to the whole-app timer. */
        const val UNKNOWN_FALLBACK_MILLIS = 3_000L

        /** Screen checks are throttled to at most one per this interval. */
        const val CHECK_INTERVAL_MILLIS = 300L
    }
}
