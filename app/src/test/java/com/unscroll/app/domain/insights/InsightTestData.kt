package com.unscroll.app.domain.insights

import java.io.File
import java.util.Locale
import kotlin.random.Random

/** The shipped bank (assets/insights) and the docs it comes from, read from disk in tests. */
internal object TestBank {

    private fun file(path: String): File =
        listOf(File(path), File("app/$path"), File("../$path")).first { it.exists() }

    val templatesText: String by lazy { file("src/main/assets/insights/templates.tsv").readText() }
    val phrasesText: String by lazy { file("src/main/assets/insights/phrases.tsv").readText() }
    val docsText: String by lazy {
        listOf(File("../docs/insight-templates.md"), File("docs/insight-templates.md")).first { it.exists() }.readText()
    }

    val bank: InsightBank by lazy { InsightBankParser.parse(templatesText, phrasesText) }

    fun engine(bank: InsightBank = this.bank) = InsightEngine(bank, Locale.US)

    fun formatter() = InsightFormatter(bank.phrases, Locale.US)
}

/** Always picks the first option: nextInt(n) is 0. Makes the order of tries the bank's order. */
internal object FirstRandom : Random() {
    override fun nextBits(bitCount: Int): Int = 0
}

/** Facts with nothing special in them; tests override what they need. */
internal fun testFacts(
    period: UsagePeriod = UsagePeriod.TODAY,
    combined: UsageFacts = UsageFacts(
        millis = 2 * HOUR + 10 * MINUTE,
        opens = 20,
        longestMillis = 30 * MINUTE,
        averageSessionMillis = 6 * MINUTE,
        swipes = SwipeCount(3_000, estimated = false),
    ),
    apps: List<AppFacts> = listOf(AppFacts("com.zhiliaoapp.musically", "TikTok", combined)),
    lateMillis: Long = 0,
    today: UsageFacts = combined,
    week: UsageFacts = combined,
    completeDays: Int = 20,
    projection: LifeProjection? = null,
    allTimeMillis: Long = 40 * HOUR,
    totalSavedMillis: Long? = null,
    streak: Int? = null,
    underLimitToday: Boolean? = null,
    underLimitYesterday: Boolean? = null,
    swipeLengthMeters: Double = 0.08,
) = InsightFacts(
    period = period,
    combined = combined,
    apps = apps,
    lateMillis = lateMillis,
    today = today,
    week = week,
    usualTodayMillis = null,
    usualOpens = null,
    bestDay = null,
    completeDays = completeDays,
    daysOfUse = completeDays + 1,
    projection = projection,
    lastWeekDailyMillis = null,
    firstWeekDailyMillis = null,
    allTimeMillis = allTimeMillis,
    totalSavedMillis = totalSavedMillis,
    streak = streak,
    underLimitToday = underLimitToday,
    underLimitYesterday = underLimitYesterday,
    bestWeekMillis = null,
    notOpenedToday = emptyList(),
    notOpenedYesterday = emptyList(),
    swipeLengthMeters = swipeLengthMeters,
    singleAppLabel = null,
)

/** A small bank for rotation tests: the real phrases, the given templates. */
internal fun bankOf(vararg lines: String): InsightBank =
    InsightBank(InsightBankParser.parseTemplates(lines.joinToString("\n")), TestBank.bank.phrases)
