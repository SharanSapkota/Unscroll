package com.unscroll.app.domain.diagnostics

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The last [capacity] log lines of this process, kept in memory only, for "Copy diagnostics"
 * (Settings). Lines are Unscroll's own technical messages (tags, states, exception class names);
 * [sanitize] keeps each to one short line. Nothing is written to disk or sent anywhere.
 * Thread-safe.
 */
class DiagnosticsLog(private val capacity: Int = CAPACITY) {
    private val lines = ArrayDeque<String>(capacity)

    fun add(time: Long, level: Char, tag: String, message: String, error: Throwable? = null) {
        val errorPart = error?.let { " (${it.javaClass.simpleName})" }.orEmpty()
        val line = "${TIME.format(Instant.ofEpochMilli(time))} $level/$tag: ${sanitize(message)}$errorPart"
        synchronized(lines) {
            if (lines.size == capacity) lines.removeFirst()
            lines.addLast(line)
        }
    }

    fun lines(): List<String> = synchronized(lines) { lines.toList() }

    fun clear() = synchronized(lines) { lines.clear() }

    companion object {
        const val CAPACITY = 200
        const val MAX_LINE = 240
        private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault())

        /** One line, at most [MAX_LINE] characters, no control characters. */
        fun sanitize(message: String): String =
            message.replace(Regex("[\\r\\n\\t]+"), " ").filter { !it.isISOControl() }.take(MAX_LINE)
    }
}

/** What "Copy diagnostics" collects. No usage data, no screen content, no account (there is none). */
data class DiagnosticsInfo(
    val appVersion: String,
    val androidVersion: String,
    val device: String,
    /** e.g. "Usage access" to "granted". */
    val permissions: List<Pair<String, String>>,
    /** One line per app: status, state, rule, version (ReelsDebugFormat). */
    val detector: List<String>,
    /** Package to installed version name and code, for the tracked Reels-capable apps. */
    val trackedVersions: List<Pair<String, String>>,
    val overlayState: String,
)

/** Builds the plain-text report shown in the preview and shared on request. Pure. */
object DiagnosticsReport {
    fun build(info: DiagnosticsInfo, logLines: List<String>): String = buildString {
        appendLine("Unscroll diagnostics")
        appendLine("App: ${info.appVersion}")
        appendLine("Android: ${info.androidVersion}")
        appendLine("Device: ${info.device}")
        appendLine()
        appendLine("Permissions")
        info.permissions.forEach { (name, state) -> appendLine("- $name: $state") }
        appendLine()
        appendLine("Overlay: ${info.overlayState}")
        appendLine()
        appendLine("Reels detection")
        if (info.detector.isEmpty()) appendLine("- no readings") else info.detector.forEach { appendLine("- $it") }
        appendLine()
        appendLine("Installed versions")
        if (info.trackedVersions.isEmpty()) appendLine("- none") else info.trackedVersions.forEach { (pkg, version) -> appendLine("- $pkg $version") }
        appendLine()
        appendLine("Log (last ${logLines.size} lines)")
        logLines.forEach { appendLine(it) }
    }.trimEnd()
}
