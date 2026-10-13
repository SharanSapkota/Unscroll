package com.unscroll.app.domain.insights

import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.floor
import kotlin.random.Random

/**
 * Turns numbers into the words of the bank's phrases: plurals ("1 full movie", "3 Eiffel Towers"),
 * thousands separators, units (m or km) and durations. Pure; the words come from phrases.tsv.
 *
 * Every number is rounded down, never up, so a message never makes usage look bigger than it is.
 * Counts of things ("full movies", "dinners") are whole things that fit in the time.
 */
class InsightFormatter(
    private val phrases: Map<String, Phrase>,
    private val locale: Locale = Locale.getDefault(),
) {
    private val integers: NumberFormat = NumberFormat.getIntegerInstance(locale).apply { isGroupingUsed = true }

    /** "4,310" (the locale's grouping). */
    fun number(value: Long): String = integers.format(value)

    /** One decimal, rounded down, without a trailing ".0": "1.4", "2". */
    fun decimal(value: Double): String = decimalFormat(minFraction = 0).format(floorTenths(value))

    /** Always one decimal, rounded down: "7.3", "1.0" (for years, so "1 years" never happens). */
    fun oneDecimal(value: Double): String = decimalFormat(minFraction = 1).format(floorTenths(value))

    /** The phrase [key] for [count], with [numberText] (default: [count] formatted) in place of {n}. */
    fun plural(key: String, count: Long, numberText: String = number(count)): String? {
        val phrase = phrases[key] ?: return null
        return (if (count == 1L) phrase.one else phrase.other).replace(N, numberText)
    }

    /** A phrase that doesn't depend on a count. */
    fun word(key: String): String? = phrases[key]?.other

    /** "about 4,310": for numbers estimated from time instead of real swipe counts. */
    fun estimate(text: String): String = phrases[KEY_ESTIMATE]?.one?.replace(N, text) ?: text

    /** Thumb distance: "60 m" under a kilometer, then "1.4 km", and whole km from 100 km on. */
    fun distance(meters: Double): String? {
        if (meters < 1.0) return null
        return when {
            meters < METERS_PER_KM -> plural("distance.m", 2, number(floor(meters).toLong()))
            meters < WHOLE_KM_FROM * METERS_PER_KM -> plural("distance.km", 2, decimal(meters / METERS_PER_KM))
            else -> plural("distance.km", 2, number(floor(meters / METERS_PER_KM).toLong()))
        }
    }

    /** "2h 10m", "2h", "35m". Null under a minute: no message talks about seconds. */
    fun duration(millis: Long): String? {
        val totalMinutes = millis / MINUTE
        if (totalMinutes < 1) return null
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours == 0L -> word("time.minutes")?.replace(N, number(minutes))
            minutes == 0L -> word("time.hours")?.replace(N, number(hours))
            else -> word("time.hours_minutes")?.replace("{h}", number(hours))?.replace("{m}", number(minutes))
        }
    }

    /** Time left before a limit: "12 min" under an hour, else like [duration]. */
    fun remaining(millis: Long): String? {
        val minutes = millis / MINUTE
        if (minutes < 1) return null
        return if (minutes < 60) word("time.remaining_minutes")?.replace(N, number(minutes)) else duration(millis)
    }

    /** "14 hours" from an hour on, else like [duration]. */
    fun hoursOrDuration(millis: Long): String? {
        val hours = millis / HOUR
        return if (hours >= 1) plural("hours", hours) else duration(millis)
    }

    /** "1 full movie", "3 full movies"; null when not even one fits. */
    fun movies(millis: Long): String? = count(millis / (Equivalents.HOURS_PER_MOVIE * HOUR))?.let { plural("movies", it) }

    /** "65 pages of a book". */
    fun pages(millis: Long): String? =
        count(millis / (Equivalents.MINUTES_PER_PAGE * MINUTE))?.let { plural("pages", it) }

    /** "0.4 of a book" under one book, then "1.5 books", "11 books". */
    fun books(millis: Long): String? {
        val books = floorTenths(millis / (Equivalents.HOURS_PER_BOOK * HOUR))
        return when {
            books < MIN_BOOKS -> null
            books < 1.0 -> word("books.fraction")?.replace(N, decimal(books))
            books < WHOLE_BOOKS_FROM -> plural("books", if (books == 1.0) 1 else 2, decimal(books))
            else -> plural("books", floor(books).toLong())
        }
    }

    /** "a 10 km walk" (one km at least). */
    fun walk(millis: Long): String? {
        val km = floorTenths(millis / (Equivalents.MINUTES_PER_KM_WALKING * MINUTE))
        if (km < 1.0) return null
        val text = if (km < WHOLE_KM_FROM) decimal(km) else number(floor(km).toLong())
        val key = if (startsWithVowelSound(text)) "walk.vowel" else "walk"
        return plural(key, 2, text)
    }

    /** "a flight to Tokyo", "2 flights to Tokyo". */
    fun flights(millis: Long): String? =
        count(millis / (Equivalents.HOURS_PER_FLIGHT_TO_TOKYO * HOUR))?.let { plural("flights", it) }

    /** "3 home-cooked dinners". */
    fun dinners(millis: Long): String? =
        count(millis / (Equivalents.MINUTES_PER_HOME_COOKED_DINNER * MINUTE))?.let { plural("dinners", it) }

    /** "two 1-hour calls" (numbers up to ten in words). */
    fun calls(millis: Long): String? {
        val calls = count(millis / (Equivalents.HOURS_PER_LONG_CALL * HOUR)) ?: return null
        return plural("calls", calls, word("number.$calls") ?: number(calls))
    }

    /** One equivalent of saved time: "a full movie", else "a home-cooked dinner", else pages. */
    fun savedEquivalent(millis: Long): String? {
        val movies = count(millis / (Equivalents.HOURS_PER_MOVIE * HOUR))
        if (movies != null) return if (movies == 1L) word("movies.single") else plural("movies", movies)
        val dinners = count(millis / (Equivalents.MINUTES_PER_HOME_COOKED_DINNER * MINUTE))
        if (dinners != null) return if (dinners == 1L) word("dinners.single") else plural("dinners", dinners)
        return pages(millis)
    }

    /** "20 books": total time as whole books (at least one). */
    fun wholeBooks(millis: Long): String? {
        val books = count(millis / (Equivalents.HOURS_PER_BOOK * HOUR)) ?: return null
        return plural("books", books)
    }

    /** "3 Eiffel Towers". */
    fun landmark(choice: LandmarkCount): String? = plural("landmark.${choice.landmark.key}", choice.count)

    private fun count(value: Double): Long? = floor(value).toLong().takeIf { it >= 1 }

    private fun decimalFormat(minFraction: Int): NumberFormat = NumberFormat.getNumberInstance(locale).apply {
        isGroupingUsed = true
        minimumFractionDigits = minFraction
        maximumFractionDigits = 1
        roundingMode = RoundingMode.FLOOR
    }

    companion object {
        const val N = "{n}"
        const val KEY_ESTIMATE = "estimate"
        private const val MINUTE = 60_000L
        private const val HOUR = 3_600_000L
        private const val METERS_PER_KM = 1_000.0
        private const val WHOLE_KM_FROM = 100.0
        private const val WHOLE_BOOKS_FROM = 10.0
        private const val MIN_BOOKS = 0.1

        /** Rounds down to tenths; the epsilon keeps 1.4 from turning into 1.3999. */
        fun floorTenths(value: Double): Double = floor(value * 10 + 1e-9) / 10

        /** English "an" before 8, 11 and 18 (eight, eleven, eighteen, eighty...). */
        fun startsWithVowelSound(number: String): Boolean {
            val whole = number.takeWhile { it.isDigit() }
            return whole.startsWith("8") || whole == "11" || whole == "18"
        }
    }
}

/** A landmark and how many of it fit in a distance. */
data class LandmarkCount(val landmark: Landmark, val count: Long)

/** Picks a landmark that gives a count between 1 and about 20 for a distance. */
object LandmarkPicker {

    /** Every landmark with a whole count of 1 to [Equivalents.LANDMARK_MAX_COUNT] in [meters]. */
    fun candidates(meters: Double): List<LandmarkCount> = Equivalents.LANDMARKS
        .map { LandmarkCount(it, floor(meters / it.meters).toLong()) }
        .filter { it.count in 1..Equivalents.LANDMARK_MAX_COUNT }

    /**
     * One of the [candidates], at random so messages stay fresh. Beyond the range ("about 20")
     * the largest landmark that fits at least once; null under the shortest landmark.
     */
    fun pick(meters: Double, random: Random): LandmarkCount? {
        val candidates = candidates(meters)
        if (candidates.isNotEmpty()) return candidates[random.nextInt(candidates.size)]
        return Equivalents.LANDMARKS.lastOrNull { meters >= it.meters }
            ?.let { LandmarkCount(it, floor(meters / it.meters).toLong()) }
    }
}
