package com.unscroll.app.domain.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class ColorThresholdsTest {

    @Test
    fun defaults_areTenAndTwentyMinutes() {
        assertEquals(ColorThresholds(10, 20), ColorThresholds())
        assertEquals(ColorThresholds(10, 20), ColorThresholds().normalized())
    }

    @Test
    fun normalized_keepsDangerAfterWarning() {
        assertEquals(ColorThresholds(15, 16), ColorThresholds(15, 5).normalized())
        assertEquals(ColorThresholds(15, 16), ColorThresholds(15, 15).normalized())
    }

    @Test
    fun normalized_clampsToRange() {
        assertEquals(ColorThresholds(1, 2), ColorThresholds(0, 0).normalized())
        assertEquals(ColorThresholds(119, 120), ColorThresholds(500, 900).normalized())
    }

    @Test
    fun opacity_isClamped() {
        assertEquals(0.4f, OverlaySettings.clampOpacity(0f), 0f)
        assertEquals(1f, OverlaySettings.clampOpacity(3f), 0f)
        assertEquals(0.75f, OverlaySettings.clampOpacity(0.75f), 0f)
    }
}
