package com.unscroll.app.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.unscroll.app.domain.overlay.PillPosition
import com.unscroll.app.domain.overlay.PillPositioner
import com.unscroll.app.domain.overlay.PillSwipes
import com.unscroll.app.domain.overlay.ScreenBounds
import com.unscroll.app.domain.fox.FoxSettings
import com.unscroll.app.ui.fox.ProvideFoxSettings
import com.unscroll.app.ui.theme.UnscrollTheme
import kotlinx.coroutines.flow.Flow
import kotlin.math.roundToInt

/**
 * One overlay window holding the pill. Deliberately thin: it only talks to WindowManager (through
 * [WindowOps]). All decisions (when to show, what to show, where it may go) are made by
 * OverlayCoordinator. Main thread only.
 *
 * Tab changes never remove the window: [setParked] only hides it in place (invisible and not
 * touchable) and brings it back. [detach] removes it even before it attached (a frame after
 * adding), disposes the composition and destroys its lifecycle owner, so nothing leaks.
 */
internal class OverlayWindow(
    private val context: Context,
    private val windowManager: WindowManager,
    initialState: OverlayUiState,
    private val now: () -> Long,
    private val onTap: () -> Unit,
    private val onMoved: (PillPosition) -> Unit,
    private val onMessageAction: (PillAction) -> Unit,
    /** The fox settings: with the fox on, the collapsed pill is a tiny fox face. */
    private val foxSettings: Flow<FoxSettings>,
) {
    private var state by mutableStateOf(initialState)
    private var swipes by mutableStateOf<PillSwipes?>(null)
    private val owner = OverlayLifecycleOwner()
    private var hasSavedPosition = false

    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        // Not focusable: keyboard and back go to the app underneath. A window that isn't
        // focusable is also not touch-modal, so touches outside the pill pass through.
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
    }

    private val view = ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        setViewTreeLifecycleOwner(owner)
        setViewTreeViewModelStoreOwner(owner)
        setViewTreeSavedStateRegistryOwner(owner)
        setContent {
            UnscrollTheme(darkTheme = true) {
                ProvideFoxSettings(foxSettings) {
                    OverlayContent(
                        state = state,
                        now = now,
                        onTap = onTap,
                        onDrag = ::dragBy,
                        onDragEnd = ::dragEnded,
                        onMessageAction = onMessageAction,
                        swipes = swipes,
                    )
                }
            }
        }
        // The pill changes size (collapsed, today's total); keep it on screen when it does.
        addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            val widthChanged = right - left != oldRight - oldLeft
            val heightChanged = bottom - top != oldBottom - oldTop
            if (widthChanged || heightChanged) placeAfterLayout()
        }
    }

    /** True from a successful [attach] until [detach]. */
    val isAdded: Boolean get() = WindowOps.isAdded(view)

    private var parked = false

    /** Adds the window. Returns false if it could not be added (no permission, bad token). */
    fun attach(savedPosition: PillPosition?): Boolean {
        hasSavedPosition = savedPosition != null
        // Without a saved position, start roughly at top center; the first layout pass centers it
        // exactly once the real size is known.
        val start = savedPosition ?: run {
            val screen = windowManager.screenBounds(context)
            val estimatedWidth = dp(ESTIMATED_WIDTH_DP)
            PillPosition((screen.width - estimatedWidth) / 2, screen.insetTop + marginTop(screen))
        }
        params.x = start.x
        params.y = start.y
        applyParked()
        owner.onCreate()
        return WindowOps.add(windowManager, view, params, TAG)
    }

    /**
     * "Only on Reels" off Reels: the window stays, invisible and not touchable (touches go to the
     * app). Back on Reels it shows again in the same place. No remove, no re-add, no flicker.
     */
    fun setParked(parked: Boolean) {
        if (this.parked == parked) return
        this.parked = parked
        applyParked()
        WindowOps.update(windowManager, view, params, TAG)
    }

    private fun applyParked() {
        view.visibility = if (parked) View.INVISIBLE else View.VISIBLE
        params.flags = if (parked) params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        else params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
    }

    fun update(newState: OverlayUiState) {
        state = newState
    }

    /** Updated on every swipe, separately from [update] so the rest of the pill isn't rebuilt. */
    fun updateSwipes(info: PillSwipes?) {
        swipes = info
    }

    fun detach() {
        // Removed even if it hasn't attached yet, or a second pill could stay on screen.
        WindowOps.remove(windowManager, view, TAG)
        view.disposeComposition()
        owner.onDestroy()
    }

    /** Re-clamps after a rotation, using the position saved for the new orientation. */
    fun reposition(savedPosition: PillPosition?) {
        hasSavedPosition = savedPosition != null
        savedPosition?.let {
            params.x = it.x
            params.y = it.y
        }
        placeAfterLayout()
    }

    private fun placeAfterLayout() {
        if (view.width == 0 || !view.isAttachedToWindow) return
        val screen = windowManager.screenBounds(context)
        val position = if (hasSavedPosition) {
            PillPositioner.clamp(PillPosition(params.x, params.y), view.width, view.height, screen)
        } else {
            PillPositioner.defaultPosition(screen, view.width, view.height, marginTop(screen))
        }
        move(position)
    }

    private fun dragBy(dx: Float, dy: Float) {
        hasSavedPosition = true
        val screen = windowManager.screenBounds(context)
        move(
            PillPositioner.clamp(
                PillPosition(params.x + dx.roundToInt(), params.y + dy.roundToInt()),
                view.width,
                view.height,
                screen,
            ),
        )
    }

    private fun dragEnded() {
        onMoved(PillPosition(params.x, params.y))
    }

    private fun move(position: PillPosition) {
        if (params.x == position.x && params.y == position.y) return
        params.x = position.x
        params.y = position.y
        if (view.isAttachedToWindow) WindowOps.update(windowManager, view, params, TAG)
    }

    private fun marginTop(screen: ScreenBounds): Int =
        dp(DEFAULT_MARGIN_DP).coerceAtMost(screen.height / 4)

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).roundToInt()

    private companion object {
        const val TAG = "OverlayWindow"
        const val DEFAULT_MARGIN_DP = 8
        const val ESTIMATED_WIDTH_DP = 96
    }
}

