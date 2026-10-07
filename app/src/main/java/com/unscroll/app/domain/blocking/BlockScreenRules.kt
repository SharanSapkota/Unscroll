package com.unscroll.app.domain.blocking

/**
 * Whether an open block screen should stay. Limit changes apply at once, so the screen is checked
 * again whenever the limits change: unblocking an app, raising its limit or turning off its
 * schedule closes the screen right away.
 */
object BlockScreenRules {

    /**
     * What the screen opened for [shown] should show now, or null to close it. [decision] is the
     * time rules' decision for the current settings; [swipeLimitReached] whether the swipe limit
     * still holds. A block for another reason (the schedule after "Block completely" was turned
     * off) keeps the screen up with that reason.
     */
    fun current(shown: BlockReason, decision: BlockDecision, swipeLimitReached: Boolean): BlockDecision.Blocked? =
        when {
            decision is BlockDecision.Blocked -> decision
            shown == BlockReason.SWIPE_LIMIT_REACHED && swipeLimitReached ->
                BlockDecision.Blocked(BlockReason.SWIPE_LIMIT_REACHED, until = null)
            else -> null
        }
}
