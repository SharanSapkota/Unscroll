package com.unscroll.app.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.unscroll.app.R
import com.unscroll.app.domain.section.InspectorLabel
import com.unscroll.app.ui.theme.Dimens
import com.unscroll.app.ui.theme.UnscrollTheme

/**
 * Debug builds only: a small floating "Mark" button over a tracked app while the Section
 * Inspector is on. Tapping it shows Reels / Chat / Home / Search / Other; the choice is logged
 * with the identifiers of the screen underneath, so captures can be told apart in logcat.
 * A TYPE_ACCESSIBILITY_OVERLAY of the section service ([context]), wrap content and not
 * focusable: everything outside it reaches the app. Main thread only.
 */
internal class SectionInspectorButton(private val context: Context) {
    private val window = ComposeOverlayWindow(TAG)

    var onMark: (InspectorLabel) -> Unit = {}

    val isShowing: Boolean get() = window.isShowing

    fun show(): Boolean = window.isShowing || window.add(context, params()) {
        UnscrollTheme(darkTheme = true) { InspectorMarkContent(onMark = { onMark(it) }) }
    }

    fun hide() = window.remove()

    private fun params() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.BOTTOM or Gravity.START
        y = context.resources.displayMetrics.heightPixels / INSPECTOR_HEIGHT_FRACTION
    }

    private companion object {
        const val TAG = "SectionInspector"

        /** Sits a fifth of the way up, clear of most bottom tab bars. */
        const val INSPECTOR_HEIGHT_FRACTION = 5
    }
}

@Composable
internal fun InspectorMarkContent(onMark: (InspectorLabel) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.padding(Dimens.spaceS),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Dimens.spaceS),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spaceXs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!open) {
                AssistChip(onClick = { open = true }, label = { Text(stringResource(R.string.inspector_mark)) })
            } else {
                InspectorLabel.entries.forEach { label ->
                    AssistChip(
                        onClick = {
                            onMark(label)
                            open = false
                        },
                        label = { Text(stringResource(label.labelRes)) },
                    )
                }
            }
        }
    }
}

private val InspectorLabel.labelRes: Int
    get() = when (this) {
        InspectorLabel.REELS -> R.string.inspector_label_reels
        InspectorLabel.CHAT -> R.string.inspector_label_chat
        InspectorLabel.HOME -> R.string.inspector_label_home
        InspectorLabel.SEARCH -> R.string.inspector_label_search
        InspectorLabel.OTHER -> R.string.inspector_label_other
    }

@PreviewLightDark
@Composable
private fun InspectorMarkPreview() {
    UnscrollTheme(darkTheme = true) { InspectorMarkContent(onMark = {}) }
}
