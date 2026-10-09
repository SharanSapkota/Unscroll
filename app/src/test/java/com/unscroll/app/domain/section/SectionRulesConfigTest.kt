package com.unscroll.app.domain.section

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/** The seeded config and the service XML: placeholders only, so nothing is blocked yet. */
class SectionRulesConfigTest {

    @Test
    fun seeds_theFourApps() {
        val byName = SectionRulesConfig.APPS.associateBy { it.appName }
        assertEquals(setOf("com.instagram.android"), byName.getValue("Instagram").packages)
        assertEquals(setOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill"), byName.getValue("TikTok").packages)
        assertEquals(setOf("com.facebook.katana"), byName.getValue("Facebook").packages)
        assertEquals(setOf("com.google.android.youtube"), byName.getValue("YouTube").packages)
        assertEquals(BlockedSection.FOR_YOU, byName.getValue("TikTok").section)
        assertEquals(BlockedSection.SHORTS, byName.getValue("YouTube").section)
    }

    @Test
    fun placeholders_detectNothing_untilRealIdsAreCaptured() {
        // No identifiers were invented: every detector returns UNKNOWN, even for a busy screen.
        val busy = SectionNode(
            viewId = "x:id/anything",
            className = "android.widget.FrameLayout",
            isSelected = true,
            children = List(20) { SectionNode(viewId = "x:id/node$it", className = "android.view.View", isSelected = it % 2 == 0) },
        )
        val detectors = SectionDetectors()
        SectionRulesConfig.APPS.forEach { rules ->
            assertFalse(rules.appName, rules.isReady)
            rules.packages.forEach { pkg ->
                val verdict = detectors.forPackage(pkg)!!.detect(SectionScreen(pkg, "AnyActivity", busy), 1L)
                assertEquals(pkg, SectionVerdict.UNKNOWN, verdict)
            }
        }
    }

    @Test
    fun serviceXml_listsTheConfigPackages_andReadsViewIds() {
        val config = serviceConfig()
        assertEquals(
            SectionRulesConfig.APPS.flatMap { it.packages }.toSet(),
            config.attr("packageNames").split(',').map { it.trim() }.toSet(),
        )
        assertEquals("true", config.attr("canRetrieveWindowContent"))
        assertTrue("flagReportViewIds" in config.attr("accessibilityFlags").split('|'))
        assertEquals("false", config.attr("isAccessibilityTool"))
        assertEquals(
            setOf("typeWindowStateChanged", "typeWindowContentChanged", "typeViewSelected"),
            config.attr("accessibilityEventTypes").split('|').toSet(),
        )
    }

    @Test
    fun scrollCountingXml_isUnchanged_noWindowContent() {
        // Without section blocking consent the existing service behaves exactly as before.
        val path = "src/main/res/xml/accessibility_service_config.xml"
        assertEquals("false", parse(path).attr("canRetrieveWindowContent"))
    }

    private fun serviceConfig(): Element = parse("src/main/res/xml/section_blocking_service_config.xml")

    private fun parse(path: String): Element {
        val file = listOf(File(path), File("app/$path")).first { it.exists() }
        return DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(file)
            .documentElement
    }

    private fun Element.attr(name: String): String = getAttributeNS(ANDROID_NS, name)

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }
}
