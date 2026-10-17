package com.unscroll.app.overlay

import android.content.Context
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.unscroll.app.util.AppLog

/**
 * One Compose window added straight to the WindowManager, outside any Activity: the swipe-limit
 * cover, the section-blocking cover and the debug Section Inspector button.
 *
 * Main thread only ([add] returns false elsewhere). One window at most: [isShowing] is set as
 * soon as the window is added (not when it attaches, a frame later). Adding never throws: a
 * refused window is logged and reported as `false`. WindowManager calls go through [WindowOps].
 * On [remove] the composition is disposed and the lifecycle owner destroyed, so nothing leaks.
 */
internal class ComposeOverlayWindow(private val tag: String) {
    private var windowManager: WindowManager? = null
    private var root: FrameLayout? = null
    private var compose: ComposeView? = null
    private var owner: OverlayLifecycleOwner? = null

    /** True from a successful [add] until [remove]. */
    var isShowing: Boolean = false
        private set

    /**
     * Adds the window with [params] through [context]'s WindowManager (an AccessibilityService
     * context for TYPE_ACCESSIBILITY_OVERLAY). [swallowBack] makes Back do nothing in a focusable
     * window. Returns true if it is showing.
     */
    fun add(
        context: Context,
        params: WindowManager.LayoutParams,
        swallowBack: Boolean = false,
        content: @Composable () -> Unit,
    ): Boolean {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            AppLog.w(tag, "add() off the main thread")
            return false
        }
        if (isShowing) return true
        val manager = context.getSystemService(WindowManager::class.java) ?: return false
        val lifecycleOwner = OverlayLifecycleOwner()
        val composeView = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent(content)
        }
        // Compose looks the owners up from the window's root view when it attaches, so they must
        // sit on the frame that is added to the WindowManager (owners only on the ComposeView
        // inside it crash with "ViewTreeLifecycleOwner not found"). Set on both, before adding.
        val frame = KeyFrame(context, swallowBack).apply {
            setOwners(lifecycleOwner)
            addView(composeView)
        }
        composeView.setOwners(lifecycleOwner)
        lifecycleOwner.onCreate()
        val added = WindowOps.add(manager, frame, params, tag)
        if (!added) {
            composeView.disposeComposition()
            lifecycleOwner.onDestroy()
            return false
        }
        windowManager = manager
        root = frame
        compose = composeView
        owner = lifecycleOwner
        isShowing = true
        return true
    }

    /** Removes the window. Main thread; safe when nothing is shown or twice. */
    fun remove() {
        val frame = root
        val manager = windowManager
        // Added but maybe not attached yet: remove it either way, or it would stay up.
        if (frame != null && manager != null) WindowOps.remove(manager, frame, tag)
        compose?.disposeComposition()
        owner?.onDestroy()
        windowManager = null
        root = null
        compose = null
        owner = null
        isShowing = false
    }

    private fun View.setOwners(owner: OverlayLifecycleOwner) {
        setViewTreeLifecycleOwner(owner)
        setViewTreeViewModelStoreOwner(owner)
        setViewTreeSavedStateRegistryOwner(owner)
    }

    /** With [swallowBack], Back does nothing (like the block screen); Home and Recents are system buttons. */
    private class KeyFrame(context: Context, private val swallowBack: Boolean) : FrameLayout(context) {
        override fun dispatchKeyEvent(event: KeyEvent): Boolean =
            if (swallowBack && event.keyCode == KeyEvent.KEYCODE_BACK) true else super.dispatchKeyEvent(event)
    }
}
