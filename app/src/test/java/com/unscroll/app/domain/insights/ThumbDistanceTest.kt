package com.unscroll.app.domain.insights

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThumbDistanceTest {

    @Test
    fun swipeLength_is55PercentOfTheScreenHeight() {
        // 2400 px at 420 dpi = 5.714 in = 14.5 cm; 55 % = 7.98 cm.
        val length = ThumbDistance.swipeLengthMeters(2_400, 420f)
        assertEquals(0.1451 * 0.55, length, 0.0005)
    }

    @Test
    fun swipeLength_fallsBackTo6cm_whenTheScreenCantBeRead() {
        assertEquals(0.06, ThumbDistance.swipeLengthMeters(0, 420f), 0.0)
        assertEquals(0.06, ThumbDistance.swipeLengthMeters(2_400, 0f), 0.0)
        assertEquals(0.06, ThumbDistance.swipeLengthMeters(2_400, Float.NaN), 0.0)
        // A bogus dpi that would make the phone 1.5 m tall.
        assertEquals(0.06, ThumbDistance.swipeLengthMeters(2_400, 40f), 0.0)
    }

    @Test
    fun estimate_is4SwipesPerMinute_roundedDown() {
        assertEquals(40, ThumbDistance.estimatedSwipes(10 * MINUTE))
        assertEquals(41, ThumbDistance.estimatedSwipes(10 * MINUTE + 29_000))
        assertEquals(0, ThumbDistance.estimatedSwipes(-5))
    }

    @Test
    fun meters_areSwipesTimesLength() {
        assertEquals(344.8, ThumbDistance.meters(4_310, 0.08), 0.001)
    }

    @Test
    fun withRealSwipes_numbersAreExact() {
        val engine = TestBank.engine(bankOf("T07\tTHUMB\tH\t-\t{swipes} swipes {period}. Laid end to end, that's {distance}."))
        val facts = testFacts(combined = UsageFacts(millis = HOUR, swipes = SwipeCount(4_310, estimated = false)))
        val insight = engine.next(facts, InsightHistory(), InsightTone.HONEST, FirstRandom)!!
        assertEquals("4,310 swipes today. Laid end to end, that's 344 m.", insight.text)
        assertEquals("344 m", insight.highlight)
    }

    @Test
    fun withoutSwipeData_estimatesSayAbout() {
        val engine = TestBank.engine(bankOf("T03\tTHUMB\tB\t-\t{swipes} swipes. {distance}. All to end up exactly where you started."))
        // One hour, no swipe data: 240 swipes estimated.
        val facts = testFacts(combined = UsageFacts(millis = HOUR, swipes = SwipeCount(240, estimated = true)))
        val insight = engine.next(facts, InsightHistory(), InsightTone.BRUTAL, FirstRandom)!!
        assertEquals("About 240 swipes. About 19 m. All to end up exactly where you started.", insight.text)
    }

    @Test
    fun about_isNotDoubled_whenTheTemplateAlreadySaysIt() {
        val engine = TestBank.engine(bankOf("T05\tTHUMB\tG\t-\tThat's {distance} of scrolling {period}. About {landmark_phrase}."))
        val facts = testFacts(combined = UsageFacts(millis = 10 * HOUR, swipes = SwipeCount(2_400, estimated = true)), swipeLengthMeters = 0.08)
        val text = engine.next(facts, InsightHistory(), InsightTone.HONEST, FirstRandom)!!.text
        // 2,400 × 8 cm = 192 m; the first landmark in range is the Olympic pool (3).
        assertEquals("That's about 192 m of scrolling today. About 3 Olympic pools.", text)
    }

    @Test
    fun landmark_countIsBetween1And20() {
        for (meters in listOf(50.0, 120.0, 900.0, 3_000.0, 40_000.0, 500_000.0)) {
            val candidates = LandmarkPicker.candidates(meters)
            assertTrue("$meters m", candidates.isNotEmpty())
            assertTrue(candidates.all { it.count in 1..20 })
        }
        val pick = LandmarkPicker.pick(1_400.0, Random(7))!!
        assertTrue(pick.count in 1..20)
        assertEquals(pick.count, (1_400.0 / pick.landmark.meters).toLong())
    }

    @Test
    fun landmark_noneUnderAnOlympicPool_largestBeyondTheRange() {
        assertNull(LandmarkPicker.pick(49.0, FirstRandom))
        // 10,000 km: more than 20 marathons, less than one Great Wall.
        val far = LandmarkPicker.pick(10_000_000.0, FirstRandom)!!
        assertEquals("marathon", far.landmark.key)
        assertEquals(236, far.count)
    }

    @Test
    fun landmark_candidatesAtOneAndAHalfKm() {
        val keys = LandmarkPicker.candidates(1_500.0).map { it.landmark.key to it.count }
        assertEquals(
            listOf("liberty" to 16L, "big_ben" to 15L, "pitch" to 14L, "eiffel" to 4L, "empire_state" to 3L, "burj_khalifa" to 1L),
            keys,
        )
    }
}
