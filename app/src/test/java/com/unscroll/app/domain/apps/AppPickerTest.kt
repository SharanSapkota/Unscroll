package com.unscroll.app.domain.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppPickerTest {

    private val launchable = listOf(
        LaunchableApp("com.zhiliaoapp.musically", "TikTok"),
        LaunchableApp("com.ss.android.ugc.trill", "TikTok"),
        LaunchableApp("com.google.android.youtube", "YouTube"),
        LaunchableApp("com.google.android.youtube", "YouTube"),
        LaunchableApp("com.android.settings", "Settings"),
        LaunchableApp("com.reddit.frontpage", "reddit"),
        LaunchableApp("com.example.notes", "Notes"),
    )
    private val excluded = { packageName: String -> packageName == "com.android.settings" }

    private val candidates = AppPicker.candidates(launchable, excluded)

    @Test
    fun candidates_hideExcludedApps_oneEntryPerPackage_sortedByName() {
        assertEquals(
            listOf("Notes", "reddit", "TikTok", "TikTok", "YouTube"),
            candidates.map { it.label },
        )
        // Variants of the same product stay separate.
        assertEquals(2, candidates.count { it.label == "TikTok" })
    }

    @Test
    fun search_isCaseInsensitive_onTheName_andLive() {
        assertEquals(listOf("YouTube"), AppPicker.search(candidates, "you").map { it.label })
        assertEquals(listOf("YouTube"), AppPicker.search(candidates, "TUBE").map { it.label })
        assertEquals(listOf("reddit"), AppPicker.search(candidates, " Red ").map { it.label })
        assertEquals(candidates, AppPicker.search(candidates, ""))
        assertTrue(AppPicker.search(candidates, "zzz").isEmpty())
    }

    @Test
    fun popularRow_onlyInstalledPopularApps_inListOrder_filteredBySearch() {
        val popular = AppPicker.popular(debug = false)
        assertEquals(
            listOf("com.google.android.youtube", "com.reddit.frontpage"),
            AppPicker.popularRow(candidates, popular, "").map { it.packageName },
        )
        assertEquals(listOf("com.reddit.frontpage"), AppPicker.popularRow(candidates, popular, "red").map { it.packageName })
    }

    @Test
    fun debugBuilds_addChromeToPopular() {
        assertTrue(AppPicker.CHROME !in AppPicker.popular(debug = false))
        assertTrue(AppPicker.CHROME in AppPicker.popular(debug = true))
    }

    @Test
    fun freeTierLimit_isOneNumber() {
        assertTrue(AddRules.canAdd(activeCount = 0, maxActiveApps = 1))
        assertTrue(!AddRules.canAdd(activeCount = 1, maxActiveApps = 1))
        assertTrue(AddRules.canAdd(activeCount = 50, maxActiveApps = Int.MAX_VALUE))
    }
}
