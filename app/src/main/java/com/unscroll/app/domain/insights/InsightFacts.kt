package com.unscroll.app.domain.insights

import com.unscroll.app.domain.session.Session
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.floor

/** A swipe count: real (from the opt-in scroll counting) or estimated from time ("about"). */
data class SwipeCount(val count: Long, val estimated: Boolean) {
    operator fun plus(other: SwipeCount) = SwipeCount(count + other.count, estimated || other.estimated)

    companion object {
        val NONE = SwipeCount(0, estimated = false)
    }
}

/** Thumb distance: swipes times the swipe length. Never inflated. */
object ThumbDistance {

    /**
     * A swipe moves the thumb [Equivalents.SWIPE_SCREEN_SHARE] of the screen height. The height comes
     * from DisplayMetrics (pixels / dots per inch); an unreadable or implausible screen gets the 6 cm
     * fallback.
     */
    fun swipeLengthMeters(screenHeightPixels: Int, yDotsPerInch: Float): Double {
        if (screenHeightPixels <= 0 || !(yDotsPerInch > 0f)) return Equivalents.FALLBACK_SWIPE_LENGTH_METERS
        val heightMeters = screenHeightPixels / yDotsPerInch.toDouble() * METERS_PER_INCH
        if (heightMeters !in Equivalents.MIN_SCREEN_HEIGHT_METERS..Equivalents.MAX_SCREEN_HEIGHT_METERS) {
            return Equivalents.FALLBACK_SWIPE_LENGTH_METERS
        }
        return heightMeters * Equivalents.SWIPE_SCREEN_SHARE
    }

    /** Without swipe data: [Equivalents.ESTIMATED_SWIPES_PER_MINUTE] per minute, rounded down. */
    fun estimatedSwipes(millis: Long): Long =
        floor(millis.coerceAtLeast(0) / Equivalents.MILLIS_PER_MINUTE * Equivalents.ESTIMATED_SWIPES_PER_MINUTE).toLong()

    fun meters(swipes: Long, swipeLengthMeters: Double): Double = swipes.coerceAtLeast(0) * swipeLengthMeters

    private const val METERS_PER_INCH = 0.0254
}

/**
 * The life projection: the last 30 complete days' average per day, carried over the waking years
 * left (16 waking hours a day; to age 80 when the user gave their age, otherwise 50 more years).
 */
data class LifeProjection(
    val averageMillisPerDay: Long,
    val remainingYears: Int,
    /** The age it runs to, or null when the user's age is unknown. */
    val ageEnd: Int?,
) {
    private val hoursPerDay: Double get() = averageMillisPerDay / Equivalents.MILLIS_PER_HOUR

    /** Waking years spent at this pace. */
    val years: Double get() = hoursPerDay / Equivalents.WAKING_HOURS_PER_DAY * remainingYears

    /** Years at half the pace. */
    val halfYears: Double get() = years / 2

    /** Years back from 30 minutes less a day; null when the average is under 30 minutes. */
    val savedYears: Double?
        get() = if (hoursPerDay < HALF_HOUR) null else HALF_HOUR / Equivalents.WAKING_HOURS_PER_DAY * remainingYears

    /** Full 24-hour days per year. */
    val daysPerYear: Long get() = floor(hoursPerDay * Equivalents.DAYS_PER_YEAR / Equivalents.HOURS_PER_DAY).toLong()

    companion object {
        private const val HALF_HOUR = 0.5

        /**
         * From complete daily totals (oldest first, ending yesterday). Null with fewer than
         * [Equivalents.PROJECTION_MIN_DAYS] days or no years left.
         */
        fun from(completeDays: List<Long>, age: Int?): LifeProjection? {
            if (completeDays.size < Equivalents.PROJECTION_MIN_DAYS) return null
            val recent = completeDays.takeLast(Equivalents.PROJECTION_DAYS)
            val remaining = if (age != null) Equivalents.PROJECTION_END_AGE - age else Equivalents.DEFAULT_REMAINING_YEARS
            if (remaining <= 0) return null
            return LifeProjection(
                averageMillisPerDay = recent.sum() / recent.size,
                remainingYears = remaining,
                ageEnd = if (age != null) Equivalents.PROJECTION_END_AGE else null,
            )
        }
    }
}

/** Usage of all apps or one app in a stretch of time, with the stretch before it to compare. */
data class UsageFacts(
    val millis: Long,
    val opens: Int = 0,
    val longestMillis: Long = 0,
    val averageSessionMillis: Long = 0,
    val swipes: SwipeCount = SwipeCount.NONE,
    /** The same stretch one period earlier, or null when Unscroll wasn't tracking all of it. */
    val previousMillis: Long? = null,
    val previousSwipes: SwipeCount? = null,
    /** Days the numbers span, for "at this pace" (today counts as a whole day, so a pace never grows). */
    val days: Double = 1.0,
) {
    /** Time saved against the stretch before: positive only. */
    val savedMillis: Long? get() = previousMillis?.let { it - millis }?.takeIf { it > 0 }

    /** Percent change against the stretch before, rounded down (null without one). */
    val percentChange: Int?
        get() {
            val previous = previousMillis?.takeIf { it > 0 } ?: return null
            val change = (millis - previous) * 100.0 / previous
            // Toward zero, so neither a rise nor a drop is ever overstated.
            return change.toInt()
        }
}

data class AppFacts(val packageName: String, val label: String, val usage: UsageFacts)

/** Everything the Insights messages can say, computed on the phone from the logged sessions. */
data class InsightFacts(
    val period: UsagePeriod,
    /** All apps in [period], with the period before it. */
    val combined: UsageFacts,
    /** Each app with time in [period], most time first. */
    val apps: List<AppFacts>,
    /** Time in [period] between 22:00 and midnight. */
    val lateMillis: Long,
    /** Today so far, against yesterday up to the same time. */
    val today: UsageFacts,
    /** The last 7 days (today so far included), against the 7 days before up to the same time. */
    val week: UsageFacts,
    /** The average of the 7 days before today, each up to the same time of day. */
    val usualTodayMillis: Long?,
    /** The average opens of the 7 days before today, each up to the same time of day. */
    val usualOpens: Int?,
    /** The lightest of the last 7 complete days. */
    val bestDay: DayOfWeek?,
    /** Complete days since the first tracked day (today not included). */
    val completeDays: Int,
    /** Days since the first tracked day, today included (1 on the first day). */
    val daysOfUse: Int,
    val projection: LifeProjection?,
    /** The average per day of the last 7 complete days. */
    val lastWeekDailyMillis: Long?,
    /** The average per day of the first 7 complete days. */
    val firstWeekDailyMillis: Long?,
    val allTimeMillis: Long,
    /** Time reclaimed since the first week: its daily average times the days since, minus the actual time. */
    val totalSavedMillis: Long?,
    /** Days in a row under the limit (goal and daily limits), today included while under; null without limits. */
    val streak: Int?,
    val underLimitToday: Boolean?,
    val underLimitYesterday: Boolean?,
    /** The last 7 complete days' total when it is the lowest of every tracked week. */
    val bestWeekMillis: Long?,
    /** Apps used on most of the last days that weren't opened today / all of yesterday. */
    val notOpenedToday: List<String>,
    val notOpenedYesterday: List<String>,
    val swipeLengthMeters: Double,
    /** The app's name when only one app was used in the last 30 days ("{apps_phrase}"). */
    val singleAppLabel: String?,
) {
    /** "No [B] in the first 3 days of use". */
    val inFirstDays: Boolean get() = daysOfUse <= FIRST_DAYS

    companion object {
        const val FIRST_DAYS = 3
    }
}

/** What [InsightFactsBuilder] reads. The repository loads it; the builder is pure. */
data class InsightInputs(
    val now: Long,
    val zone: ZoneId,
    val period: UsagePeriod,
    /** The period's totals, opens, sessions and hours (all time included). */
    val periodUsage: PeriodUsage,
    /** Counted swipes per app in the period, or null when scroll counting didn't cover all of it. */
    val periodSwipes: Map<String, Int>?,
    /** Apps whose swipes are counted ("Count swipes" on). */
    val countedApps: Set<String>,
    /** When scroll counting was first on, or null if never. */
    val countingSince: Long?,
    /** Sessions overlapping the last [InsightFactsBuilder.HISTORY_DAYS] days. */
    val sessions: List<Session>,
    val firstSessionStart: Long?,
    val allTimeMillis: Long,
    /** Total of the first 7 local days from the first tracked day. */
    val firstWeekMillis: Long?,
    val labels: Map<String, String>,
    val goalMinutes: Int?,
    /** Daily limits of the active apps, in minutes. */
    val dailyLimitMinutes: Map<String, Int>,
    val activeApps: Set<String>,
    val swipeLengthMeters: Double,
    val age: Int?,
) {
    fun labelOf(packageName: String): String = labels[packageName] ?: packageName
}

/** Turns sessions into [InsightFacts]. Pure; every comparison only uses days Unscroll tracked. */
object InsightFactsBuilder {

    /** How far back the history goes (streaks, best week, comparisons). */
    const val HISTORY_DAYS = 120

    /** "Most of the last days": an app used on at least this many of 7 days. */
    const val REGULAR_DAYS = 5

    private const val WEEK = 7
    private const val MONTH = 30
    private const val MINUTE = 60_000L

    fun build(inputs: InsightInputs): InsightFacts {
        val zone = inputs.zone
        val now = inputs.now
        val today = localDate(now, zone)
        val todayStart = startOfDay(today, zone)
        val elapsed = now - todayStart
        val firstDay = inputs.firstSessionStart?.let { localDate(it, zone) } ?: today
        val completeDays = ChronoUnit.DAYS.between(firstDay, today).toInt().coerceAtLeast(0)
        val history = History(inputs, today, firstDay)

        val period = periodFacts(inputs, history, today, elapsed, completeDays)
        val daily = history.completeDailyTotals(completeDays)
        val lastWeekDaily = if (completeDays >= WEEK) daily.takeLast(WEEK).sum() / WEEK else null
        val firstWeekDaily = if (completeDays >= WEEK) inputs.firstWeekMillis?.let { it / WEEK } else null
        val underToday = history.underLimit(today, now)

        return InsightFacts(
            period = inputs.period,
            combined = period.first,
            apps = period.second,
            lateMillis = inputs.periodUsage.hourly.drop(Equivalents.LATE_NIGHT_FROM_HOUR).sum(),
            today = history.stretch(null, today, 1, elapsed, inputs.periodUsageIfToday()),
            week = history.stretch(null, today, WEEK, elapsed),
            usualTodayMillis = if (completeDays >= WEEK) {
                (1..WEEK).sumOf { history.millisUpTo(today.minusDays(it.toLong()), elapsed) } / WEEK
            } else {
                null
            },
            usualOpens = if (completeDays >= WEEK) {
                (1..WEEK).sumOf { history.opensUpTo(today.minusDays(it.toLong()), elapsed) } / WEEK
            } else {
                null
            },
            bestDay = if (completeDays >= WEEK) {
                (1..WEEK).map { today.minusDays(it.toLong()) }
                    .minByOrNull { history.dayTotal(it) }?.dayOfWeek
            } else {
                null
            },
            completeDays = completeDays,
            daysOfUse = completeDays + 1,
            projection = LifeProjection.from(daily, inputs.age),
            lastWeekDailyMillis = lastWeekDaily,
            firstWeekDailyMillis = firstWeekDaily,
            allTimeMillis = inputs.allTimeMillis,
            totalSavedMillis = totalSaved(inputs, history, today, completeDays),
            streak = streak(history, today, firstDay, now, underToday),
            underLimitToday = underToday,
            underLimitYesterday = if (completeDays >= 1) history.underLimit(today.minusDays(1), now) else null,
            bestWeekMillis = bestWeek(history, today, firstDay),
            notOpenedToday = if (completeDays >= WEEK) history.notOpened(today, today.minusDays(1)) else emptyList(),
            notOpenedYesterday = if (completeDays >= WEEK + 1) {
                history.notOpened(today.minusDays(1), today.minusDays(2))
            } else {
                emptyList()
            },
            swipeLengthMeters = inputs.swipeLengthMeters,
            singleAppLabel = history.appsUsedSince(today.minusDays(MONTH.toLong() - 1)).singleOrNull()?.let { inputs.labelOf(it) },
        )
    }

    /** The selected period: all apps, then each app. */
    private fun periodFacts(
        inputs: InsightInputs,
        history: History,
        today: LocalDate,
        elapsed: Long,
        completeDays: Int,
    ): Pair<UsageFacts, List<AppFacts>> {
        val days = when (inputs.period) {
            UsagePeriod.TODAY -> 1
            UsagePeriod.WEEK -> WEEK
            UsagePeriod.MONTH -> MONTH
            UsagePeriod.ALL_TIME -> null
        }
        // Today counts as a whole day: a pace can only come out lower than it is.
        val spanDays = (completeDays + 1).let { if (days != null) minOf(days, it) else it }.toDouble()
        val apps = inputs.periodUsage.apps.map { app ->
            val swipes = periodSwipes(inputs, app.packageName, app.usage.totalMillis)
            val previous = days?.let { history.stretch(app.packageName, today, it, elapsed) }
            AppFacts(
                packageName = app.packageName,
                label = inputs.labelOf(app.packageName),
                usage = UsageFacts(
                    millis = app.usage.totalMillis,
                    opens = app.usage.opens,
                    longestMillis = app.usage.longestSessionMillis,
                    averageSessionMillis = app.usage.averageSessionMillis,
                    swipes = swipes,
                    previousMillis = previous?.previousMillis,
                    previousSwipes = previous?.previousSwipes,
                    days = spanDays,
                ),
            )
        }
        val combined = inputs.periodUsage.combined
        val previous = days?.let { history.stretch(null, today, it, elapsed) }
        return UsageFacts(
            millis = combined.totalMillis,
            opens = combined.opens,
            longestMillis = combined.longestSessionMillis,
            averageSessionMillis = combined.averageSessionMillis,
            swipes = apps.fold(SwipeCount.NONE) { sum, app -> sum + app.usage.swipes },
            previousMillis = previous?.previousMillis,
            previousSwipes = previous?.previousSwipes,
            days = spanDays,
        ) to apps
    }

    private fun periodSwipes(inputs: InsightInputs, packageName: String, millis: Long): SwipeCount {
        val counted = inputs.periodSwipes
        return if (counted != null && packageName in inputs.countedApps) {
            SwipeCount(counted[packageName]?.toLong() ?: 0L, estimated = false)
        } else {
            SwipeCount(ThumbDistance.estimatedSwipes(millis), estimated = true)
        }
    }

    private fun totalSaved(inputs: InsightInputs, history: History, today: LocalDate, completeDays: Int): Long? {
        val firstWeek = inputs.firstWeekMillis ?: return null
        val daysAfter = completeDays - WEEK
        if (daysAfter < 1) return null
        val todayMillis = history.millisUpTo(today, Long.MAX_VALUE)
        val actual = inputs.allTimeMillis - todayMillis - firstWeek
        val saved = firstWeek / WEEK * daysAfter - actual
        return saved.takeIf { it >= MINUTE }
    }

    private fun streak(history: History, today: LocalDate, firstDay: LocalDate, now: Long, underToday: Boolean?): Int? {
        if (underToday == null) return null
        var days = 0
        var day = today.minusDays(1)
        while (!day.isBefore(firstDay) && !day.isBefore(history.windowStartDay) && history.underLimit(day, now) == true) {
            days++
            day = day.minusDays(1)
        }
        return if (underToday) days + 1 else 0
    }

    /** The last 7 complete days, if lower than every earlier 7 days (at least two of them, all tracked). */
    private fun bestWeek(history: History, today: LocalDate, firstDay: LocalDate): Long? {
        // "Yet" means ever: the whole history has to be in the window.
        if (firstDay.isBefore(history.windowStartDay)) return null
        fun weekTotal(endExclusive: LocalDate) = (1..WEEK).sumOf { history.dayTotal(endExclusive.minusDays(it.toLong())) }
        val current = weekTotal(today)
        var earlier = 0
        var end = today.minusDays(WEEK.toLong())
        while (!end.minusDays(WEEK.toLong()).isBefore(firstDay)) {
            if (weekTotal(end) <= current) return null
            earlier++
            end = end.minusDays(WEEK.toLong())
        }
        return current.takeIf { earlier >= 2 }
    }

    /** Only for TODAY: the period's own numbers are today's. */
    private fun InsightInputs.periodUsageIfToday(): UsageSummary? =
        if (period == UsagePeriod.TODAY) periodUsage.combined else null

    /** Day-by-day views of the loaded sessions. */
    private class History(
        private val inputs: InsightInputs,
        private val today: LocalDate,
        private val firstDay: LocalDate,
    ) {
        private val zone = inputs.zone
        private val now = inputs.now
        val windowStartDay: LocalDate = today.minusDays(HISTORY_DAYS.toLong())
        private val byApp = inputs.sessions.groupBy { it.packageName }
        private val dayRange = TimeRange(startOfDay(windowStartDay, zone), startOfDay(today.plusDays(1), zone))
        private val appDays: Map<String, Map<LocalDate, Long>> =
            byApp.mapValues { (_, sessions) -> UsageMath.totalsByDay(sessions, dayRange, zone, now) }

        fun dayTotal(day: LocalDate): Long = appDays.values.sumOf { it[day] ?: 0L }

        fun appDayTotal(packageName: String, day: LocalDate): Long = appDays[packageName]?.get(day) ?: 0L

        /** Complete days from the first tracked day (within the window) to yesterday, oldest first. */
        fun completeDailyTotals(completeDays: Int): List<Long> {
            val count = minOf(completeDays, HISTORY_DAYS)
            return (count downTo 1).map { dayTotal(today.minusDays(it.toLong())) }
        }

        fun millisUpTo(day: LocalDate, elapsed: Long): Long {
            val start = startOfDay(day, zone)
            val end = minOf(startOfDay(day.plusDays(1), zone), start + elapsed.coerceAtMost(DAY_MILLIS))
            val range = TimeRange(start, end)
            return inputs.sessions.sumOf { UsageMath.overlap(it, range, now) }
        }

        fun opensUpTo(day: LocalDate, elapsed: Long): Int {
            val start = startOfDay(day, zone)
            return inputs.sessions.count { it.startTime >= start && it.startTime < start + elapsed }
        }

        /**
         * [days] days ending today (today up to [elapsed]) for one app or all, against the same
         * stretch [days] days earlier when that one was fully tracked.
         */
        fun stretch(packageName: String?, today: LocalDate, days: Int, elapsed: Long, own: UsageSummary? = null): UsageFacts {
            val first = today.minusDays(days.toLong() - 1)
            val range = TimeRange(startOfDay(first, zone), startOfDay(today, zone) + elapsed)
            val previousFirst = first.minusDays(days.toLong())
            val previousRange = TimeRange(startOfDay(previousFirst, zone), startOfDay(today.minusDays(days.toLong()), zone) + elapsed)
            val sessions = if (packageName == null) inputs.sessions else byApp[packageName].orEmpty()
            val current = sessions.sumOf { UsageMath.overlap(it, range, now) }
            val firstStart = inputs.firstSessionStart
            val previousTracked = firstStart != null && firstStart <= previousRange.from && !previousFirst.isBefore(windowStartDay)
            val started = sessions.filter { it.startTime >= range.from && it.startTime < range.to }
            return UsageFacts(
                millis = own?.totalMillis ?: current,
                opens = own?.opens ?: started.size,
                longestMillis = own?.longestSessionMillis ?: started.maxOfOrNull { (it.endTime ?: now) - it.startTime } ?: 0L,
                averageSessionMillis = own?.averageSessionMillis
                    ?: if (started.isEmpty()) 0L else started.sumOf { (it.endTime ?: now) - it.startTime } / started.size,
                swipes = swipes(sessions, range, current),
                previousMillis = if (previousTracked) sessions.sumOf { UsageMath.overlap(it, previousRange, now) } else null,
                previousSwipes = if (previousTracked) {
                    swipes(sessions, previousRange, sessions.sumOf { UsageMath.overlap(it, previousRange, now) })
                } else {
                    null
                },
                days = days.toDouble(),
            )
        }

        /** Counted swipes when scroll counting covered all of [range] for every app in it; else estimated. */
        private fun swipes(sessions: List<Session>, range: TimeRange, millis: Long): SwipeCount {
            val since = inputs.countingSince
            val inRange = sessions.filter { it.startTime >= range.from && it.startTime < range.to }
            val allCounted = inRange.all { it.packageName in inputs.countedApps }
            return if (since != null && since <= range.from && allCounted) {
                SwipeCount(inRange.sumOf { it.scrollCount.toLong() }, estimated = false)
            } else {
                SwipeCount(ThumbDistance.estimatedSwipes(millis), estimated = true)
            }
        }

        /** Under the goal and every daily limit on [day]; null when there is no limit at all. */
        fun underLimit(day: LocalDate, now: Long): Boolean? {
            val goal = inputs.goalMinutes
            val limits = inputs.dailyLimitMinutes
            if (goal == null && limits.isEmpty()) return null
            if (goal != null && dayTotal(day) > goal * MINUTE) return false
            return limits.all { (packageName, minutes) -> appDayTotal(packageName, day) <= minutes * MINUTE }
        }

        /** Active apps without a session on [day] that were used on most of the 7 days up to [lastBefore]. */
        fun notOpened(day: LocalDate, lastBefore: LocalDate): List<String> = inputs.activeApps
            .filter { appDayTotal(it, day) == 0L && opensOn(it, day) == 0 }
            .filter { packageName ->
                (0 until WEEK).count { appDayTotal(packageName, lastBefore.minusDays(it.toLong())) > 0 } >= REGULAR_DAYS
            }
            .sorted()
            .map { inputs.labelOf(it) }

        private fun opensOn(packageName: String, day: LocalDate): Int {
            val start = startOfDay(day, zone)
            val end = startOfDay(day.plusDays(1), zone)
            return byApp[packageName].orEmpty().count { it.startTime in start until end }
        }

        fun appsUsedSince(from: LocalDate): List<String> = appDays
            .filter { (_, days) -> days.any { (day, millis) -> !day.isBefore(from) && millis > 0 } }
            .keys.sorted()
    }

    private const val DAY_MILLIS = 24 * 60 * 60_000L
}
