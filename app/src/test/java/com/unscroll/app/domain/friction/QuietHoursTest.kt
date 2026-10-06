package com.unscroll.app.domain.friction

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuietHoursTest {

    private val zone = ZoneId.of("Europe/Berlin")

    private fun at(time: String) = LocalDateTime.parse("2026-10-06T$time").atZone(zone).toInstant().toEpochMilli()

    @Test
    fun disabled_neverQuiet() {
        assertFalse(QuietHours(enabled = false).isQuiet(at("23:00"), zone))
    }

    @Test
    fun rangeCrossingMidnight() {
        val quiet = QuietHours(enabled = true, startMinute = 22 * 60, endMinute = 7 * 60)
        assertFalse(quiet.isQuiet(at("21:59"), zone))
        assertTrue(quiet.isQuiet(at("22:00"), zone))
        assertTrue(quiet.isQuiet(at("23:59"), zone))
        assertTrue(quiet.isQuiet(at("00:00"), zone))
        assertTrue(quiet.isQuiet(at("06:59"), zone))
        assertFalse(quiet.isQuiet(at("07:00"), zone))
        assertFalse(quiet.isQuiet(at("12:00"), zone))
    }

    @Test
    fun sameDayRange() {
        val quiet = QuietHours(enabled = true, startMinute = 9 * 60, endMinute = 17 * 60)
        assertFalse(quiet.isQuiet(at("08:59"), zone))
        assertTrue(quiet.isQuiet(at("09:00"), zone))
        assertFalse(quiet.isQuiet(at("17:00"), zone))
    }

    @Test
    fun startEqualsEnd_allDay() {
        assertTrue(QuietHours(enabled = true, startMinute = 0, endMinute = 0).isQuiet(at("15:00"), zone))
    }
}
