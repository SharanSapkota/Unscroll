package com.unscroll.app.service

import android.view.accessibility.AccessibilityNodeInfo
import com.unscroll.app.domain.section.SectionNode

/**
 * The only code that touches AccessibilityNodeInfo for section blocking. It copies four
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
