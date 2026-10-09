package com.unscroll.app.domain.section

import org.junit.Assert.assertEquals
import org.junit.Test

class SectionInspectorFormatTest {

    private val screen = SectionScreen(
        packageName = "com.example.social",
        windowClassName = "com.example.social.MainActivity",
        root = SectionNode(
            viewId = "com.example.social:id/root",
            className = "android.widget.FrameLayout",
            children = listOf(
                SectionNode(viewId = "com.example.social:id/tab", className = "android.view.View", isSelected = true),
                SectionNode(viewId = "com.example.social:id/tab", className = "android.view.View", isSelected = true),
                SectionNode(viewId = "com.example.social:id/tab", className = "android.view.View", isSelected = false),
                SectionNode(viewId = "com.example.social:id/hidden", className = "android.view.View", isVisible = false),
                SectionNode(className = "android.widget.TextView"),
            ),
        ),
    )

    @Test
    fun logsPackageWindowAndDeduplicatedVisibleIdentifiers() {
        assertEquals(
            listOf(
                "package=com.example.social window=com.example.social.MainActivity",
                "  id=com.example.social:id/root class=android.widget.FrameLayout selected=false",
                "  id=com.example.social:id/tab class=android.view.View selected=true",
                "  id=com.example.social:id/tab class=android.view.View selected=false",
                "  id=- class=android.widget.TextView selected=false",
            ),
            SectionInspectorFormat.lines(screen),
        )
    }

    @Test
    fun markedScreens_carryTheLabel() {
        assertEquals(
            "MARK REELS package=com.example.social window=com.example.social.MainActivity",
            SectionInspectorFormat.lines(screen, InspectorLabel.REELS).first(),
        )
    }

    @Test
    fun unreadableTree() {
        assertEquals(
            listOf("package=com.example.social window=-", "  (node tree not readable)"),
            SectionInspectorFormat.lines(SectionScreen("com.example.social", null, null)),
        )
    }
}
