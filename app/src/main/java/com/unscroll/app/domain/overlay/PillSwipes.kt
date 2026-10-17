package com.unscroll.app.domain.overlay

/**
 * Swipe info for the pill: the open session's count (today's earlier swipes are added from the
 * base) and swipes left near a swipe limit. Only exists while scroll counting runs
 * ([PillRules.swipes]); without it the pill shows only the fox and the time.
 */
data class PillSwipes(
    val sessionCount: Int,
    /** The user wants the count on the pill (global and per-app switch). Colors use it either way. */
    val showCount: Boolean,
    /** Swipes left under the app's swipe limit, once within the last 20 %; else null. */
    val remaining: Int? = null,
)
