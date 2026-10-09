package com.unscroll.app.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.view.WindowManager
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.unscroll.app.R
import com.unscroll.app.domain.fox.FoxSettings
import com.unscroll.app.domain.section.BlockedSection
import com.unscroll.app.domain.tracking.DefaultTrackedApps
import com.unscroll.app.ui.components.StopScreen
import com.unscroll.app.ui.fox.ProvideFoxSettings
import com.unscroll.app.ui.section.coverBackRes
import com.unscroll.app.ui.section.coverOpenRes
import com.unscroll.app.ui.section.coverTitleRes
import com.unscroll.app.ui.theme.UnscrollTheme
import kotlinx.coroutines.flow.Flow

/** What the section cover shows. */
data class SectionCoverState(val packageName: String, val section: BlockedSection)

/**
 * Covers a blocked section (Reels, For You, Shorts) the moment it is detected: the calm dark
 * [StopScreen] with the concerned fox, "Reels are blocked. Chat is open." and "Take me back to
 * chat" (Back for the app), plus "Go home".
 *
 * A TYPE_ACCESSIBILITY_OVERLAY window of the section-blocking service ([context] must be that
 * service): it needs no overlay permission and disappears with the service, so it can never
 * outlive it. It fills the screen and is touchable, so taps don't reach the section, but it is
 * NOT focusable: the system Back, Home and Recents keep working, and Back goes to the app, which
 * leaves the section. Main thread only.
 */
internal class SectionCover(
    private val context: Context,
    private val fox: Flow<FoxSettings>,
) {
    private val window = ComposeOverlayWindow(TAG)
    private var state by mutableStateOf<SectionCoverState?>(null)

    var onBack: () -> Unit = {}
    var onGoHome: () -> Unit = {}

    /** The package currently covered, or null. */
    val coveredPackage: String? get() = if (window.isShowing) state?.packageName else null

    /** Shows the cover (or updates it). Returns false if the system refused the window. */
    fun show(newState: SectionCoverState): Boolean {
        if (window.isShowing) {
            if (state != newState) state = newState
            return true
        }
        state = newState
        val added = window.add(context, params()) {
            state?.let { current ->
                UnscrollTheme(darkTheme = true) {
                    ProvideFoxSettings(fox) {
                        SectionCoverContent(current, onBack = { onBack() }, onGoHome = { onGoHome() })
                    }
                }
            }
        }
        if (!added) state = null
        return added
    }

    fun hide() {
        window.remove()
        state = null
    }

    private fun params() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        // Touchable (taps stay here) but not focusable: system Back, Home and Recents always work.
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.OPAQUE,
    )

    private companion object {
        const val TAG = "SectionCover"
    }
}

@Composable
internal fun SectionCoverContent(state: SectionCoverState, onBack: () -> Unit, onGoHome: () -> Unit) {
    StopScreen(
        packageName = state.packageName,
        headline = stringResource(state.section.coverTitleRes),
        number = null,
        numberLabel = stringResource(state.section.coverOpenRes),
        goHomeLabel = stringResource(state.section.coverBackRes),
        onGoHome = onBack,
    ) {
        TextButton(onClick = onGoHome) { Text(stringResource(R.string.action_go_home)) }
    }
}

@PreviewLightDark
@Composable
private fun SectionCoverPreview() {
    UnscrollTheme(darkTheme = true) {
        SectionCoverContent(SectionCoverState(DefaultTrackedApps.INSTAGRAM, BlockedSection.REELS), onBack = {}, onGoHome = {})
    }
}
