package com.unscroll.app.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.unscroll.app.data.appearance.AppearancePreferences
import com.unscroll.app.domain.overlay.CoverReason
import com.unscroll.app.ui.fox.ProvideFoxSettings
import com.unscroll.app.ui.theme.UnscrollTheme
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** What the limit cover shows. */
data class LimitCoverState(
    val packageName: String,
    val appName: String,
    val reason: CoverReason,
    /** Swipes in the swipe limit's window (shown for [CoverReason.SWIPE_LIMIT]). */
    val swipes: Int,
    /** Time today: in Reels for Instagram/TikTok, in the app for every other app. */
    val todayMillis: Long,
    /** "I need access" is offered: +20 swipes (swipe limit, if the user allowed it) or +5 min (time limit). */
    val accessAllowed: Boolean,
    /** Instagram/TikTok: the cover only sits over Reels and offers "Take me back to chat". */
    val reelsApp: Boolean,
    /** TikTok says "For You" where Instagram says "Reels". */
    val forYou: Boolean = false,
)

/**
 * The cover shown when a limit is reached: a full-screen TYPE_APPLICATION_OVERLAY window owned by
 * OverlayCoordinator (the only class that shows or hides it). It is touchable, so no touch reaches
 * the covered screen, and it can never trap the user:
 * - Instagram/TikTok: it is only ever up while Reels / For You is on screen, it is not focusable
 *   (system Back goes to the app, which leaves Reels and takes the cover down), and it offers
 *   "Take me back to chat" (GLOBAL_ACTION_BACK) and "Go home".
 * - Every other app: as before (the swipe-limit cover), focusable so Back is swallowed, with
 *   "Go home". Home and Recents always work.
 *
 * One window at most ([ComposeOverlayWindow]); a second [show] only changes what it says.
 */
@Singleton
class LimitCover @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appearancePreferences: AppearancePreferences,
) {
    private val window = ComposeOverlayWindow(TAG)
    private val windowContext: Context by lazy { context.overlayWindowContext() }
    private var state by mutableStateOf<LimitCoverState?>(null)

    var onGoHome: () -> Unit = {}
    var onBackToChat: () -> Unit = {}
    var onAccess: (LimitCoverState) -> Unit = {}

    val isShowing: Boolean get() = window.isShowing

    /** Shows the cover or updates what it says. Main thread. Returns false if it could not be added. */
    fun show(newState: LimitCoverState): Boolean {
        if (isShowing) {
            if (state != newState) state = newState
            return true
        }
        state = newState
        val added = window.add(windowContext, coverParams(focusable = !newState.reelsApp), swallowBack = !newState.reelsApp) {
            state?.let { current ->
                UnscrollTheme(darkTheme = true) {
                    ProvideFoxSettings(appearancePreferences.fox) {
                        LimitCoverContent(
                            state = current,
                            onGoHome = { onGoHome() },
                            onBackToChat = { onBackToChat() },
                            onAccess = { onAccess(current) },
                        )
                    }
                }
            }
        }
        if (!added) state = null
        return added
    }

    /** Removes the cover. Main thread; safe when nothing is shown or twice. */
    fun hide() {
        window.remove()
        state = null
    }

    private fun coverParams(focusable: Boolean) = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        // Touchable on purpose: no touch reaches the covered screen. Not focusable over
        // Instagram/TikTok, so system Back always reaches the app (back to chat).
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            (if (focusable) 0 else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE),
        PixelFormat.OPAQUE,
    )

    private companion object {
        const val TAG = "LimitCover"
    }
}
