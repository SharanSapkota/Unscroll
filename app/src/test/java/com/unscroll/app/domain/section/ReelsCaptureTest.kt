package com.unscroll.app.domain.section

import com.unscroll.app.domain.reels.ReelsState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Reels-only timer's rules (the same [SectionRulesConfig]) against the device captures, at the
 * captured versions: Instagram Reels and TikTok For You are IN_REELS, every other captured screen
 * (chat, inbox, home, search, profile, friends, upload) is not. Each check runs on the full
 * captured tree and on the sparse tree the service really builds (only the nodes it looks up with
 * findAccessibilityNodeInfosByViewId, [SectionRuleSet.allViewIds]).
 */
class ReelsCaptureTest {

    private val detectors = SectionDetectors()

    private val instagram = AppVersion(versionCode = null, versionName = "450.0.0.50.77")
    private val tiktok = AppVersion(versionCode = null, versionName = "47.0.3")

    private fun state(screen: SectionScreen, version: AppVersion): ReelsState =
        ReelsState.of(detectors.forPackage(screen.packageName)!!.explain(screen, version).verdict)

    /** What SectionNodeSnapshot.probe returns: a root holding only the looked-up IDs' nodes. */
    private fun sparse(screen: SectionScreen, version: AppVersion): SectionScreen {
        val ids = detectors.ruleSetFor(screen.packageName, version)!!.allViewIds
        val root = screen.root!!
        return screen.copy(root = root.copy(children = root.children.filter { RuleBasedSectionDetector.matchesId(it.viewId, ids) }))
    }

    private fun both(captured: SectionCaptures.Captured, version: AppVersion): List<ReelsState> =
        listOf(state(captured.screen, version), state(sparse(captured.screen, version), version))

    @Test
    fun instagramReels_isInReels_andEveryOtherScreenIsNot() {
        SectionCaptures.INSTAGRAM.blocked.forEach { captured ->
            both(captured, instagram).forEach { assertEquals(captured.name, ReelsState.IN_REELS, it) }
        }
        SectionCaptures.INSTAGRAM.allowed.forEach { captured ->
            // Chat (inbox and thread), home feed, search and profile.
            both(captured, instagram).forEach { assertEquals(captured.name, ReelsState.OTHER, it) }
        }
    }

    @Test
    fun tiktokForYou_isInReels_andEveryOtherScreenIsNot() {
        SectionCaptures.TIKTOK.blocked.forEach { captured ->
            both(captured, tiktok).forEach { assertEquals(captured.name, ReelsState.IN_REELS, it) }
        }
        SectionCaptures.TIKTOK.allowed.forEach { captured ->
            // Chat, Friends (also a video feed), profile and upload (camera).
            both(captured, tiktok).forEach { assertNotEquals(captured.name, ReelsState.IN_REELS, it) }
            assertEquals(captured.name, ReelsState.OTHER, state(captured.screen, tiktok))
        }
    }

    @Test
    fun matchedRule_namesTheCombination() {
        val reels = SectionCaptures.INSTAGRAM.blocked.single().screen
        val result = detectors.forPackage(reels.packageName)!!.explain(reels, instagram)
        assertEquals(SectionVerdict.IN_BLOCKED_SECTION, result.verdict)
        assertEquals("selected clips_tab + clips_viewer_container", result.rule)

        val forYou = SectionCaptures.TIKTOK.blocked.single().screen
        assertEquals(
            "selected omq + video_seek_bar + view_pager_layout_wrapper",
            detectors.forPackage(forYou.packageName)!!.explain(forYou, tiktok).rule,
        )
    }

    @Test
    fun tiktok_otherVersionOrPackage_isUnknown_soTheTimerFallsBack() {
        val forYou = SectionCaptures.TIKTOK.blocked.single().screen
        listOf(AppVersion(2024700030L, "47.1.0"), AppVersion(null, null), AppVersion(1L, "46.9.9")).forEach { version ->
            assertEquals(version.toString(), ReelsState.UNKNOWN, state(forYou, version))
            assertFalse(detectors.isAvailable(forYou.packageName, version))
        }
        assertTrue(detectors.isAvailable(forYou.packageName, tiktok))
        // Only com.zhiliaoapp.musically was captured; the other TikTok package has no rules.
        val trill = forYou.copy(packageName = "com.ss.android.ugc.trill")
        assertEquals(ReelsState.UNKNOWN, state(trill, tiktok))
        assertFalse(detectors.isAvailable("com.ss.android.ugc.trill", tiktok))
    }

    @Test
    fun tiktokRules_neverReachSectionBlocking() {
        // Blocking looks rule sets up by version code; TikTok's is pinned to a version name.
        val forYou = SectionCaptures.TIKTOK.blocked.single().screen
        listOf(null, 1L, Long.MAX_VALUE).forEach { code ->
            assertEquals(SectionVerdict.UNKNOWN, detectors.forPackage(forYou.packageName)!!.detect(forYou, code))
            assertFalse(detectors.isAvailable(forYou.packageName, code))
        }
    }

    @Test
    fun instagram_anyVersion_screenWithoutKnownMarkers_isUnknown() {
        // Instagram's readable IDs aren't version-pinned. A screen with none of the rules' IDs
        // and no tab bar (an uncaptured full-screen view such as stories, or IDs renamed by an
        // update) is UNKNOWN, never IN_REELS, so the timer can fall back instead of going quiet.
        val unknownScreen = SectionScreen(
            packageName = "com.instagram.android",
            windowClassName = "com.instagram.mainactivity.InstagramMainActivity",
            root = SectionNode(className = "android.widget.FrameLayout", children = List(5) { SectionNode(className = "android.view.View") }),
        )
        listOf(instagram, AppVersion(999L, "999.0.0.0.1"), AppVersion.UNKNOWN).forEach {
            assertEquals(ReelsState.UNKNOWN, state(unknownScreen, it))
        }
    }

    @Test
    fun unreadableTree_isUnknown() {
        val reels = SectionCaptures.INSTAGRAM.blocked.single().screen.copy(root = null)
        assertEquals(ReelsState.UNKNOWN, state(reels, instagram))
        val forYou = SectionCaptures.TIKTOK.blocked.single().screen.copy(root = null)
        assertEquals(ReelsState.UNKNOWN, state(forYou, tiktok))
    }

    @Test
    fun ruleSets_recordTheVerifiedVersions() {
        assertEquals("450.0.0.50.77", SectionRulesConfig.INSTAGRAM.ruleSets.single().verifiedOn!!.appVersionName)
        val tiktokSet = SectionRulesConfig.TIKTOK.ruleSets.single()
        assertEquals("47.0.3", tiktokSet.verifiedOn!!.appVersionName)
        assertEquals(setOf("47.0.3"), tiktokSet.versionNames)
        assertEquals(setOf("com.zhiliaoapp.musically"), tiktokSet.packages)
    }
}
