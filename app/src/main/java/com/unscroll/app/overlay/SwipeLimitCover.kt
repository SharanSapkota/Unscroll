package com.unscroll.app.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.view.KeyEvent
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
 * every touch; nothing reaches the app underneath. It is focusable so Back is swallowed and the
 * "I need access" phrase can be typed. The system Home and Recents buttons still work, and "Go home"
 * always does: the user is never trapped. Main thread only; thin on purpose, the decisions live in
 * SwipeLimitEnforcer.
 */
@Singleton
class SwipeLimitCover @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val windowManager: WindowManager? = context.getSystemService(WindowManager::class.java)
    private var root: FrameLayout? = null
    private var owner: OverlayLifecycleOwner? = null
    private var state by mutableStateOf<SwipeCoverState?>(null)

    var onGoHome: () -> Unit = {}
    var onAccessGranted: (packageName: String) -> Unit = {}

    val isShowing: Boolean get() = root?.isAttachedToWindow == true

    /** The package currently covered, or null. */
    val coveredPackage: String? get() = if (isShowing) state?.packageName else null

    /** Shows (or updates) the cover. Throws if the overlay permission is missing; the caller handles it. */
    fun show(newState: SwipeCoverState) {
        state = newState
        if (isShowing) return
        val manager = windowManager ?: return
        val lifecycleOwner = OverlayLifecycleOwner()
        val compose = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
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
        val frame = BackSwallowingFrame(context).apply { addView(compose) }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // Touchable and focusable on purpose: every touch and Back land here, not in the app.
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.OPAQUE,
        ).apply {
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        lifecycleOwner.onCreate()
        try {
            manager.addView(frame, params)
        } catch (e: RuntimeException) {
            lifecycleOwner.onDestroy()
            throw e
        }
        root = frame
        owner = lifecycleOwner
    }

    /** Removes the cover. Safe to call when nothing is shown. */
    fun hide() {
        val frame = root ?: return
        if (frame.isAttachedToWindow) windowManager?.removeViewImmediate(frame)
        owner?.onDestroy()
        root = null
        owner = null
        state = null
    }

    /** Back does nothing on the cover (like the block screen); Home and Recents are system buttons. */
    private class BackSwallowingFrame(context: Context) : FrameLayout(context) {
        override fun dispatchKeyEvent(event: KeyEvent): Boolean =
            if (event.keyCode == KeyEvent.KEYCODE_BACK) true else super.dispatchKeyEvent(event)
    }
}
