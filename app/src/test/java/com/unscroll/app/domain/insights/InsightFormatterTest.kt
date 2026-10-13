package com.unscroll.app.domain.insights

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InsightFormatterTest {

    private val format = TestBank.formatter()

    @Test
    fun numbers_getThousandsSeparators() {
        assertEquals("4,310", format.number(4_310))
        assertEquals("1,234,567", format.number(1_234_567))
        assertEquals("4.310", InsightFormatter(TestBank.bank.phrases, Locale.GERMANY).number(4_310))
    }

    @Test
    fun plurals_singularForExactlyOne() {
        assertEquals("1 full movie", format.movies(2 * HOUR))
        assertEquals("3 full movies", format.movies(6 * HOUR + 59 * MINUTE))
        assertEquals("1 Eiffel Tower", format.landmark(LandmarkCount(Equivalents.LANDMARKS.first { it.key == "eiffel" }, 1)))
        assertEquals("3 Eiffel Towers", format.landmark(LandmarkCount(Equivalents.LANDMARKS.first { it.key == "eiffel" }, 3)))
        assertEquals("2 Statues of Liberty", format.landmark(LandmarkCount(Equivalents.LANDMARKS.first { it.key == "liberty" }, 2)))
        assertEquals("1 home-cooked dinner", format.dinners(45 * MINUTE))
        assertEquals("3 home-cooked dinners", format.dinners(2 * HOUR + 15 * MINUTE))
        assertEquals("1 page of a book", format.pages(2 * MINUTE))
        assertEquals("65 pages of a book", format.pages(130 * MINUTE))
    }

    @Test
    fun countsRoundDown_andNeedAtLeastOne() {
        // 1h 59m is not a full movie.
        assertNull(format.movies(HOUR + 59 * MINUTE))
        assertNull(format.dinners(44 * MINUTE))
        assertNull(format.pages(MINUTE))
        assertNull(format.flights(11 * HOUR))
        assertEquals("a flight to Tokyo", format.flights(12 * HOUR))
        assertEquals("2 flights to Tokyo", format.flights(24 * HOUR))
    }

    @Test
    fun calls_spellSmallNumbers() {
        assertEquals("one 1-hour call", format.calls(HOUR))
        assertEquals("two 1-hour calls", format.calls(2 * HOUR + 30 * MINUTE))
        assertEquals("ten 1-hour calls", format.calls(10 * HOUR))
        assertEquals("11 1-hour calls", format.calls(11 * HOUR))
        assertNull(format.calls(59 * MINUTE))
    }

    @Test
    fun books_fractionThenDecimalsThenWhole() {
        assertEquals("0.4 of a book", format.books(2 * HOUR + 30 * MINUTE))
        assertEquals("1 book", format.books(6 * HOUR))
        assertEquals("1.5 books", format.books(9 * HOUR))
        assertEquals("11 books", format.books(66 * HOUR + 30 * MINUTE))
        assertNull(format.books(30 * MINUTE))
        assertEquals("20 books", format.wholeBooks(120 * HOUR))
        assertEquals("1 book", format.wholeBooks(6 * HOUR))
    }

    @Test
    fun walk_inKm_withTheRightArticle() {
        // 12 minutes per km.
        assertEquals("a 10 km walk", format.walk(2 * HOUR))
        assertEquals("an 8 km walk", format.walk(96 * MINUTE))
        assertEquals("an 11 km walk", format.walk(132 * MINUTE))
        assertEquals("an 18 km walk", format.walk(216 * MINUTE))
        assertEquals("a 1.5 km walk", format.walk(18 * MINUTE))
        assertNull(format.walk(11 * MINUTE))
    }

    @Test
    fun distance_metersThenKm() {
        assertNull(format.distance(0.5))
        assertEquals("60 m", format.distance(60.9))
        assertEquals("999 m", format.distance(999.99))
        assertEquals("1 km", format.distance(1_000.0))
        assertEquals("1.4 km", format.distance(1_499.0))
        assertEquals("99.9 km", format.distance(99_999.0))
        assertEquals("1,234 km", format.distance(1_234_567.0))
    }

    @Test
    fun durations() {
        assertEquals("2h 10m", format.duration(2 * HOUR + 10 * MINUTE + 59_000))
        assertEquals("2h", format.duration(2 * HOUR))
        assertEquals("35m", format.duration(35 * MINUTE))
        assertNull(format.duration(59_000))
        assertEquals("12 min", format.remaining(12 * MINUTE + 30_000))
        assertEquals("1h 5m", format.remaining(65 * MINUTE))
        assertEquals("14 hours", format.hoursOrDuration(14 * HOUR + 59 * MINUTE))
        assertEquals("1 hour", format.hoursOrDuration(HOUR))
        assertEquals("45m", format.hoursOrDuration(45 * MINUTE))
    }

    @Test
    fun decimals_roundDown() {
        assertEquals("7.3", format.oneDecimal(7.39))
        assertEquals("1.0", format.oneDecimal(1.0))
        assertEquals("1.4", format.decimal(1.4))
        assertEquals("2", format.decimal(2.0))
        assertTrue(InsightFormatter.floorTenths(0.3) == 0.3)
    }

    @Test
    fun savedEquivalent_biggestThingThatFits() {
        assertEquals("a full movie", format.savedEquivalent(2 * HOUR + 10 * MINUTE))
        assertEquals("2 full movies", format.savedEquivalent(4 * HOUR))
        assertEquals("a home-cooked dinner", format.savedEquivalent(50 * MINUTE))
        assertEquals("10 pages of a book", format.savedEquivalent(20 * MINUTE))
        assertNull(format.savedEquivalent(MINUTE))
    }

    @Test
    fun estimates_sayAbout() {
        assertEquals("about 4,310", format.estimate("4,310"))
    }
}
