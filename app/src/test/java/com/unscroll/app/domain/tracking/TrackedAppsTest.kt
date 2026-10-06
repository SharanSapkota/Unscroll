package com.unscroll.app.domain.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackedAppsTest {

    @Test
    fun tracksInstagramTikTokAndFacebook() {
        assertEquals(
            setOf(
                "com.instagram.android",
                "com.zhiliaoapp.musically",
                "com.ss.android.ugc.trill",
                "com.facebook.katana",
            ),
            TrackedApps.packageNames,
        )
    }

    @Test
    fun isTracked() {
        assertTrue(TrackedApps.isTracked("com.instagram.android"))
        assertFalse(TrackedApps.isTracked("com.android.chrome"))
        assertFalse(TrackedApps.isTracked(null))
    }
}
