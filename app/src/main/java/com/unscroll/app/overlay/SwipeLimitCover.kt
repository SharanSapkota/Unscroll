package com.unscroll.app.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.unscroll.app.data.appearance.AppearancePreferences
import com.unscroll.app.ui.fox.ProvideFoxSettings
import com.unscroll.app.ui.theme.UnscrollTheme
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** What the swipe-limit cover shows. */
data class SwipeCoverState(
    val packageName: String,
    val appName: String,
    val swipes: Int,
    /** Time in the app today. */
    val todayMillis: Long,
    /** "I need access" is offered (the user enabled it for this app). */
    val accessAllowed: Boolean,
)

/**
 * The hard stop when a swipe limit is reached: a full-screen TYPE_APPLICATION_OVERLAY window over
 * the tracked app. It is touchable (no FLAG_NOT_TOUCHABLE) and fills the screen, so it consumes
 * every touch; nothing reaches the app underneath. It is focusable so Back is swallowed. The
 * system Home and Recents buttons still work, and "Go home" always does: the user is never
 * trapped. Thin on purpose, the decisions live in SwipeLimitEnforcer and SwipeCoverTracker.
 *
 * One window at most: [isShowing] is set as soon as the window is added (not when it attaches,
 * which happens a frame later), so a second [show] only updates what the cover says. Adding and
 * removing always happen on the main thread, and never throw: a failure is logged and reported
 * as `false`, and the caller falls back to the block screen.
 */
@Singleton
class SwipeLimitCover @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appearancePreferences: AppearancePreferences,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val window = ComposeOverlayWindow(TAG)
    private var state by mutableStateOf<SwipeCoverState?>(null)

    var onGoHome: () -> Unit = {}
    var onAccessGranted: (packageName: String) -> Unit = {}

    /** Called (main thread) when a [show] that had to be posted to the main thread failed. */
    var onShowFailed: (packageName: String) -> Unit = {}

    /** True from a successful add until [hide]. */
    val isShowing: Boolean get() = window.isShowing

    /** The package currently covered, or null. */
    val coveredPackage: String? get() = if (isShowing) state?.packageName else null

    /**
     * Shows the cover, or updates what it says if it is already up. Returns false if it could not
     * be shown (no overlay permission, no window manager, or the system refused the window).
     * Called off the main thread, it posts itself there and reports a failure via [onShowFailed].
     */
    fun show(newState: SwipeCoverState): Boolean {
        if (!isMainThread()) {
            Log.w(TAG, "show() called off the main thread; posting")
            mainHandler.post { if (!show(newState)) onShowFailed(newState.packageName) }
            return true
        }
        if (isShowing) {
            if (state != newState) state = newState
            return true
        }
        if (!Settings.canDrawOverlays(context)) return false
        state = newState
        // Back is swallowed: the cover is focusable so it can't reach the app either.
        val added = window.add(context, coverParams(), swallowBack = true) {
            state?.let { current ->
                UnscrollTheme(darkTheme = true) {
                    ProvideFoxSettings(appearancePreferences.fox) {
                        SwipeLimitCoverContent(
                            state = current,
                            onGoHome = { onGoHome() },
                            onAccessGranted = { onAccessGranted(current.packageName) },
                        )
                    }
                }
            }
        }
        if (!added) state = null
        return added
    }

    /** Removes the cover. Safe to call when nothing is shown, twice, or off the main thread. */
    fun hide() {
        if (!isMainThread()) {
            mainHandler.post { hide() }
            return
        }
        window.remove()
        state = null
    }

    private fun coverParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        // Touchable and focusable on purpose: every touch and Back land here, not in the app.
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.OPAQUE,
    )

    private fun isMainThread() = Looper.myLooper() == Looper.getMainLooper()

    private companion object {
        const val TAG = "SwipeLimitCover"
    }
}
