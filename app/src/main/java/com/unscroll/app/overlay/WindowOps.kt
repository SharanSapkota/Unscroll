package com.unscroll.app.overlay

import android.os.Looper
import android.view.View
import android.view.WindowManager
import com.unscroll.app.util.AppLog

/**
 * The one place Unscroll talks to WindowManager (the pill, the cover, the tint, and the
 * accessibility-overlay windows of section blocking). Main thread only: a call from another thread
 * is refused and logged. Every call is idempotent and never throws: adding a view that is already
 * added does nothing, removing one that isn't there does nothing, and errors (bad token, revoked
 * permission, a dead window) are logged and reported as `false`.
 */
internal object WindowOps {
    private const val TAG = "WindowOps"

    /** Views added and not removed yet. Main thread only. */
    private val added = HashSet<View>()

    fun isAdded(view: View): Boolean = view in added

    fun add(manager: WindowManager, view: View, params: WindowManager.LayoutParams, tag: String): Boolean {
        if (!onMainThread("add", tag)) return false
        if (view in added) return true
        return try {
            manager.addView(view, params)
            added += view
            true
        } catch (e: WindowManager.BadTokenException) {
            AppLog.w(tag, "Window not shown: bad token", e)
            false
        } catch (e: SecurityException) {
            AppLog.w(tag, "Window not shown: permission missing", e)
            false
        } catch (e: IllegalStateException) {
            AppLog.w(tag, "Window not shown", e)
            false
        } catch (e: IllegalArgumentException) {
            AppLog.w(tag, "Window not shown", e)
            false
        }
    }

    fun update(manager: WindowManager, view: View, params: WindowManager.LayoutParams, tag: String): Boolean {
        if (!onMainThread("update", tag) || view !in added) return false
        return try {
            manager.updateViewLayout(view, params)
            true
        } catch (e: IllegalArgumentException) {
            // Not attached (removed by the system): forget it so the next add works.
            AppLog.w(tag, "Window update failed", e)
            added -= view
            false
        } catch (e: IllegalStateException) {
            AppLog.w(tag, "Window update failed", e)
            false
        }
    }

    /** Removes [view] even if it isn't attached yet (added a frame ago), so it can't stay up. */
    fun remove(manager: WindowManager, view: View, tag: String) {
        if (!onMainThread("remove", tag) || view !in added) return
        added -= view
        try {
            manager.removeViewImmediate(view)
        } catch (e: IllegalArgumentException) {
            AppLog.w(tag, "Window was not attached", e)
        } catch (e: IllegalStateException) {
            AppLog.w(tag, "Window could not be removed", e)
        }
    }

    private fun onMainThread(call: String, tag: String): Boolean {
        val main = Looper.myLooper() == Looper.getMainLooper()
        if (!main) AppLog.w(TAG, "$call() for $tag off the main thread, refused")
        return main
    }
}
