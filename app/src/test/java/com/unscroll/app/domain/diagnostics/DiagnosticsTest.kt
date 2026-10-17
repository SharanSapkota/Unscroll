package com.unscroll.app.domain.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsTest {

    @Test
    fun log_keepsTheLast200Lines_oneLineEach() {
        val log = DiagnosticsLog()
        repeat(250) { log.add(time = it.toLong(), level = 'W', tag = "Tag", message = "line $it\nsecond") }
        val lines = log.lines()
        assertEquals(DiagnosticsLog.CAPACITY, lines.size)
        assertTrue(lines.first().endsWith("W/Tag: line 50 second"))
        assertTrue(lines.last().endsWith("W/Tag: line 249 second"))
        assertTrue(lines.none { '\n' in it })
    }

    @Test
    fun log_namesTheExceptionClass_only() {
        val log = DiagnosticsLog()
        log.add(0, 'W', "Overlay", "Window not shown", IllegalStateException("secret detail"))
        val line = log.lines().single()
        assertTrue(line.endsWith("W/Overlay: Window not shown (IllegalStateException)"))
        assertFalse("secret detail" in line)
    }

    @Test
    fun sanitize_capsLength() {
        assertEquals(DiagnosticsLog.MAX_LINE, DiagnosticsLog.sanitize("x".repeat(1_000)).length)
    }

    @Test
    fun report_hasEverySection() {
        val report = DiagnosticsReport.build(
            DiagnosticsInfo(
                appVersion = "0.2.0 (2)",
                androidVersion = "15 (API 35)",
                device = "Google Pixel 8",
                permissions = listOf("Usage access" to "granted", "Overlay" to "missing"),
                detector = listOf("com.instagram.android · ACTIVE · IN_REELS · v=1"),
                trackedVersions = listOf("com.instagram.android" to "450.0.0.50.77 (1)"),
                overlayState = "PillActive",
            ),
            logLines = listOf("10-17 10:00:00.000 W/Tag: hi"),
        )
        listOf("App: 0.2.0 (2)", "Android: 15 (API 35)", "Device: Google Pixel 8", "- Overlay: missing", "Overlay: PillActive",
            "- com.instagram.android · ACTIVE", "- com.instagram.android 450.0.0.50.77 (1)", "Log (last 1 lines)", "W/Tag: hi").forEach {
            assertTrue(it, it in report)
        }
    }
}
