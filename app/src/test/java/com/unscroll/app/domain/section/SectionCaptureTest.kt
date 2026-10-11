package com.unscroll.app.domain.section

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The real [SectionRulesConfig] against the real captures ([SectionCaptures]): every screen the
 * user marked as Reels / Shorts is blocked, and no captured allowed screen (chat, home, search,
 * profile, stories, friends, upload, ...) is ever blocked, for any installed version.
 */
class SectionCaptureTest {

    private val detectors = SectionDetectors()

    /** The rule sets have open version ranges; check unknown, small and huge version codes. */
    private val versions = listOf(null, 1L, 2_147_483_647L, Long.MAX_VALUE)

    private fun detect(captured: SectionCaptures.Captured, version: Long?): SectionVerdict {
        val screen = captured.screen
        return detectors.forPackage(screen.packageName)!!.detect(screen, version)
    }

    private val supported = listOf(SectionCaptures.INSTAGRAM, SectionCaptures.YOUTUBE)
    private val unsupported = listOf(SectionCaptures.TIKTOK, SectionCaptures.FACEBOOK)

    @Test
    fun capturesCoverEveryConfiguredApp() {
        assertEquals(SectionRulesConfig.APPS, SectionCaptures.APPS.map { it.rules })
        SectionCaptures.APPS.forEach { app ->
            assertTrue(app.rules.appName, app.blocked.isNotEmpty() && app.allowed.size >= 4)
            (app.blocked + app.allowed).forEach { assertTrue(it.name, it.screen.packageName in app.rules.packages) }
        }
    }

    @Test
    fun allowedScreens_areNeverBlocked() {
        SectionCaptures.APPS.forEach { app ->
            app.allowed.forEach { captured ->
                versions.forEach { version ->
                    assertNotEquals(
                        "${captured.name} (version $version) would be blocked",
                        SectionVerdict.IN_BLOCKED_SECTION,
                        detect(captured, version),
                    )
                }
            }
        }
    }

    @Test
    fun allowedScreens_areNeverBlocked_evenWithEveryNodeSelected() {
        // Tabs that exist on every screen (Instagram's clips_tab) must not block on their own,
        // whichever tab Android reports as selected.
        SectionCaptures.APPS.forEach { app ->
            app.allowed.forEach { captured ->
                val root = captured.screen.root!!
                val allSelected = captured.screen.copy(root = root.copy(children = root.children.map { it.copy(isSelected = true) }))
                assertNotEquals(
                    captured.name,
                    SectionVerdict.IN_BLOCKED_SECTION,
                    detectors.forPackage(allSelected.packageName)!!.detect(allSelected, null),
                )
            }
        }
    }

    @Test
    fun blockedScreens_areBlocked_forSupportedApps() {
        supported.forEach { app ->
            app.blocked.forEach { captured ->
                versions.forEach { version ->
                    assertEquals("${captured.name} (version $version)", SectionVerdict.IN_BLOCKED_SECTION, detect(captured, version))
                }
            }
        }
    }

    @Test
    fun allowedScreens_areReadAsAllowed_forSupportedApps() {
        supported.forEach { app ->
            app.allowed.forEach { assertEquals(it.name, SectionVerdict.IN_ALLOWED_SECTION, detect(it, null)) }
        }
    }

    @Test
    fun unsupportedApps_answerUnknown_forEveryCapture() {
        // TikTok and Facebook: no safe difference in the captures, so nothing is ever blocked.
        unsupported.forEach { app ->
            assertFalse(app.rules.appName, app.rules.isReady)
            (app.blocked + app.allowed).forEach { captured ->
                versions.forEach { assertEquals(captured.name, SectionVerdict.UNKNOWN, detect(captured, it)) }
            }
        }
    }

    @Test
    fun ruleIdentifiers_neverAppearOnAnAllowedScreen() {
        // Stronger than the verdicts above: a blocked or required ID that shows up anywhere on an
        // allowed screen (Instagram's clips_tab aside, which only counts while selected) fails
        // here, even if the combination would still hold.
        SectionCaptures.APPS.forEach { app ->
            app.rules.ruleSets.forEach { set ->
                app.allowed.forEach { captured ->
                    val nodes = captured.screen.root!!.visible().toList()
                    (set.blockedViewIds + set.requiredViewIds).forEach { id ->
                        assertFalse(
                            "$id is on ${captured.name}",
                            nodes.any { RuleBasedSectionDetector.matchesId(it.viewId, setOf(id)) },
                        )
                    }
                    set.selectedTabViewIds.forEach { id ->
                        assertFalse(
                            "$id is selected on ${captured.name}",
                            nodes.any { it.isSelected && RuleBasedSectionDetector.matchesId(it.viewId, setOf(id)) },
                        )
                    }
                    assertTrue(captured.name, set.blockedClassNames.none { cls -> nodes.any { it.className == cls } })
                    assertFalse(captured.name, captured.screen.windowClassName in set.blockedWindowClasses)
                }
            }
        }
    }

    @Test
    fun ruleIdentifiers_allComeFromTheBlockedCapture() {
        // Nothing invented: every identifier in a rule set was logged on that app's screens.
        SectionCaptures.APPS.forEach { app ->
            val blockedIds = app.blocked.flatMap { c -> c.screen.root!!.visible().mapNotNull { it.viewId }.toList() }.toSet()
            val allowedIds = app.allowed.flatMap { c -> c.screen.root!!.visible().mapNotNull { it.viewId }.toList() }.toSet()
            app.rules.ruleSets.forEach { set ->
                (set.blockedViewIds + set.requiredViewIds + set.selectedTabViewIds).forEach {
                    assertTrue("$it not in ${app.rules.appName}'s blocked capture", it in blockedIds)
                }
                set.allowedViewIds.forEach {
                    assertTrue("$it not in ${app.rules.appName}'s allowed captures", it in allowedIds)
                    assertFalse("$it is on the blocked screen", it in blockedIds)
                }
            }
        }
    }

    @Test
    fun aSingleSignal_isNotEnough() {
        // The combination is what blocks: without the required IDs the Reels / Shorts screen is
        // not recognised (fail open).
        supported.forEach { app ->
            val set = app.rules.ruleSets.single()
            assertTrue(app.rules.appName, set.requiredViewIds.isNotEmpty())
            app.blocked.forEach { captured ->
                set.requiredViewIds.forEach { required ->
                    val root = captured.screen.root!!
                    val without = captured.screen.copy(
                        root = root.copy(children = root.children.filterNot { RuleBasedSectionDetector.matchesId(it.viewId, setOf(required)) }),
                    )
                    assertEquals(
                        "${captured.name} without $required",
                        SectionVerdict.IN_ALLOWED_SECTION,
                        detectors.forPackage(without.packageName)!!.detect(without, null),
                    )
                }
            }
        }
    }

    @Test
    fun instagramChatMarkers_winOverReels() {
        // A chat marker on screen keeps it open even with the Reels viewer showing.
        val reels = SectionCaptures.INSTAGRAM.blocked.single().screen
        val root = reels.root!!
        SectionRulesConfig.INSTAGRAM.ruleSets.single().allowedViewIds.forEach { marker ->
            val withChat = reels.copy(root = root.copy(children = root.children + SectionNode(viewId = marker)))
            assertEquals(marker, SectionVerdict.IN_ALLOWED_SECTION, detectors.forPackage(withChat.packageName)!!.detect(withChat, null))
        }
    }

    @Test
    fun unreadableTree_isUnknown() {
        SectionCaptures.APPS.forEach { app ->
            val screen = app.blocked.first().screen.copy(root = null)
            assertEquals(SectionVerdict.UNKNOWN, detectors.forPackage(screen.packageName)!!.detect(screen, null))
        }
    }

    @Test
    fun reelsToggle_availableExactlyForAppsWithRules() {
        listOf("com.instagram.android", "com.google.android.youtube").forEach { pkg ->
            versions.forEach { assertTrue("$pkg $it", detectors.isAvailable(pkg, it)) }
        }
        listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill", "com.facebook.katana").forEach { pkg ->
            versions.forEach { assertFalse("$pkg $it", detectors.isAvailable(pkg, it)) }
        }
    }

    @Test
    fun ruleSets_recordWhereTheyWereVerified() {
        mapOf(SectionRulesConfig.INSTAGRAM to "450.0.0.50.77", SectionRulesConfig.YOUTUBE to "21.40.161").forEach { (rules, version) ->
            val verified = rules.ruleSets.single().verifiedOn!!
            assertEquals(SectionCapture("Pixel 8", "17", version, null, "2026-10-11"), verified)
        }
    }
}
