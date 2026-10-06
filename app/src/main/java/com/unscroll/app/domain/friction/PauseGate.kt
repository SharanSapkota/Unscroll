package com.unscroll.app.domain.friction

/**
 * Decides when the pause screen ("mindful gate") appears. Fed with every change of the tracked app
 * in the foreground. Thread-safe; the service and the pause screen both call it.
 *
 * The pause appears when the user opens a tracked app "from outside", but not:
 * - when they come back within [quickReturnMillis] of last actually using it,
 * - when the app is blocked (the block screen wins),
 * - during the first [startupGraceMillis] after tracking starts (avoids a startup loop),
 * - right after they pressed "Continue" on the pause screen for that app.
 *
 * An app only counts as "used" once the user got into it without a pause or past one, so
 * pressing "Never mind" and reopening quickly still shows the pause again.
 */
class PauseGate(
    private val quickReturnMillis: Long = 30_000,
    private val startupGraceMillis: Long = 5_000,
    private val continueGraceMillis: Long = 15_000,
) {
    private var trackingStartedAt: Long? = null
    private var activePackage: String? = null
    private val lastActiveAt = mutableMapOf<String, Long>()
    private val continuedAt = mutableMapOf<String, Long>()

    @Synchronized
    fun onTrackingStarted(now: Long) {
        trackingStartedAt = now
        activePackage = null
    }

    /**
     * [packageName] is the tracked app now in front, or null for anything else (including our own
     * pause and block screens). Returns true if the pause screen should be shown for it.
     */
    @Synchronized
    fun onForegroundChanged(
        packageName: String?,
        now: Long,
        pauseEnabled: Boolean,
        blocked: Boolean,
    ): Boolean {
        val previous = activePackage
        if (previous != null && previous != packageName) {
            lastActiveAt[previous] = now
            activePackage = null
        }
        if (packageName == null || packageName == previous) return false

        val started = trackingStartedAt
        val inStartupGrace = started == null || now - started < startupGraceMillis
        val quickReturn = lastActiveAt[packageName]?.let { now - it <= quickReturnMillis } == true
        val justContinued = continuedAt.remove(packageName)?.let { now - it <= continueGraceMillis } == true

        val showPause = pauseEnabled && !blocked && !inStartupGrace && !quickReturn && !justContinued
        if (!showPause && !blocked) {
            activePackage = packageName
            lastActiveAt[packageName] = now
        }
        return showPause
    }

    /** The user pressed "Continue to app": let the next open through. */
    @Synchronized
    fun onContinued(packageName: String, now: Long) {
        continuedAt[packageName] = now
    }
}
