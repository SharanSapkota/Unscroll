package com.unscroll.app.domain.blocking

/** What SwipeLimitEnforcer should do with the swipe-limit cover. */
sealed interface CoverAction {
    /** Add the cover over [packageName]. */
    data class Show(val packageName: String) : CoverAction

    /** No overlay permission (or adding the window failed): Home plus the block screen. */
    data class Fallback(val packageName: String) : CoverAction

    /** Take the cover down. */
    data object Hide : CoverAction

    data object None : CoverAction
}

/**
 * The show/hide rules for the swipe-limit cover, kept pure so they can be tested. Not thread-safe;
 * SwipeLimitEnforcer calls it under its mutex.
 *
 * - The cover is added once per visit: while it is up for an app, more swipe or window events for
 *   that app do nothing (no second window, no re-trigger on every swipe).
 * - It comes down when the limit no longer holds (raised, removed, extension, new window) or the
 *   app leaves the foreground, and goes up again when the app comes back over its limit.
 * - Without the overlay permission the fallback (Home plus the block screen) also fires once per
 *   visit, not on every event.
 */
class SwipeCoverTracker {

    /** The app the cover is up for, or null. */
    var shownFor: String? = null
        private set

    /** The app the fallback already fired for during this visit, or null. */
    var fallbackFor: String? = null
        private set

    /** A fresh swipe-limit status for [packageName], which is (about to be) on screen. */
    fun onStatus(packageName: String, reached: Boolean, canDrawOverlays: Boolean): CoverAction {
        if (!reached) {
            if (fallbackFor == packageName) fallbackFor = null
            return if (shownFor == packageName) hidden() else CoverAction.None
        }
        if (shownFor == packageName || fallbackFor == packageName) return CoverAction.None
        return if (canDrawOverlays) CoverAction.Show(packageName) else fallback(packageName)
    }

    /** The foreground app changed to [packageName] (null: none, e.g. home or screen off). */
    fun onForeground(packageName: String?): CoverAction {
        if (fallbackFor != packageName) fallbackFor = null
        val shown = shownFor ?: return CoverAction.None
        return if (shown != packageName) hidden() else CoverAction.None
    }

    /** The window for [packageName] was added. */
    fun onShown(packageName: String) {
        shownFor = packageName
    }

    /** Adding the window failed (permission revoked in between, bad token): fall back instead. */
    fun onShowFailed(packageName: String): CoverAction {
        if (shownFor == packageName) shownFor = null
        return fallback(packageName)
    }

    /** The cover was taken down from outside ("Go home", service stopped). */
    fun onHidden() {
        shownFor = null
    }

    private fun hidden(): CoverAction {
        shownFor = null
        return CoverAction.Hide
    }

    private fun fallback(packageName: String): CoverAction {
        fallbackFor = packageName
        return CoverAction.Fallback(packageName)
    }
}
