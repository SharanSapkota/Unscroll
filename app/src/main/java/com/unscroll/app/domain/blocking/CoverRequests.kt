package com.unscroll.app.domain.blocking

import com.unscroll.app.domain.overlay.CoverReason
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The limits that currently hold per app, reported by the enforcers (BlockEnforcer: time limit
 * and schedule for Reels-capable apps; SwipeLimitEnforcer: swipe limits), merged into one cover
 * reason per app with [LimitCoverRules] (the first one reached wins). OverlayCoordinator reads
 * [reasons] and decides whether the cover may show (only in Reels for Instagram and TikTok).
 *
 * A request is only a wish: nothing here touches a window. Thread-safe.
 */
@Singleton
class CoverRequests @Inject constructor() {
    private val lock = Any()
    private val holding = HashMap<String, MutableSet<CoverReason>>()
    private val _reasons = MutableStateFlow<Map<String, CoverReason>>(emptyMap())

    /** Per app, the reason its cover would show, if any. */
    val reasons: StateFlow<Map<String, CoverReason>> = _reasons.asStateFlow()

    /** [reason] holds (or no longer holds) for [packageName]. */
    fun set(packageName: String, reason: CoverReason, holds: Boolean) = synchronized(lock) {
        val current = holding[packageName]
        if (holds == (current?.contains(reason) == true)) return@synchronized
        if (holds) {
            holding.getOrPut(packageName) { mutableSetOf() } += reason
        } else {
            current?.remove(reason)
            if (current?.isEmpty() == true) holding.remove(packageName)
        }
        publish(packageName)
    }

    /** Nothing holds for [packageName] any more (it left, or its limits were lifted). */
    fun clear(packageName: String) = synchronized(lock) {
        if (holding.remove(packageName) != null) publish(packageName)
    }

    fun clearAll() = synchronized(lock) {
        holding.clear()
        _reasons.value = emptyMap()
    }

    private fun publish(packageName: String) {
        val now = holding[packageName].orEmpty()
        val reason = LimitCoverRules.reason(
            current = _reasons.value[packageName],
            timeReached = CoverReason.TIME_LIMIT in now,
            swipeReached = CoverReason.SWIPE_LIMIT in now,
            inSchedule = CoverReason.SCHEDULE in now,
        )
        _reasons.value = if (reason == null) _reasons.value - packageName else _reasons.value + (packageName to reason)
    }
}
