package com.unscroll.app.domain.apps

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppExclusionsTest {

    private val own = "com.sharansapkota.unscroll"
    private val device = setOf("com.google.android.apps.nexuslauncher", "com.oem.dialer", "com.oem.sms")

    @Test
    fun neverAddsOrBlocks_selfLauncherSettingsPhoneMessagingEmergencyPlayStoreOrSystemUi() {
        listOf(
            own,
            "com.google.android.apps.nexuslauncher",
            "com.oem.dialer",
            "com.oem.sms",
            "com.android.settings",
            "com.google.android.dialer",
            "com.android.phone",
            "com.google.android.apps.messaging",
            "com.android.emergency",
            "com.google.android.apps.safetyhub",
            "com.android.vending",
            "com.android.systemui",
            "com.android.systemui.plugin",
            "android",
        ).forEach { assertTrue(it, AppExclusions.isExcluded(it, own, device)) }
    }

    @Test
    fun anyOtherApp_canBeAdded() {
        listOf("com.instagram.android", "com.google.android.youtube", "com.android.chrome", "com.example.anyapp")
            .forEach { assertFalse(it, AppExclusions.isExcluded(it, own, device)) }
    }
}
