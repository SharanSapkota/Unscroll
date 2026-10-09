package com.unscroll.app.domain.section

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The rule engine on fake node trees. The identifiers here are made up for the tests. */
class SectionDetectorTest {

    private val app = "com.example.social"
    private val twin = "com.example.social.lite"

    private val rules = AppSectionRules(
        appName = "Example",
        packages = setOf(app, twin),
        section = BlockedSection.REELS,
        ruleSets = listOf(
            SectionRuleSet(
                blockedWindowClasses = setOf("com.example.social.ClipsActivity"),
                blockedViewIds = setOf("id/clips_viewer"),
                blockedClassNames = setOf("com.example.social.ClipsPlayerView"),
                selectedTabViewIds = setOf("$app:id/tab_clips"),
                allowedWindowClasses = setOf("com.example.social.ChatActivity"),
                allowedViewIds = setOf("id/thread_composer"),
            ),
        ),
    )
    private val detector = RuleBasedSectionDetector(rules)

    private fun tabBar(clipsSelected: Boolean) = SectionNode(
        viewId = "$app:id/tab_bar",
        className = "android.widget.LinearLayout",
        children = listOf(
            SectionNode(viewId = "$app:id/tab_home", className = "android.widget.FrameLayout", isSelected = !clipsSelected),
            SectionNode(viewId = "$app:id/tab_clips", className = "android.widget.FrameLayout", isSelected = clipsSelected),
            SectionNode(viewId = "$app:id/tab_inbox", className = "android.widget.FrameLayout"),
        ),
    )

    private fun screen(vararg nodes: SectionNode, pkg: String = app, window: String? = "com.example.social.MainActivity") =
        SectionScreen(pkg, window, SectionNode(className = "android.widget.FrameLayout", children = nodes.toList()))

    private fun detect(screen: SectionScreen, version: Long? = null) = detector.detect(screen, version)

    @Test
    fun blockedSection_byViewId() {
        assertEquals(
            SectionVerdict.IN_BLOCKED_SECTION,
            detect(screen(SectionNode(viewId = "$app:id/clips_viewer", className = "android.view.View"))),
        )
    }

    @Test
    fun blockedSection_byClassName_andByWindowClass() {
        assertEquals(
            SectionVerdict.IN_BLOCKED_SECTION,
            detect(screen(SectionNode(className = "com.example.social.ClipsPlayerView"))),
        )
        assertEquals(
            SectionVerdict.IN_BLOCKED_SECTION,
            detect(screen(SectionNode(className = "android.view.View"), window = "com.example.social.ClipsActivity")),
        )
    }

    @Test
    fun selectedTab_blocksOnlyWhileSelected() {
        assertEquals(SectionVerdict.IN_BLOCKED_SECTION, detect(screen(tabBar(clipsSelected = true))))
        assertEquals(SectionVerdict.IN_ALLOWED_SECTION, detect(screen(tabBar(clipsSelected = false))))
    }

    @Test
    fun chat_isNeverBlocked_evenWithABlockedMarkerOnScreen() {
        // A chat thread with a shared reel in it: the allowed marker wins.
        val chat = screen(
            SectionNode(viewId = "$app:id/thread_composer"),
            SectionNode(viewId = "$app:id/clips_viewer"),
        )
        assertEquals(SectionVerdict.IN_ALLOWED_SECTION, detect(chat))
        val chatWindow = screen(tabBar(clipsSelected = true), window = "com.example.social.ChatActivity")
        assertEquals(SectionVerdict.IN_ALLOWED_SECTION, detect(chatWindow))
    }

    @Test
    fun otherScreens_areAllowed() {
        assertEquals(
            SectionVerdict.IN_ALLOWED_SECTION,
            detect(screen(SectionNode(viewId = "$app:id/search_box"), SectionNode(viewId = "$app:id/profile_header"))),
        )
    }

    @Test
    fun invisibleNodes_areIgnored() {
        // A preloaded, off-screen clips viewer must not block the home feed.
        val hidden = SectionNode(viewId = "$app:id/clips_viewer", isVisible = false)
        assertEquals(SectionVerdict.IN_ALLOWED_SECTION, detect(screen(hidden)))
    }

    @Test
    fun unreadableTree_failsOpen() {
        assertEquals(SectionVerdict.UNKNOWN, detect(SectionScreen(app, "com.example.social.ClipsActivity", root = null)))
    }

    @Test
    fun unknownPackage_failsOpen() {
        val other = screen(SectionNode(viewId = "com.other:id/clips_viewer"), pkg = "com.other")
        assertEquals(SectionVerdict.UNKNOWN, detect(other))
    }

    @Test
    fun emptyRules_failOpen() {
        val empty = RuleBasedSectionDetector(rules.copy(ruleSets = listOf(SectionRuleSet())))
        assertEquals(
            SectionVerdict.UNKNOWN,
            empty.detect(screen(SectionNode(viewId = "$app:id/clips_viewer"), tabBar(clipsSelected = true)), null),
        )
        assertFalse(rules.copy(ruleSets = listOf(SectionRuleSet())).isReady)
        assertTrue(rules.isReady)
    }

    @Test
    fun multiplePackages_shareShortIds_fullIdsStayPerPackage() {
        // "id/clips_viewer" matches in both packages.
        assertEquals(
            SectionVerdict.IN_BLOCKED_SECTION,
            detect(screen(SectionNode(viewId = "$twin:id/clips_viewer"), pkg = twin)),
        )
        // The selected tab is listed with the first package only, so it doesn't match the twin.
        val twinTabs = SectionNode(viewId = "$twin:id/tab_clips", isSelected = true)
        assertEquals(SectionVerdict.IN_ALLOWED_SECTION, detect(screen(twinTabs, pkg = twin)))
    }

    @Test
    fun versionAware_ruleSets() {
        val versioned = RuleBasedSectionDetector(
            rules.copy(
                ruleSets = listOf(
                    SectionRuleSet(maxVersionCode = 99, blockedViewIds = setOf("id/old_clips")),
                    SectionRuleSet(minVersionCode = 100, blockedViewIds = setOf("id/new_clips")),
                ),
            ),
        )
        val old = screen(SectionNode(viewId = "$app:id/old_clips"))
        val new = screen(SectionNode(viewId = "$app:id/new_clips"))
        assertEquals(SectionVerdict.IN_BLOCKED_SECTION, versioned.detect(old, 50))
        assertEquals(SectionVerdict.IN_ALLOWED_SECTION, versioned.detect(new, 50))
        assertEquals(SectionVerdict.IN_BLOCKED_SECTION, versioned.detect(new, 120))
        // Unknown version and no open-ended rule set: fail open.
        assertEquals(SectionVerdict.UNKNOWN, versioned.detect(new, null))
    }

    @Test
    fun idMatching() {
        val entries = setOf("id/tab", "com.a:id/full")
        assertTrue(RuleBasedSectionDetector.matchesId("com.a:id/tab", entries))
        assertTrue(RuleBasedSectionDetector.matchesId("com.b:id/tab", entries))
        assertTrue(RuleBasedSectionDetector.matchesId("com.a:id/full", entries))
        assertFalse(RuleBasedSectionDetector.matchesId("com.b:id/full", entries))
        assertFalse(RuleBasedSectionDetector.matchesId("com.a:id/tab2", entries))
        assertFalse(RuleBasedSectionDetector.matchesId(null, entries))
        assertFalse(RuleBasedSectionDetector.matchesId("", entries))
    }

    @Test
    fun detectors_lookUpEveryPackage() {
        val detectors = SectionDetectors(listOf(rules))
        assertEquals(setOf(app, twin), detectors.packages)
        assertEquals(BlockedSection.REELS, detectors.forPackage(twin)?.section)
        assertEquals(null, detectors.forPackage("com.other"))
        assertEquals(rules, detectors.rulesFor(app))
    }
}
