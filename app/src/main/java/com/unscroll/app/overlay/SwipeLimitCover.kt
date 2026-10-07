package com.unscroll.app.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
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
) {
    private val windowManager: WindowManager? = context.getSystemService(WindowManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var root: FrameLayout? = null
    private var compose: ComposeView? = null
    private var owner: OverlayLifecycleOwner? = null
    private var state by mutableStateOf<SwipeCoverState?>(null)

    var onGoHome: () -> Unit = {}
    var onAccessGranted: (packageName: String) -> Unit = {}

    /** Called (main thread) when a [show] that had to be posted to the main thread failed. */
    var onShowFailed: (packageName: String) -> Unit = {}

    /** True from a successful add until [hide]. */
    var isShowing: Boolean = false
        private set

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
        val manager = windowManager ?: return false
        if (!Settings.canDrawOverlays(context)) return false
        state = newState
        val lifecycleOwner = OverlayLifecycleOwner()
        val composeView = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                state?.let { current ->
                    UnscrollTheme {
                        SwipeLimitCoverContent(
                            state = current,
                            onGoHome = { onGoHome() },
                            onAccessGranted = { onAccessGranted(current.packageName) },
                        )
                    }
                }
            }
        }
        // Compose looks the owners up from the window's root view when it attaches, so they must
        // sit on the frame that is added to the WindowManager (owners only on the ComposeView
        // inside it crash with "ViewTreeLifecycleOwner not found"). Set on both, before adding.
        val frame = BackSwallowingFrame(context).apply {
            setOwners(lifecycleOwner)
            addView(composeView)
        }
        composeView.setOwners(lifecycleOwner)
        lifecycleOwner.onCreate()
        val added = try {
            manager.addView(frame, coverParams())
            true
        } catch (e: WindowManager.BadTokenException) {
            Log.w(TAG, "Swipe-limit cover not shown", e)
            false
        } catch (e: SecurityException) {
            Log.w(TAG, "Swipe-limit cover not shown", e)
            false
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Swipe-limit cover not shown", e)
            false
        }
        if (!added) {
            composeView.disposeComposition()
            lifecycleOwner.onDestroy()
            state = null
            return false
        }
        root = frame
        compose = composeView
        owner = lifecycleOwner
        isShowing = true
        return true
    }

    /** Removes the cover. Safe to call when nothing is shown, twice, or off the main thread. */
    fun hide() {
        if (!isMainThread()) {
            mainHandler.post { hide() }
            return
        }
        val frame = root
        if (frame != null) {
            try {
                // Added but maybe not attached yet: remove it either way, or it would stay up.
                windowManager?.removeViewImmediate(frame)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Swipe-limit cover was not attached", e)
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Swipe-limit cover could not be removed", e)
            }
        }
        compose?.disposeComposition()
        owner?.onDestroy()
        root = null
        compose = null
        owner = null
        isShowing = false
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

    private fun View.setOwners(owner: OverlayLifecycleOwner) {
        setViewTreeLifecycleOwner(owner)
        setViewTreeViewModelStoreOwner(owner)
        setViewTreeSavedStateRegistryOwner(owner)
    }

    private fun isMainThread() = Looper.myLooper() == Looper.getMainLooper()

    /** Back does nothing on the cover (like the block screen); Home and Recents are system buttons. */
    private class BackSwallowingFrame(context: Context) : FrameLayout(context) {
        override fun dispatchKeyEvent(event: KeyEvent): Boolean =
            if (event.keyCode == KeyEvent.KEYCODE_BACK) true else super.dispatchKeyEvent(event)
    }

    private companion object {
        const val TAG = "SwipeLimitCover"
    }
}
