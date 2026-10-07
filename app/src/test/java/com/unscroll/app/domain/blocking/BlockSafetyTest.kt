package com.unscroll.app.domain.blocking

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockSafetyTest {

    private val own = "com.unscroll.app"
    private val home = setOf("com.google.android.apps.nexuslauncher")

    @Test
    fun safety_onlyTrackedAppsCanBeBlocked() {
        assertTrue(BlockSafety.canBlock("com.instagram.android", own, home))
        assertTrue(BlockSafety.canBlock("com.zhiliaoapp.musically", own, home))
        assertFalse(BlockSafety.canBlock("com.android.chrome", own, home))
    }

    @Test
    fun safety_neverBlocksSelfLauncherSettingsPhoneOrEmergency() {
        listOf(
            own,
            "com.google.android.apps.nexuslauncher",
            "com.android.settings",
            "com.google.android.dialer",
            "com.android.phone",
            "com.android.emergency",
        ).forEach { assertFalse(it, BlockSafety.canBlock(it, own, home)) }
    }
}
