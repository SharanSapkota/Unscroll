package com.unscroll.app.domain.scroll

import com.unscroll.app.domain.tracking.DefaultTrackedApps
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * Guards the privacy limits of the accessibility service config: no window content, only scroll
 * and window-change events, only the tracked apps, and no claim to be an accessibility tool.
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
    fun cannotRetrieveWindowContent() {
        assertEquals("false", attr("canRetrieveWindowContent"))
    }

    @Test
    fun listensOnlyToScrollAndWindowChanges() {
        assertEquals(setOf("typeViewScrolled", "typeWindowStateChanged"), attr("accessibilityEventTypes").split('|').toSet())
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
