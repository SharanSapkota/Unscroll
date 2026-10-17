package com.unscroll.app.service

import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import com.unscroll.app.domain.section.SectionNode

/**
 * The only code that touches AccessibilityNodeInfo (section blocking and the Reels-only timer).
 * It copies four
 * technical properties per node (viewIdResourceName, className, isSelected, isVisibleToUser) and
 * nothing else: no text, content description, hint, state description, tooltip, pane title or
 * extras. SectionPrivacyGuardTest fails the build if this file or the rest of the detection path
 * ever reads one of those.
 */
internal object SectionNodeSnapshot {
    /** Bounds the work per evaluation; a partial tree can only miss markers (fail open). */
    const val MAX_NODES = 1_500
    const val MAX_DEPTH = 60

    /** A copy of [root]'s tree, or null if it can't be read (detectors then return UNKNOWN). */
    fun capture(root: AccessibilityNodeInfo?): SectionNode? {
        if (root == null) return null
        return try {
            copy(root, depth = 0, budget = intArrayOf(MAX_NODES))
        } catch (e: RuntimeException) {
            // Nodes can go stale while being read; treat the screen as unreadable.
            null
        }
    }

    /** Bounds the Reels-only timer's lookups: nodes kept per reading. */
    const val MAX_PROBED_NODES = 64

    /**
     * A sparse copy for the Reels-only timer: instead of walking the tree, looks up only the rule
     * set's [viewIds] with findAccessibilityNodeInfosByViewId (the search runs in the app's own
     * process) and returns a root whose children are the matches. Short IDs ("id/x") are expanded
     * with [packageName]. Every node it gets is recycled (needed below Android 13). Null when the
     * tree can't be read (UNKNOWN). [root] stays owned by the caller.
     */
    fun probe(root: AccessibilityNodeInfo?, packageName: String, viewIds: Set<String>): SectionNode? {
        if (root == null) return null
        return try {
            val found = ArrayList<SectionNode>()
            viewIds.forEach { id ->
                val fullId = if (':' in id) id else "$packageName:$id"
                root.findAccessibilityNodeInfosByViewId(fullId).orEmpty().forEach { node ->
                    if (found.size < MAX_PROBED_NODES) {
                        found += SectionNode(
                            viewId = node.viewIdResourceName,
                            className = node.className?.toString(),
                            isSelected = node.isSelected,
                            isVisible = node.isVisibleToUser,
                        )
                    }
                    release(node)
                }
            }
            SectionNode(viewId = root.viewIdResourceName, className = root.className?.toString(), children = found)
        } catch (e: RuntimeException) {
            // Nodes can go stale while being read; treat the screen as unreadable.
            null
        }
    }

    /** Returns [node] to the pool below Android 13 (a no-op since, where recycle() is deprecated). */
    fun release(node: AccessibilityNodeInfo?) {
        if (node == null || Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
        try {
            @Suppress("DEPRECATION")
            node.recycle()
        } catch (e: IllegalStateException) {
            // Already recycled.
        }
    }

    private fun copy(node: AccessibilityNodeInfo, depth: Int, budget: IntArray): SectionNode {
        budget[0]--
        val children = if (depth >= MAX_DEPTH) {
            emptyList()
        } else {
            (0 until node.childCount).mapNotNull { index ->
                if (budget[0] <= 0) null else node.getChild(index)?.let { copy(it, depth + 1, budget) }
            }
        }
        return SectionNode(
            viewId = node.viewIdResourceName,
            className = node.className?.toString(),
            isSelected = node.isSelected,
            isVisible = node.isVisibleToUser,
            children = children,
        )
    }
}
