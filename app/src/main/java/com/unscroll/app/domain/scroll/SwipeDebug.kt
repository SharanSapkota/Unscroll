package com.unscroll.app.domain.scroll

/**
 * Debug builds only: the last event the scroll-counting service handled, for Settings › Debug and
 * logcat (tag SwipeDebug). Holds only the event type, the package name, the outcome and the count:
 * never anything from the screen.
 */
data class SwipeDebugInfo(
    /** "TYPE_VIEW_SCROLLED", "TYPE_WINDOW_STATE_CHANGED", or null before the first event. */
    val lastEventType: String? = null,
    val packageName: String? = null,
    /** What happened to the event ([SwipeDebugOutcome]). */
    val outcome: SwipeDebugOutcome? = null,
    /** Swipes in the open session afterwards, or null without one. */
    val sessionSwipes: Int? = null,
)

enum class SwipeDebugOutcome(val label: String) {
    COUNTED("counted"),
    SAME_SWIPE("same swipe"),
    NO_CONSENT("ignored: no in-app consent"),
    UNTRACKED("ignored: app not tracked"),
    NOT_COUNTED("ignored: screen off or Count swipes off"),
    NO_SESSION("ignored: no open session for this app"),
    NOT_IN_REELS("ignored: not in Reels (Reels-only timer)"),
    WINDOW("window change"),
}

object SwipeDebugFormat {
    /** "connected yes · TYPE_VIEW_SCROLLED · com.instagram.android · counted · 86 swipes". */
    fun line(connected: Boolean, info: SwipeDebugInfo): String = buildList {
        add("connected ${if (connected) "yes" else "no"}")
        add(info.lastEventType ?: "no event yet")
        info.packageName?.let { add(it) }
        info.outcome?.let { add(it.label) }
        add(info.sessionSwipes?.let { "$it swipes" } ?: "no session")
    }.joinToString(" · ")
}
