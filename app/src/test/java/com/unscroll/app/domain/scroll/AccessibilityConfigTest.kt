package com.unscroll.app.domain.scroll

import com.unscroll.app.domain.tracking.DefaultTrackedApps
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * Guards the privacy limits of the accessibility service config: window content only for the
 * Reels-only timer's view IDs (the code is checked by SectionPrivacyGuardTest), only the events it
 * needs, only the tracked apps, and no claim to be an accessibility tool.
 */
class AccessibilityConfigTest {

    private val config: Element by lazy {
        val path = "src/main/res/xml/accessibility_service_config.xml"
        val file = listOf(File(path), File("app/$path")).first { it.exists() }
        DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(file)
            .documentElement
    }

    private fun attr(name: String): String = config.getAttributeNS(ANDROID_NS, name)

    @Test
    fun retrievesWindowContent_forTheReelsOnlyTimer() {
        // Deliberate: view IDs tell Reels from other screens. Only IDs, never text (guard test).
        assertEquals("true", attr("canRetrieveWindowContent"))
    }

    @Test
    fun flags_notImportantViews_andViewIds_only() {
        // Full-screen video pagers mark their scrolling view not important for accessibility;
        // without that flag Android drops their scroll events. flagReportViewIds gives the view IDs
        // the Reels rules match. Nothing that reveals more (no interactive windows).
        assertEquals(
            setOf("flagDefault", "flagIncludeNotImportantViews", "flagReportViewIds"),
            attr("accessibilityFlags").split('|').toSet(),
        )
    }

    @Test
    fun listensOnlyToScroll_window_contentAndSelectionChanges() {
        assertEquals(
            setOf("typeViewScrolled", "typeWindowStateChanged", "typeWindowContentChanged", "typeViewSelected"),
            attr("accessibilityEventTypes").split('|').toSet(),
        )
    }

    @Test
    fun packageNames_matchTheDefaultApps() {
        // The service replaces this list with the user's tracked apps when it connects.
        assertEquals(DefaultTrackedApps.packageNames.toSet(), attr("packageNames").split(',').map { it.trim() }.toSet())
    }

    @Test
    fun doesNotClaimToBeAnAccessibilityTool() {
        assertEquals("false", attr("isAccessibilityTool"))
        assertTrue(attr("description").startsWith("@string/"))
    }

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }
}
