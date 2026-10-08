package com.unscroll.app.domain.sample

import com.unscroll.app.domain.tracking.DefaultTrackedApps
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.random.Random
import org.junit.Assert.assertTrue
import org.junit.Test

class SampleSessionGeneratorTest {

    private val zone = ZoneId.of("Europe/Berlin")
    private val now = LocalDateTime.parse("2026-10-06T20:00:00").atZone(zone).toInstant().toEpochMilli()
    private val sessions = SampleSessionGenerator.generate(now, zone, days = 30, random = Random(7))

    @Test
    fun generatesPlentyOfSessionsForTrackedAppsOnly() {
        assertTrue(sessions.size > 100)
        assertTrue(sessions.all { it.packageName in DefaultTrackedApps.packageNames })
    }

    @Test
    fun sessionsAreWithinThirtyDays_endBeforeNow_andNeverOverlap() {
        val earliest = LocalDateTime.parse("2026-09-07T00:00:00").atZone(zone).toInstant().toEpochMilli()
        assertTrue(sessions.all { it.startTime >= earliest && it.endTime <= now && it.endTime > it.startTime })
        sessions.zipWithNext().forEach { (a, b) -> assertTrue(b.startTime >= a.endTime) }
    }
}
