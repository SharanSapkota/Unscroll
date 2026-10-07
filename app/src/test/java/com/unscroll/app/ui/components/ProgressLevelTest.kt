package com.unscroll.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class ProgressLevelTest {

    @Test
    fun greenUntil80Percent_amberUntilTheLimit_thenRed() {
        assertEquals(ProgressLevel.GOOD, ProgressLevel.of(0f))
        assertEquals(ProgressLevel.GOOD, ProgressLevel.of(0.79f))
        assertEquals(ProgressLevel.WARN, ProgressLevel.of(0.8f))
        assertEquals(ProgressLevel.WARN, ProgressLevel.of(0.99f))
        assertEquals(ProgressLevel.DANGER, ProgressLevel.of(1f))
        assertEquals(ProgressLevel.DANGER, ProgressLevel.of(2.5f))
    }
}
