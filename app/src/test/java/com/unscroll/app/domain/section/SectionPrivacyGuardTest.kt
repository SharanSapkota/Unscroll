package com.unscroll.app.domain.section

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The "never read text" guarantee for section blocking and the Reels-only timer. Fails if any code
 * on either detection path reads a node's (or an event's) text, content description, hint or other
 * user-visible content (getText, getContentDescription, getHintText, ...), and if any other main
 * source touches AccessibilityNodeInfo at all.
 */
class SectionPrivacyGuardTest {

    private val root: File = listOf(File("."), File("..")).first { File(it, "settings.gradle.kts").exists() }
    private val sources = File(root, "app/src/main/java/com/unscroll/app")

    /** Everything that sees accessibility nodes or what was read from them. */
    private val detectionPath: List<File> =
        listOf("domain/section", "domain/reels", "data/reels").flatMap { dir ->
            File(sources, dir).listFiles().orEmpty().filter { it.extension == "kt" }
        } +
            listOf(
                File(sources, "service/SectionNodeSnapshot.kt"),
                File(sources, "service/SectionBlockingService.kt"),
                // The Reels-only timer's screen checks run in the swipe counter's service.
                File(sources, "service/ScrollAccessibilityService.kt"),
            )

    @Test
    fun detectionPath_neverReadsContent() {
        assertTrue("detection path not found", detectionPath.size > 8 && detectionPath.all { it.exists() })
        assertTrue(detectionPath.any { it.name == "ScrollAccessibilityService.kt" })
        val offenders = detectionPath.flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                val code = line.substringBefore("//")
                FORBIDDEN.firstOrNull { it.containsMatchIn(code) && !isComment(code) }
                    ?.let { "${file.name}:${index + 1}: $line" }
            }
        }
        assertEquals("Content accessors on the section detection path", emptyList<String>(), offenders)
    }

    /**
     * What "Copy diagnostics" can share, and the log lines it includes: the same rule. Detector
     * state there is the verdict and rule IDs (ReelsDebugFormat), never anything read from a screen.
     */
    private val diagnosticsPath: List<File> = listOf(
        File(sources, "domain/diagnostics/Diagnostics.kt"),
        File(sources, "data/diagnostics/DiagnosticsRepository.kt"),
        File(sources, "util/AppLog.kt"),
        File(sources, "overlay/OverlayCoordinator.kt"),
    )

    @Test
    fun diagnosticsPath_neverReadsContent() {
        assertTrue("diagnostics path not found", diagnosticsPath.all { it.exists() })
        val offenders = diagnosticsPath.flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                val code = line.substringBefore("//")
                FORBIDDEN.firstOrNull { it.containsMatchIn(code) && !isComment(code) }
                    ?.let { "${file.name}:${index + 1}: $line" }
            }
        }
        assertEquals("Content accessors on the diagnostics path", emptyList<String>(), offenders)
    }

    @Test
    fun swipeCounter_neverAsksForOtherWindows() {
        // Screen checks only look at the active window of Instagram/TikTok, never the window list.
        val config = File(root, "app/src/main/res/xml/accessibility_service_config.xml").readText()
        assertTrue(config.contains("flagReportViewIds"))
        assertTrue(!Regex("""android:accessibilityFlags="[^"]*flagRetrieveInteractiveWindows""").containsMatchIn(config))
    }

    @Test
    fun onlyTheSnapshotTouchesAccessibilityNodeInfo() {
        val users = sources.walkTopDown()
            .filter { it.extension == "kt" && it.readText().contains("AccessibilityNodeInfo") }
            .map { it.name }
            .toSet()
        assertEquals(setOf("SectionNodeSnapshot.kt"), users)
    }

    @Test
    fun theGuardCatchesContentAccess() {
        val bad = listOf(
            "val t = node.text",
            "node.getText()",
            "val d = node.contentDescription?.toString()",
            "node.getContentDescription()",
            "node.hintText",
            "node.getHintText()",
            "val hint = node.hintText?.toString()",
            "event.getText()",
            "event.contentDescription",
            "node.stateDescription",
            "node.tooltipText",
            "node.paneTitle",
            "event.text.joinToString()",
            "node.extras",
            "node.error",
        )
        bad.forEach { line -> assertTrue(line, FORBIDDEN.any { it.containsMatchIn(line) }) }
        val fine = listOf(
            "node.viewIdResourceName",
            "node.className?.toString()",
            "node.isSelected",
            "node.isVisibleToUser",
            "event.packageName?.toString()",
            "event.className?.toString()",
            "root.findAccessibilityNodeInfosByViewId(fullId)",
            "val textSize = 3",
        )
        fine.forEach { line -> assertTrue(line, FORBIDDEN.none { it.containsMatchIn(line) }) }
    }

    private fun isComment(code: String): Boolean = code.trimStart().let { it.startsWith("*") || it.startsWith("/*") }

    private companion object {
        val FORBIDDEN = listOf(
            Regex("""\.text\b"""),
            Regex("""\bgetText\s*\("""),
            Regex("""contentDescription""", RegexOption.IGNORE_CASE),
            Regex("""hintText""", RegexOption.IGNORE_CASE),
            Regex("""stateDescription""", RegexOption.IGNORE_CASE),
            Regex("""tooltipText""", RegexOption.IGNORE_CASE),
            Regex("""paneTitle""", RegexOption.IGNORE_CASE),
            Regex("""\.extras\b"""),
            Regex("""\.error\b"""),
            Regex("""\bgetError\s*\("""),
        )
    }
}
