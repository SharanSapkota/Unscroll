package com.unscroll.app.domain.scroll

/**
 * "Take a break after N swipes". Counts swipes since the last break in the current session; when
 * that reaches N, a break is due and the count starts again, so the next break comes after another
 * N swipes ("Keep scrolling"). A new session starts from zero. Thread-safe.
 */
class SwipeBreakTracker {
    private var sessionId: Long? = null

    /** Session swipe count at the last break (or reset). */
    private var base = 0

    /**
     * Called with the session's running swipe count. Returns true when a break is due now, and
     * then counts the next N swipes from here.
     */
    @Synchronized
    fun onSwipeCount(sessionId: Long, count: Int, breakAfter: Int?): Boolean {
        startSession(sessionId)
        if (breakAfter == null || breakAfter <= 0) return false
        if (count - base < breakAfter) return false
        base = count
        return true
    }

    /** "Keep scrolling": the next break comes after another N swipes from [count]. */
    @Synchronized
    fun onKeepScrolling(sessionId: Long, count: Int) {
        startSession(sessionId)
        base = count
    }

    /** Swipes left until the next break, or null when breaks are off. */
    @Synchronized
    fun swipesUntilBreak(sessionId: Long, count: Int, breakAfter: Int?): Int? {
        if (breakAfter == null || breakAfter <= 0) return null
        val since = if (sessionId == this.sessionId) count - base else count
        return (breakAfter - since).coerceAtLeast(0)
    }

    private fun startSession(id: Long) {
        if (id != sessionId) {
            sessionId = id
            base = 0
        }
    }

    companion object {
        val PRESETS = listOf(25, 50, 100, 200)

        /** Picked when the user switches swipe breaks on. */
        const val DEFAULT_BREAK_AFTER = 50

        /** Breathing countdown on the break screen. */
        const val BREAK_SECONDS = 15
    }
}
