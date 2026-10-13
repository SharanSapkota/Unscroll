package com.unscroll.app.domain.insights

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LifeProjectionTest {

    private val twoHours = 2 * HOUR

    @Test
    fun needsSevenCompleteDays() {
        assertNull(LifeProjection.from(List(6) { twoHours }, age = null))
        assertNotNull(LifeProjection.from(List(7) { twoHours }, age = null))
    }

    @Test
    fun usesTheLast30DaysOnly() {
        // 10 old days at 10 hours, then 30 days at 2 hours: only the 30 count.
        val days = List(10) { 10 * HOUR } + List(30) { twoHours }
        assertEquals(twoHours, LifeProjection.from(days, age = null)!!.averageMillisPerDay)
    }

    @Test
    fun withoutAge_50MoreYears_16WakingHours() {
        val projection = LifeProjection.from(List(7) { twoHours }, age = null)!!
        assertEquals(50, projection.remainingYears)
        assertNull(projection.ageEnd)
        // 2 / 16 × 50 = 6.25 waking years.
        assertEquals(6.25, projection.years, 1e-9)
        assertEquals(3.125, projection.halfYears, 1e-9)
        // 30 minutes less a day: 0.5 / 16 × 50.
        assertEquals(1.5625, projection.savedYears!!, 1e-9)
        // 2 h × 365 / 24 = 30.4 full days.
        assertEquals(30, projection.daysPerYear)
    }

    @Test
    fun withAge_runsTo80() {
        val projection = LifeProjection.from(List(7) { twoHours }, age = 30)!!
        assertEquals(50, projection.remainingYears)
        assertEquals(80, projection.ageEnd)
        assertEquals(40, LifeProjection.from(List(7) { twoHours }, age = 40)!!.remainingYears)
        assertNull(LifeProjection.from(List(7) { twoHours }, age = 80))
    }

    @Test
    fun noSavedYears_underHalfAnHourADay() {
        assertNull(LifeProjection.from(List(7) { 20 * MINUTE }, age = null)!!.savedYears)
    }

    @Test
    fun lifeTemplates_fillFromTheProjection() {
        val engine = TestBank.engine(bankOf("L02\tLIFE\tH\t-\t{hours_per_day} a day adds up. By age {age_end}: {years} years."))
        val facts = testFacts(projection = LifeProjection.from(List(7) { twoHours + 10 * MINUTE }, age = 30))
        val insight = engine.weekly(facts, InsightHistory(), InsightTone.HONEST, now = 0, random = FirstRandom)!!
        // 2h 10m / 16 h × 50 years = 6.77 → 6.7 (rounded down).
        assertEquals("2h 10m a day adds up. By age 80: 6.7 years.", insight.text)
        assertEquals("6.7 years", insight.highlight)
    }

    @Test
    fun ageEndTemplates_skipped_withoutAge() {
        val engine = TestBank.engine(bankOf("L02\tLIFE\tH\t-\t{hours_per_day} a day adds up. By age {age_end}: {years} years."))
        val facts = testFacts(projection = LifeProjection.from(List(7) { twoHours }, age = null))
        assertNull(engine.weekly(facts, InsightHistory(), InsightTone.HONEST, now = 0, random = FirstRandom))
    }
}
