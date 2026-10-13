package com.unscroll.app.domain.insights

import com.unscroll.app.domain.fox.FoxMood
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InsightEngineTest {

    private val facts = testFacts()

    @Test
    fun skipsTemplatesWithMissingData_andPicksAnother() {
        // FirstRandom tries them in order: the first needs a streak, which there is none of.
        val engine = TestBank.engine(
            bankOf(
                "R14\tTRENDS\tH\t-\t{streak} days in a row under your limit.",
                "E13\tCOULD_HAVE\tH\t-\t{time} = {movies_phrase}, with popcorn.",
            ),
        )
        val insight = engine.next(facts, InsightHistory(), InsightTone.HONEST, FirstRandom)!!
        assertEquals("E13", insight.templateId)
        assertEquals("2h 10m = 1 full movie, with popcorn.", insight.text)
        assertEquals("2h 10m", insight.highlight)
    }

    @Test
    fun nothingFills_null() {
        val engine = TestBank.engine(bankOf("R14\tTRENDS\tH\t-\t{streak} days in a row under your limit."))
        assertNull(engine.next(facts, InsightHistory(), InsightTone.HONEST, FirstRandom))
    }

    @Test
    fun neverRepeatsWithinTheLast30Uses() {
        val engine = TestBank.engine()
        var history = InsightHistory()
        val seen = mutableListOf<String>()
        val random = Random(42)
        repeat(60) {
            val insight = engine.next(facts, history, InsightTone.BRUTAL, random) ?: return@repeat
            assertFalse("${insight.templateId} repeated within 30", insight.templateId in seen.takeLast(30))
            seen += insight.templateId
            history = history.record(insight, now = 0)
        }
        assertTrue(seen.size >= 30)
        assertEquals(30, history.recentIds.size)
    }

    @Test
    fun neverTheSameCategoryTwiceInARow() {
        val engine = TestBank.engine()
        var history = InsightHistory()
        var last: InsightCategory? = null
        val random = Random(3)
        repeat(40) {
            val insight = engine.next(facts, history, InsightTone.HONEST, random) ?: return@repeat
            assertNotEquals(last, insight.category)
            last = insight.category
            history = history.record(insight, now = 0)
        }
    }

    @Test
    fun sameCategory_skipped_evenIfItWouldFill() {
        val engine = TestBank.engine(
            bankOf(
                "E13\tCOULD_HAVE\tH\t-\t{time} = {movies_phrase}, with popcorn.",
                "T15\tTHUMB\tG\t-\tThumb odometer: {distance} {period}.",
            ),
        )
        val history = InsightHistory(lastCategory = InsightCategory.COULD_HAVE)
        assertEquals("T15", engine.next(facts, history, InsightTone.HONEST, FirstRandom)!!.templateId)
    }

    @Test
    fun toneFilter_gentleAlways_honestForHonestAndBrutal_brutalOnlyForBrutal() {
        val engine = TestBank.engine()
        fun tones(setting: InsightTone): Set<InsightTone?> {
            var history = InsightHistory()
            val tones = mutableSetOf<InsightTone?>()
            val random = Random(11)
            repeat(80) {
                val insight = engine.next(facts, history, setting, random) ?: return@repeat
                tones += insight.tone
                history = history.record(insight, 0).copy(recentIds = emptyList())
            }
            return tones
        }
        assertEquals(setOf(InsightTone.GENTLE), tones(InsightTone.GENTLE))
        assertEquals(setOf(InsightTone.GENTLE, InsightTone.HONEST), tones(InsightTone.HONEST))
        assertEquals(setOf(InsightTone.GENTLE, InsightTone.HONEST, InsightTone.BRUTAL), tones(InsightTone.BRUTAL))
    }

    @Test
    fun noBrutal_inTheFirst3Days_orAfterAGoodDay() {
        val engine = TestBank.engine(bankOf("T06\tTHUMB\tB\t-\tYour thumb ran {distance} {period}. Your legs did nothing."))
        val withSwipes = facts.copy(combined = facts.combined.copy(swipes = SwipeCount(500, estimated = false)))
        assertNotNull(engine.next(withSwipes, InsightHistory(), InsightTone.BRUTAL, FirstRandom))
        assertNull(engine.next(withSwipes.copy(daysOfUse = 3), InsightHistory(), InsightTone.BRUTAL, FirstRandom))
        assertNotNull(engine.next(withSwipes.copy(daysOfUse = 4), InsightHistory(), InsightTone.BRUTAL, FirstRandom))
        assertNull(engine.next(withSwipes.copy(underLimitYesterday = true), InsightHistory(), InsightTone.BRUTAL, FirstRandom))
    }

    @Test
    fun underTheLimit_noNegativeMessages_winsFirst() {
        val engine = TestBank.engine()
        val good = testFacts(
            combined = facts.combined.copy(previousMillis = 3 * HOUR),
            underLimitToday = true,
            streak = 4,
        )
        var history = InsightHistory()
        val random = Random(5)
        val shown = mutableListOf<Insight>()
        repeat(30) {
            val insight = engine.next(good, history, InsightTone.BRUTAL, random) ?: return@repeat
            assertFalse("${insight.templateId} is negative", insight.isNegativeMessage())
            shown += insight
            history = history.record(insight, 0)
        }
        // Wins come first, then every other one while there are wins left (never one category twice in a row).
        assertEquals(InsightCategory.WINS, shown.first().category)
        assertEquals(listOf("W01", "W02", "W03", "W04", "W05", "W06", "W08", "W12", "W13"), shown.filter { it.category == InsightCategory.WINS }.map { it.templateId }.sorted())
    }

    @Test
    fun lifeProjection_onlyWeekly_atMostOncePerWeek() {
        val engine = TestBank.engine()
        val withProjection = testFacts(projection = LifeProjection.from(List(30) { 2 * HOUR }, age = null))
        // Never in the regular rotation.
        var history = InsightHistory()
        repeat(40) {
            val insight = engine.next(withProjection, history, InsightTone.BRUTAL, Random(it)) ?: return@repeat
            assertNotEquals(InsightCategory.LIFE, insight.category)
            history = history.record(insight, 0)
        }
        val now = 100 * InsightEngine.WEEK_MILLIS
        val weekly = engine.weekly(withProjection, InsightHistory(), InsightTone.HONEST, now, Random(1))!!
        assertEquals(InsightCategory.LIFE, weekly.category)
        val after = InsightHistory().record(weekly, now)
        assertEquals(now, after.lastLifeAt)
        assertNull(engine.weekly(withProjection, after, InsightTone.HONEST, now + InsightEngine.WEEK_MILLIS - 1, Random(1)))
        assertNotNull(engine.weekly(withProjection, after, InsightTone.HONEST, now + InsightEngine.WEEK_MILLIS, Random(1)))
    }

    @Test
    fun lessThanLastWeek_onlyWhenItIsLess() {
        val engine = TestBank.engine(
            bankOf(
                "R01\tTRENDS\tG\t-\t{pct}% less than last week. Your thumb is thanking you.",
                "R02\tTRENDS\tH\t-\t{pct}% more than last week. Worth a look?",
            ),
        )
        val less = facts.copy(week = UsageFacts(millis = 8 * HOUR, previousMillis = 10 * HOUR))
        assertEquals("20% less than last week. Your thumb is thanking you.", engine.next(less, InsightHistory(), InsightTone.HONEST, FirstRandom)!!.text)
        val more = facts.copy(week = UsageFacts(millis = 12 * HOUR, previousMillis = 10 * HOUR))
        assertEquals("20% more than last week. Worth a look?", engine.next(more, InsightHistory(), InsightTone.HONEST, FirstRandom)!!.text)
        val untracked = facts.copy(week = UsageFacts(millis = 8 * HOUR, previousMillis = null))
        assertNull(engine.next(untracked, InsightHistory(), InsightTone.HONEST, FirstRandom))
    }

    @Test
    fun appTemplates_useThatAppsNumbers() {
        val engine = TestBank.engine(bankOf("R09\tTRENDS\tH\t-\tLongest session {period}: {longest} in {app}."))
        val facts = testFacts(
            apps = listOf(
                AppFacts("a", "Instagram", UsageFacts(millis = 2 * HOUR, longestMillis = 20 * MINUTE)),
                AppFacts("b", "TikTok", UsageFacts(millis = HOUR, longestMillis = 45 * MINUTE)),
            ),
        )
        assertEquals("Longest session today: 45m in TikTok.", engine.next(facts, InsightHistory(), InsightTone.HONEST, FirstRandom)!!.text)
    }

    @Test
    fun pluralNouns_needTwoOrMore() {
        val engine = TestBank.engine(bankOf("R08\tTRENDS\tB\t-\t{opens} opens. {opens} times you picked up the phone for \"just a sec\"."))
        assertNull(engine.next(facts.copy(combined = facts.combined.copy(opens = 1)), InsightHistory(), InsightTone.BRUTAL, FirstRandom))
        assertEquals(
            "20 opens. 20 times you picked up the phone for \"just a sec\".",
            engine.next(facts, InsightHistory(), InsightTone.BRUTAL, FirstRandom)!!.text,
        )
    }

    @Test
    fun excluded_isNeverPicked() {
        val engine = TestBank.engine(bankOf("E13\tCOULD_HAVE\tH\t-\t{time} = {movies_phrase}, with popcorn."))
        assertNull(engine.next(facts, InsightHistory(), InsightTone.HONEST, FirstRandom, exclude = "E13"))
    }

    @Test
    fun foxLines_byMood_filledAndNeverTwiceInARow() {
        val engine = TestBank.engine()
        val fox = FoxFacts(trackingEnabled = true, hasLimit = true, remainingMillis = 12 * MINUTE, topAppLabel = "TikTok")
        var last: String? = null
        repeat(20) {
            val line = engine.foxLine(FoxMood.ALERT, fox, last, Random(it))!!
            assertTrue(line.templateId in setOf("F07", "F08", "F09", "F10", "F11", "F12"))
            assertNotEquals(last, line.templateId)
            assertFalse(line.text.contains("{"))
            last = line.templateId
        }
        val withoutRemaining = fox.copy(remainingMillis = null)
        repeat(20) {
            val line = engine.foxLine(FoxMood.ALERT, withoutRemaining, null, Random(it))!!
            assertFalse(line.templateId == "F09" || line.templateId == "F12")
        }
    }

    @Test
    fun foxLines_trackingOff_onlyTheNappingOnesThatAreTrue() {
        val engine = TestBank.engine()
        repeat(20) {
            val line = engine.foxLine(FoxMood.SLEEPY, FoxFacts(trackingEnabled = false), null, Random(it))!!
            assertTrue(line.templateId in setOf("F19", "F20"))
        }
        repeat(20) {
            val line = engine.foxLine(FoxMood.SLEEPY, FoxFacts(trackingEnabled = true), null, Random(it))!!
            assertTrue(line.templateId in setOf("F19", "F21", "F22"))
        }
    }

    @Test
    fun everyTemplate_fillsWithoutLeftoverBraces_whenTheDataIsThere() {
        val engine = TestBank.engine()
        val rich = testFacts(
            combined = UsageFacts(
                millis = 30 * HOUR, opens = 40, longestMillis = HOUR, averageSessionMillis = 4 * MINUTE,
                swipes = SwipeCount(9_000, estimated = false), previousMillis = 40 * HOUR,
                previousSwipes = SwipeCount(12_000, estimated = false), days = 7.0,
            ),
            period = UsagePeriod.WEEK,
        ).let {
            it.copy(
                apps = listOf(AppFacts("a", "TikTok", it.combined), AppFacts("b", "Instagram", it.combined.copy(millis = 2 * HOUR))),
                today = it.combined.copy(millis = 2 * HOUR, previousMillis = 3 * HOUR, opens = 10),
                week = it.combined,
                lateMillis = 5 * HOUR,
                projection = LifeProjection.from(List(30) { 3 * HOUR }, age = 30),
                lastWeekDailyMillis = 3 * HOUR,
                firstWeekDailyMillis = 4 * HOUR,
                usualTodayMillis = 3 * HOUR,
                usualOpens = 30,
                bestDay = java.time.DayOfWeek.TUESDAY,
                totalSavedMillis = 120 * HOUR,
                streak = 7,
                underLimitToday = true,
                bestWeekMillis = 20 * HOUR,
                notOpenedToday = listOf("Facebook"),
                notOpenedYesterday = listOf("Facebook"),
                allTimeMillis = 200 * HOUR,
                completeDays = 33,
                daysOfUse = 34,
                singleAppLabel = null,
            )
        }
        for (template in TestBank.bank.templates.filter { it.category != InsightCategory.FOX }) {
            val insight = engine.render(template, rich.copy(period = if (template.id == "E29") UsagePeriod.TODAY else UsagePeriod.WEEK), Random(1))
            if (template.id in NEEDS_OTHER_DATA) continue
            assertNotNull("${template.id} didn't fill", insight)
            assertFalse("${template.id}: ${insight!!.text}", insight.text.contains('{'))
            assertTrue("${template.id} starts lower case: ${insight.text}", !insight.text.first().isLowerCase())
        }
    }

    private fun Insight.isNegativeMessage() = category.canBeNegative && tone != InsightTone.GENTLE

    private companion object {
        /** Data that contradicts the rich facts on purpose (more than last week, more than yesterday). */
        val NEEDS_OTHER_DATA = setOf("R02", "R04", "M02")
    }
}
