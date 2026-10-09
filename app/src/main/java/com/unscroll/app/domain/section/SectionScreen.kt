package com.unscroll.app.domain.section

/**
 * One view on the app's screen, reduced to technical identifiers. This is everything section
 * blocking ever knows about a screen: there is deliberately no field for text, content
 * descriptions, hints or any other content, so none can reach the rules or the logs.
 */
data class SectionNode(
    /** viewIdResourceName, e.g. "com.example.app:id/tab_bar", or null when the view has no ID. */
    val viewId: String? = null,
    /** The view's class name, e.g. "android.widget.FrameLayout". */
    val className: String? = null,
    val isSelected: Boolean = false,
    val isVisible: Boolean = true,
    val children: List<SectionNode> = emptyList(),
) {
    /** This node and everything under it, depth first. */
    fun all(): Sequence<SectionNode> = sequence {
        yield(this@SectionNode)
        children.forEach { yieldAll(it.all()) }
    }

    /** The visible nodes. */
    fun visible(): Sequence<SectionNode> = all().filter { it.isVisible }
}

/** What a detector gets: the app, its window (activity) class and the node tree, if readable. */
data class SectionScreen(
    val packageName: String,
    /** Class name from the last window-state change (the activity or dialog), or null. */
    val windowClassName: String?,
    /** Null when the node tree could not be read: detectors then return UNKNOWN (fail open). */
    val root: SectionNode?,
)
