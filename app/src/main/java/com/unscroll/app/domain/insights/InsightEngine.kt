package com.unscroll.app.domain.insights

import com.unscroll.app.domain.fox.FoxMood
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.floor
import kotlin.random.Random

/** One filled-in message, ready to show. */
data class Insight(
    val templateId: String,
    val category: InsightCategory,
    val tone: InsightTone?,
    val text: String,
    /** The big number (a distance, time or years), shown bold; null for messages without one. */
    val highlight: String?,
    /** The fox's mood next to it. */
    val mood: FoxMood,
) {
    /** What the share image shows: the big number and the line. */
    val share: InsightShare? get() = if (category == InsightCategory.FOX) null else InsightShare(highlight, text)
}

data class InsightShare(val number: String?, val line: String)

/** The persisted rotation state (DataStore). */
data class InsightHistory(
    /** Template ids, oldest first, at most [NO_REPEAT_WITHIN]. */
    val recentIds: List<String> = emptyList(),
    val lastCategory: InsightCategory? = null,
    /** When a life projection was last shown. */
    val lastLifeAt: Long? = null,
) {
    fun record(insight: Insight, now: Long): InsightHistory = InsightHistory(
        recentIds = (recentIds + insight.templateId).takeLast(NO_REPEAT_WITHIN),
        lastCategory = insight.category,
        lastLifeAt = if (insight.category == InsightCategory.LIFE) now else lastLifeAt,
    )

    companion object {
        /** A template is never repeated within its last 30 uses. */
        const val NO_REPEAT_WITHIN = 30
    }
}

/** What the fox's lines can mention (FoxViewModel). */
data class FoxFacts(
    val trackingEnabled: Boolean = true,
    /** A goal or any daily time limit is set. */
    val hasLimit: Boolean = false,
    /** Time left before the closest daily limit today, if any is left. */
    val remainingMillis: Long? = null,
    /** The app with the most time today. */
    val topAppLabel: String? = null,
)

/**
 * Fills the bank's templates from [InsightFacts] and picks the next one under the rotation rules
 * (docs/insight-templates.md). Pure: the clock and the random source come from the caller.
 *
 * Rotation: never a template from the last 30 uses, never the same category twice in a row, only
 * tones the setting allows ([G] always, [H] for Honest and Brutal, [B] only for Brutal), no [B] in
 * the first 3 days of use or right after a day under the limit, no negative message (categories 1
 * to 4 with [H]/[B]) on a day under the limit (Wins come first then), and life projections only on
 * the weekly card, at most once a week. A template whose placeholders can't all be filled with real
 * data is skipped and another one is tried.
 */
class InsightEngine(
    private val bank: InsightBank,
    private val locale: Locale = Locale.getDefault(),
) {
    private val format = InsightFormatter(bank.phrases, locale)

    /** The next message for the Home card, or null when none qualifies. */
    fun next(
        facts: InsightFacts,
        history: InsightHistory,
        tone: InsightTone,
        random: Random,
        exclude: String? = null,
    ): Insight? = pick(facts, history, tone, random, weekly = false, exclude = exclude)

    /** The weekly life projection card, or null (not enough data, or one was shown in the last week). */
    fun weekly(facts: InsightFacts, history: InsightHistory, tone: InsightTone, now: Long, random: Random): Insight? {
        val last = history.lastLifeAt
        if (last != null && now - last < WEEK_MILLIS) return null
        return pick(facts, history, tone, random, weekly = true, exclude = null)
    }

    /** A fox speech bubble for [mood] (the bank's fox section), never [lastId] again. */
    fun foxLine(mood: FoxMood, fox: FoxFacts, lastId: String?, random: Random): Insight? {
        val wanted = if (mood == FoxMood.NEUTRAL) FoxMood.HAPPY else mood
        val candidates = bank.templates.filter { it.category == InsightCategory.FOX && it.mood == wanted }
        val allowed = candidates.filter { it.id != lastId && InsightTemplateRules.foxAllowed(it.id, fox) }
            .ifEmpty { candidates.filter { InsightTemplateRules.foxAllowed(it.id, fox) } }
        return firstFilled(allowed, random) { fillFox(it, fox, mood) }
    }

    /** The template [id] filled from [facts] (to show a picked message again with fresh numbers). */
    fun render(id: String, facts: InsightFacts, random: Random): Insight? =
        bank.template(id)?.let { render(it, facts, random) }

    /** [template] filled from [facts], or null when any placeholder lacks real data. */
    fun render(template: InsightTemplate, facts: InsightFacts, random: Random): Insight? {
        if (template.category == InsightCategory.FOX) return null
        if (!InsightTemplateRules.allowed(template, facts)) return null
        val scope = InsightTemplateRules.scope(template, facts, format) ?: return null
        val values = HashMap<String, Value>()
        for (name in template.placeholders.distinct()) {
            values[name] = value(name, template, facts, scope, random) ?: return null
        }
        return Insight(
            templateId = template.id,
            category = template.category,
            tone = template.tone,
            text = substitute(template.text, values),
            highlight = HIGHLIGHT_ORDER.firstOrNull { it in values }?.let { name ->
                val text = values.getValue(name).text
                if (name == "years") format.plural("years", 2, text) else text
            },
            mood = moodFor(template),
        )
    }

    private fun pick(
        facts: InsightFacts,
        history: InsightHistory,
        tone: InsightTone,
        random: Random,
        weekly: Boolean,
        exclude: String?,
    ): Insight? {
        val eligible = bank.templates.filter { template ->
            template.id != exclude && eligible(template, facts, history, tone, weekly)
        }
        // On a day under the limit, Wins first (negative messages are already out).
        val preferred = if (facts.underLimitToday == true) eligible.filter { it.category == InsightCategory.WINS } else emptyList()
        return firstFilled(preferred, random) { render(it, facts, random) }
            ?: firstFilled(eligible - preferred.toSet(), random) { render(it, facts, random) }
    }

    private fun eligible(
        template: InsightTemplate,
        facts: InsightFacts,
        history: InsightHistory,
        tone: InsightTone,
        weekly: Boolean,
    ): Boolean {
        if (template.category == InsightCategory.FOX) return false
        if ((template.category == InsightCategory.LIFE) != weekly) return false
        if (template.id in history.recentIds) return false
        if (!weekly && template.category == history.lastCategory) return false
        val tag = template.tone ?: return false
        if (!tag.allowedWith(tone)) return false
        if (tag == InsightTone.BRUTAL && (facts.inFirstDays || facts.underLimitYesterday == true)) return false
        if (template.isNegative && facts.underLimitToday == true) return false
        return true
    }

    /** Tries candidates in random order until one fills. */
    private fun firstFilled(
        candidates: List<InsightTemplate>,
        random: Random,
        fill: (InsightTemplate) -> Insight?,
    ): Insight? {
        val left = candidates.toMutableList()
        while (left.isNotEmpty()) {
            val template = left.removeAt(random.nextInt(left.size))
            fill(template)?.let { return it }
        }
        return null
    }

    private fun fillFox(template: InsightTemplate, fox: FoxFacts, mood: FoxMood): Insight? {
        val values = HashMap<String, Value>()
        for (name in template.placeholders.distinct()) {
            values[name] = when (name) {
                "remaining" -> fox.remainingMillis?.let { format.remaining(it) }
                "app" -> fox.topAppLabel
                else -> null
            }?.let { Value(it) } ?: return null
        }
        return Insight(template.id, template.category, null, substitute(template.text, values), null, mood)
    }

    private fun value(name: String, template: InsightTemplate, facts: InsightFacts, scope: Scope, random: Random): Value? {
        val usage = scope.usage
        val swipes = usage.swipes
        val meters = ThumbDistance.meters(swipes.count, facts.swipeLengthMeters)
        val projection = facts.projection
        return when (name) {
            "period" -> scope.periodWord?.let { Value(it) }
            "time" -> format.duration(usage.millis)?.let { Value(it) }
            "app" -> scope.appLabel?.let { Value(it) }
            "apps_phrase" -> (facts.singleAppLabel ?: format.word("apps.generic"))?.let { Value(it) }
            "swipes" -> swipes.takeIf { it.count >= MIN_PLURAL }?.let { estimated(format.number(it.count), it.estimated) }
            "distance" -> format.distance(meters)?.let { estimated(it, swipes.estimated) }
            "landmark_phrase" -> LandmarkPicker.pick(meters, random)?.let { format.landmark(it) }
                ?.let { estimated(it, swipes.estimated) }
            "marathon_pct" -> floor(meters / Equivalents.MARATHON_METERS * PERCENT).toLong().takeIf { it >= 1 }
                ?.let { estimated(format.number(it), swipes.estimated) }
            "movies_phrase" -> format.movies(usage.millis)?.let { Value(it) }
            "pages_phrase" -> format.pages(usage.millis)?.let { Value(it) }
            "books_phrase" -> format.books(usage.millis)?.let { Value(it) }
            "walk_phrase" -> format.walk(usage.millis)?.let { Value(it) }
            "flight_phrase" -> format.flights(usage.millis)?.let { Value(it) }
            "dinners_phrase" -> format.dinners(scope.dinnersMillis ?: usage.millis)?.let { Value(it) }
            "calls_phrase" -> format.calls(usage.millis)?.let { Value(it) }
            "month_hours" -> monthMillis(usage)?.let { floor(it / Equivalents.MILLIS_PER_HOUR).toLong() }
                ?.takeIf { it >= MIN_PLURAL }?.let { Value(format.number(it)) }
            "month_books_phrase" -> monthMillis(usage)?.let { format.books(it.toLong()) }?.let { Value(it) }
            "years" -> projection?.years?.takeIf { it >= MIN_YEARS }?.let { Value(format.oneDecimal(it)) }
            "half_years" -> projection?.halfYears?.takeIf { it >= MIN_YEARS }?.let { Value(format.oneDecimal(it)) }
            "saved_years" -> projection?.savedYears?.takeIf { it >= MIN_YEARS }?.let { Value(format.oneDecimal(it)) }
            "days_per_year" -> projection?.daysPerYear?.takeIf { it >= MIN_PLURAL }?.let { Value(format.number(it)) }
            "hours_per_day" -> (scope.dailyMillis ?: projection?.averageMillisPerDay)
                ?.let { format.duration(it) }?.let { Value(it) }
            "age_end" -> projection?.ageEnd?.let { Value(format.number(it.toLong())) }
            "pct" -> scope.percent?.takeIf { it >= 1 }?.let { Value(format.number(it.toLong())) }
            "time_diff" -> scope.timeDiffMillis?.let { format.duration(it) }?.let { Value(it) }
            "best_day" -> facts.bestDay?.getDisplayName(TextStyle.FULL, locale)?.let { Value(it) }
            "opens" -> usage.opens.takeIf { it >= MIN_PLURAL }?.let { Value(format.number(it.toLong())) }
            "avg_opens" -> facts.usualOpens?.takeIf { it >= MIN_PLURAL }?.let { Value(format.number(it.toLong())) }
            "longest" -> format.duration(usage.longestMillis)?.let { Value(it) }
            "avg_session" -> format.duration(usage.averageSessionMillis)?.let { Value(it) }
            "late_pct" -> latePercent(facts)?.let { Value(format.number(it)) }
            "streak" -> facts.streak?.takeIf { it >= MIN_PLURAL }?.let { Value(format.number(it.toLong())) }
            "saved_time" -> usage.savedMillis?.let { format.duration(it) }?.let { Value(it) }
            "saved_equiv" -> (scope.savedEquivMillis ?: usage.savedMillis)?.let { format.savedEquivalent(it) }?.let { Value(it) }
            "saved_distance" -> savedDistance(usage, facts.swipeLengthMeters)
            "total_saved" -> facts.totalSavedMillis?.let { format.hoursOrDuration(it) }?.let { Value(it) }
            "total_hours" -> (facts.allTimeMillis / HOUR).takeIf { it >= MIN_PLURAL }?.let { Value(format.number(it)) }
            "days_total" -> (facts.allTimeMillis / DAY).takeIf { it >= MIN_PLURAL }?.let { Value(format.number(it)) }
            "books_total_phrase" -> format.wholeBooks(facts.allTimeMillis)?.let { Value(it) }
            // "{remaining}" is for the fox only (fillFox): elsewhere it has no data.
            else -> null
        }
    }

    private fun estimated(text: String, estimated: Boolean) = Value(text, estimated)

    /** "At this pace, a month": the period's daily average times 30. */
    private fun monthMillis(usage: UsageFacts): Double? =
        if (usage.days <= 0.0 || usage.millis <= 0) null else usage.millis / usage.days * MONTH_DAYS

    private fun latePercent(facts: InsightFacts): Long? {
        val total = facts.combined.millis
        if (total <= 0) return null
        return floor(facts.lateMillis * PERCENT / total).toLong().takeIf { it >= 1 }
    }

    /** Week over week, like for like: counted swipes on both sides, or estimates on both. */
    private fun savedDistance(usage: UsageFacts, swipeLength: Double): Value? {
        val previousMillis = usage.previousMillis ?: return null
        val previous = usage.previousSwipes ?: return null
        val estimated = previous.estimated || usage.swipes.estimated
        val now = if (estimated) ThumbDistance.estimatedSwipes(usage.millis) else usage.swipes.count
        val before = if (estimated) ThumbDistance.estimatedSwipes(previousMillis) else previous.count
        val meters = ThumbDistance.meters(before - now, swipeLength)
        return format.distance(meters)?.let { Value(it, estimated) }
    }

    /** Puts the values in; "about" goes in front of estimates unless the template already says it. */
    private fun substitute(text: String, values: Map<String, Value>): String {
        val out = StringBuilder()
        var last = 0
        for (match in InsightTemplate.PLACEHOLDER.findAll(text)) {
            out.append(text, last, match.range.first)
            val value = values.getValue(match.groupValues[1])
            val saysAbout = out.endsWith(ABOUT, ignoreCase = true)
            val filled = if (value.estimated && !saysAbout) format.estimate(value.text) else value.text
            out.append(if (startsSentence(out)) capitalize(filled) else filled)
            last = match.range.last + 1
        }
        out.append(text, last, text.length)
        return out.toString()
    }

    private fun startsSentence(before: CharSequence): Boolean {
        val trimmed = before.trimEnd()
        return trimmed.isEmpty() || trimmed.last() in SENTENCE_ENDS
    }

    private fun capitalize(text: String): String =
        text.replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }

    private fun moodFor(template: InsightTemplate): FoxMood = when {
        template.category == InsightCategory.WINS || template.category == InsightCategory.MILESTONES -> FoxMood.HAPPY
        template.tone == InsightTone.BRUTAL -> FoxMood.CONCERNED
        template.tone == InsightTone.HONEST -> FoxMood.ALERT
        else -> FoxMood.HAPPY
    }

    /** A filled-in value; [estimated] numbers get "about". */
    private data class Value(val text: String, val estimated: Boolean = false)

    companion object {
        const val WEEK_MILLIS = 7 * 24 * 3_600_000L
        private const val HOUR = 3_600_000L
        private const val DAY = 24 * HOUR
        private const val PERCENT = 100.0
        private const val MONTH_DAYS = 30.0

        /** Placeholders followed by a plural noun in the templates ("{swipes} swipes") need 2 or more. */
        private const val MIN_PLURAL = 2

        private const val MIN_YEARS = 0.1
        private const val ABOUT = "about "
        private val SENTENCE_ENDS = setOf('.', '!', '?')

        /** Which value becomes the big number: the first of these the template has. */
        private val HIGHLIGHT_ORDER = listOf(
            "distance", "saved_distance", "years", "time", "saved_time", "total_saved",
            "hours_per_day", "longest", "time_diff", "remaining",
        )

        /** Every placeholder the engine fills (InsightBankTest checks the docs table and the bank against it). */
        val SUPPORTED_PLACEHOLDERS: Set<String> = setOf(
            "period", "time", "app", "apps_phrase", "swipes", "distance", "landmark_phrase", "marathon_pct",
            "movies_phrase", "pages_phrase", "books_phrase", "walk_phrase", "flight_phrase", "dinners_phrase",
            "calls_phrase", "month_hours", "month_books_phrase", "years", "half_years", "saved_years",
            "days_per_year", "hours_per_day", "age_end", "pct", "time_diff", "best_day", "opens", "avg_opens",
            "longest", "avg_session", "late_pct", "streak", "remaining", "saved_time", "saved_equiv",
            "saved_distance", "total_saved", "total_hours", "days_total", "books_total_phrase",
        )
    }
}

/** Which numbers a template's placeholders refer to. */
internal data class Scope(
    val usage: UsageFacts,
    val periodWord: String?,
    val appLabel: String? = null,
    /** "{pct}" for this template (already signed the way the text reads), if any. */
    val percent: Int? = null,
    /** "{time_diff}" for this template, if any. */
    val timeDiffMillis: Long? = null,
    /** "{hours_per_day}" when it isn't the 30-day projection average. */
    val dailyMillis: Long? = null,
    /** "{saved_equiv}" when it isn't the time saved in [usage]. */
    val savedEquivMillis: Long? = null,
    /** "{dinners_phrase}" when it isn't [usage]'s time. */
    val dinnersMillis: Long? = null,
)

/**
 * The per-template rules that keep every message true: which numbers each template talks about
 * (its period, its app, what "less" compares with) and when it applies at all. Keyed by the ids in
 * docs/insight-templates.md; the texts stay in the bank.
 */
internal object InsightTemplateRules {

    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE

    /** R15's "short visits": an average session up to 5 minutes, over at least 3 opens. */
    private const val SHORT_SESSION_MILLIS = 5 * MINUTE
    private const val SHORT_SESSION_MIN_OPENS = 3

    /** M02 "First week done": during the second week. */
    private val SECOND_WEEK = 7..13

    /** M04 "30 days with Unscroll": during the week after day 30. */
    private val DAY_30_WEEK = 30..36

    /** M03 "7 days under your limit. Seven!" */
    private const val SEVEN = 7

    /** M10 "100 hours reclaimed". */
    private const val HUNDRED_HOURS = 100 * HOUR

    /** Templates about today, whatever period Home shows. */
    private val TODAY_TEMPLATES = setOf("E30", "R03", "R04", "R16", "W08", "W14")

    /** Templates about the last 7 days against the 7 before ("than last week"). */
    private val WEEK_TEMPLATES = setOf("R01", "R02", "W01", "W06", "W07")

    fun allowed(template: InsightTemplate, facts: InsightFacts): Boolean = when (template.id) {
        // "A day like {period}": only for a day.
        "E29" -> facts.period == UsagePeriod.TODAY
        // "biggest time sink": needs a second app to compare with.
        "R06" -> facts.apps.count { it.usage.millis >= MINUTE } >= 2
        "R15" -> facts.combined.opens >= SHORT_SESSION_MIN_OPENS &&
            facts.combined.averageSessionMillis in MINUTE..SHORT_SESSION_MILLIS
        // "Under your limit again": today is under it too.
        "W03" -> facts.underLimitToday == true
        // "only shown when the data is good"
        "W12" -> facts.underLimitToday == true || facts.week.savedMillis != null
        "W11" -> facts.bestWeekMillis != null
        // "Fewer opens": fewer than usual by this time of day.
        "W14" -> facts.usualOpens != null && facts.today.opens < facts.usualOpens
        "M02" -> facts.completeDays in SECOND_WEEK && facts.firstWeekDailyMillis != null
        "M03" -> facts.streak == SEVEN
        "M04" -> facts.completeDays in DAY_30_WEEK
        "M07" -> facts.notOpenedToday.isNotEmpty()
        "M08" -> facts.notOpenedYesterday.isNotEmpty()
        "M10" -> (facts.totalSavedMillis ?: 0L) >= HUNDRED_HOURS
        else -> true
    }

    fun scope(template: InsightTemplate, facts: InsightFacts, format: InsightFormatter): Scope? {
        val periodWord = format.word(periodKey(facts.period))
        val todayWord = format.word(periodKey(UsagePeriod.TODAY))
        val base = when (template.id) {
            in TODAY_TEMPLATES -> Scope(facts.today, todayWord)
            in WEEK_TEMPLATES -> Scope(facts.week, format.word(periodKey(UsagePeriod.WEEK)))
            "W11" -> Scope(UsageFacts(millis = facts.bestWeekMillis ?: return null), null)
            else -> Scope(facts.combined, periodWord)
        }
        val withApp = if ("app" in template.placeholders) withApp(template, facts, base) ?: return null else base
        return when (template.id) {
            "R01", "W06" -> withApp.copy(percent = withApp.usage.percentChange?.let { -it })
            "R02" -> withApp.copy(percent = withApp.usage.percentChange)
            "M04" -> withApp.copy(percent = firstWeekDrop(facts))
            "R03" -> withApp.copy(timeDiffMillis = withApp.usage.savedMillis)
            "R04" -> withApp.copy(timeDiffMillis = withApp.usage.previousMillis?.let { withApp.usage.millis - it }?.takeIf { it > 0 })
            "R16" -> withApp.copy(timeDiffMillis = facts.usualTodayMillis?.let { it - facts.today.millis }?.takeIf { it > 0 })
            // "This week you averaged" / "Your week": the last 7 complete days.
            "L03", "L12" -> withApp.copy(dailyMillis = facts.lastWeekDailyMillis ?: return null)
            "M02" -> withApp.copy(dailyMillis = facts.firstWeekDailyMillis ?: return null)
            "W09" -> withApp.copy(savedEquivMillis = facts.totalSavedMillis ?: return null)
            "M10" -> withApp.copy(savedEquivMillis = HUNDRED_HOURS)
            // "trade one hour of it for {dinners_phrase}": what fits in one hour.
            "E30" -> if (withApp.usage.millis >= HOUR) withApp.copy(dinnersMillis = HOUR) else null
            else -> withApp
        }
    }

    /** Fox lines that only fit some days. */
    fun foxAllowed(id: String, fox: FoxFacts): Boolean = when (id) {
        // "You're under your limit": only with a limit.
        "F05" -> fox.hasLimit
        // "Tracking is off. I'm napping."
        "F20" -> !fox.trackingEnabled
        // "Quiet day" / "Barely any scrolling": only what tracking saw.
        "F21", "F22" -> fox.trackingEnabled
        else -> true
    }

    /** The app a template with {app} talks about; its numbers replace the combined ones. */
    private fun withApp(template: InsightTemplate, facts: InsightFacts, base: Scope): Scope? {
        val label = when (template.id) {
            "M07" -> facts.notOpenedToday.firstOrNull() ?: return null
            "M08" -> facts.notOpenedYesterday.firstOrNull() ?: return null
            else -> null
        }
        if (label != null) return base.copy(appLabel = label)
        val app = when {
            "longest" in template.placeholders -> facts.apps.maxByOrNull { it.usage.longestMillis }
            "saved_time" in template.placeholders -> facts.apps.filter { it.usage.savedMillis != null }
                .maxByOrNull { it.usage.savedMillis ?: 0L }
            else -> facts.apps.firstOrNull()
        } ?: return null
        return base.copy(usage = app.usage, appLabel = app.label)
    }

    private fun firstWeekDrop(facts: InsightFacts): Int? {
        val first = facts.firstWeekDailyMillis?.takeIf { it > 0 } ?: return null
        val last = facts.lastWeekDailyMillis ?: return null
        return ((first - last) * 100 / first).toInt().takeIf { it >= 1 }
    }

    fun periodKey(period: UsagePeriod): String = when (period) {
        UsagePeriod.TODAY -> "period.today"
        UsagePeriod.WEEK -> "period.week"
        UsagePeriod.MONTH -> "period.month"
        UsagePeriod.ALL_TIME -> "period.all"
    }
}
