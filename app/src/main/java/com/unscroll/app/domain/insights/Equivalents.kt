package com.unscroll.app.domain.insights

/**
 * Conversion constants for "hours invested" and the Insights messages. Keep them all here, exactly
 * as in docs/insight-templates.md ("Constants"), so the numbers stay honest. Never pick a value that
 * makes usage look bigger than it is.
 */
object Equivalents {
    const val HOURS_PER_DAY = 24.0

    /** Roughly how long it takes to read a typical novel. */
    const val HOURS_PER_BOOK = 6.0

    /** Roughly a direct flight from Europe or the US west coast to Tokyo (Helsinki to Tokyo style). */
    const val HOURS_PER_FLIGHT_TO_TOKYO = 12.0

    const val MILLIS_PER_HOUR = 3_600_000.0
    const val MILLIS_PER_MINUTE = 60_000.0

    /** A swipe moves the thumb about this share of the screen height (from DisplayMetrics). */
    const val SWIPE_SCREEN_SHARE = 0.55

    /** Swipe length when the screen size can't be read: 6 cm. Never invent a bigger number. */
    const val FALLBACK_SWIPE_LENGTH_METERS = 0.06

    /** Screen heights outside this range are treated as unreadable (the fallback applies). */
    const val MIN_SCREEN_HEIGHT_METERS = 0.08
    const val MAX_SCREEN_HEIGHT_METERS = 0.30

    /** Swipes per minute when there is no swipe data. Results are labelled "about". */
    const val ESTIMATED_SWIPES_PER_MINUTE = 4.0

    const val HOURS_PER_MOVIE = 2.0

    /** Reading speed: conservative. */
    const val MINUTES_PER_PAGE = 2.0

    /** Walking: about 5 km/h. */
    const val MINUTES_PER_KM_WALKING = 12.0

    const val MINUTES_PER_HOME_COOKED_DINNER = 45.0

    const val HOURS_PER_LONG_CALL = 1.0

    /** Life projection: waking hours in a day. */
    const val WAKING_HOURS_PER_DAY = 16.0

    /** Life projection: the age it runs to when the user gave theirs. */
    const val PROJECTION_END_AGE = 80

    /** Life projection: years ahead when the age is unknown. */
    const val DEFAULT_REMAINING_YEARS = 50

    /** Life projection: the average of the last this many complete days... */
    const val PROJECTION_DAYS = 30

    /** ...shown only once there are at least this many complete days of data. */
    const val PROJECTION_MIN_DAYS = 7

    /** Full days per year, for "{days_per_year} full days of your year". */
    const val DAYS_PER_YEAR = 365.0

    const val MARATHON_METERS = 42_195.0

    /** "After 22:00": usage from this local hour until midnight. */
    const val LATE_NIGHT_FROM_HOUR = 22

    /** The landmark picker aims for a count between 1 and about this many. */
    const val LANDMARK_MAX_COUNT = 20

    /** Landmarks for thumb distance, shortest first. The key names the words in phrases.tsv. */
    val LANDMARKS: List<Landmark> = listOf(
        Landmark("pool", 50.0),
        Landmark("liberty", 93.0),
        Landmark("big_ben", 96.0),
        Landmark("pitch", 105.0),
        Landmark("eiffel", 330.0),
        Landmark("empire_state", 443.0),
        Landmark("burj_khalifa", 828.0),
        Landmark("everest", 8_849.0),
        Landmark("marathon", MARATHON_METERS),
        Landmark("great_wall", 21_196_000.0),
    )
}

/** A landmark of a known length or height, in meters. */
data class Landmark(val key: String, val meters: Double)
