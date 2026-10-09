package com.unscroll.app.domain.section

/** What the user says they are looking at, from the debug "Mark this screen as" button. */
enum class InspectorLabel { REELS, CHAT, HOME, SEARCH, OTHER }

/**
 * Debug-only Section Inspector output: the package, the window class and a deduplicated list of
 * (viewIdResourceName, className, isSelected) for the visible nodes. Built from [SectionScreen],
 * which has no text or content descriptions, so none can ever be logged.
 */
object SectionInspectorFormat {

    /** Logcat truncates long lines; one entry per line keeps every identifier readable. */
    fun lines(screen: SectionScreen, label: InspectorLabel? = null): List<String> {
        val header = buildString {
            if (label != null) append("MARK ").append(label.name).append(' ')
            append("package=").append(screen.packageName)
            append(" window=").append(screen.windowClassName ?: "-")
        }
        val root = screen.root ?: return listOf(header, "  (node tree not readable)")
        val entries = root.visible()
            .map { Entry(it.viewId, it.className, it.isSelected) }
            .distinct()
            .map { "  id=${it.viewId ?: "-"} class=${it.className ?: "-"} selected=${it.selected}" }
            .toList()
        return listOf(header) + entries
    }

    private data class Entry(val viewId: String?, val className: String?, val selected: Boolean)
}
