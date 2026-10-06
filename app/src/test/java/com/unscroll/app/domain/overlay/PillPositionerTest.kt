package com.unscroll.app.domain.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class PillPositionerTest {

    // 1080x2400 phone with a 100 px status bar/cutout and a 60 px navigation bar.
    private val phone = ScreenBounds(width = 1080, height = 2400, insetTop = 100, insetBottom = 60)
    private val pillWidth = 300
    private val pillHeight = 80

    private fun clamp(x: Int, y: Int, screen: ScreenBounds = phone) =
        PillPositioner.clamp(PillPosition(x, y), pillWidth, pillHeight, screen)

    @Test
    fun positionInsideBounds_isUnchanged() {
        assertEquals(PillPosition(400, 900), clamp(400, 900))
    }

    @Test
    fun cannotGoAboveStatusBarOrCutout() {
        assertEquals(PillPosition(400, 100), clamp(400, 0))
        assertEquals(PillPosition(400, 100), clamp(400, -500))
    }

    @Test
    fun cannotGoOffTheLeftOrRightEdge() {
        assertEquals(PillPosition(0, 900), clamp(-50, 900))
        assertEquals(PillPosition(1080 - 300, 900), clamp(2000, 900))
    }

    @Test
    fun cannotGoUnderTheNavigationBar() {
        assertEquals(PillPosition(400, 2400 - 60 - 80), clamp(400, 2390))
    }

    @Test
    fun landscapeSideInsets_areRespected() {
        // Landscape with the cutout on the left and the nav bar on the right.
        val landscape = ScreenBounds(width = 2400, height = 1080, insetLeft = 100, insetTop = 50, insetRight = 120)
        assertEquals(PillPosition(100, 50), clamp(0, 0, landscape))
        assertEquals(PillPosition(2400 - 120 - 300, 1080 - 80), clamp(5000, 5000, landscape))
    }

    @Test
    fun pillWiderThanScreen_pinsToTopLeftOfAllowedArea() {
        val tiny = ScreenBounds(width = 200, height = 50, insetTop = 10)
        assertEquals(PillPosition(0, 10), clamp(500, 500, tiny))
    }

    @Test
    fun defaultPosition_isTopCenterBelowStatusBar() {
        assertEquals(
            PillPosition(x = (1080 - 300) / 2, y = 100 + 16),
            PillPositioner.defaultPosition(phone, pillWidth, pillHeight, marginTop = 16),
        )
    }
}
