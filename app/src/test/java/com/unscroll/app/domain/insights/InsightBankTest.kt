package com.unscroll.app.domain.insights

import com.unscroll.app.domain.fox.FoxMood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The bundled bank (assets/insights) against docs/insight-templates.md and the engine. */
class InsightBankTest {

    private val bank = TestBank.bank

    /** Placeholder names in the docs' "Placeholders" table. */
    private val docsTable: Set<String> by lazy {
        TestBank.docsText.lineSequence()
            .dropWhile { !it.startsWith("## Placeholders") }
            .drop(1)
            .takeWhile { !it.startsWith("## ") }
            .mapNotNull { Regex("""^\| \{([a-z_]+)\} \|""").find(it)?.groupValues?.get(1) }
            .toSet()
    }

    /** Every template line in the docs: id, tag, text. */
    private val docsTemplates: List<Triple<String, Char?, String>> by lazy {
        TestBank.docsText.lineSequence()
            .mapNotNull { Regex("""^([A-Z]\d\d) (?:\[([GHB])\] )?(.+)$""").find(it) }
            .map { Triple(it.groupValues[1], it.groupValues[2].firstOrNull(), it.groupValues[3]) }
            .toList()
    }

    @Test
    fun bank_hasAll134Templates_withUniqueIds() {
        assertEquals(134, bank.templates.size)
        assertEquals(134, bank.templates.map { it.id }.toSet().size)
    }

    @Test
    fun bank_matchesTheDocsExactly() {
        assertEquals(docsTemplates.size, bank.templates.size)
        for ((id, tag, text) in docsTemplates) {
            val template = bank.template(id)
            assertNotNull("$id missing from the bank", template)
            assertEquals("$id text", text, template!!.text)
            assertEquals("$id tone", tag?.let { InsightTone.fromTag(it) }, template.tone)
        }
    }

    @Test
    fun categories_followTheDocsSections() {
        val counts = bank.templates.groupingBy { it.category }.eachCount()
        assertEquals(24, counts[InsightCategory.THUMB])
        assertEquals(32, counts[InsightCategory.COULD_HAVE])
        assertEquals(16, counts[InsightCategory.LIFE])
        assertEquals(16, counts[InsightCategory.TRENDS])
        assertEquals(22, counts[InsightCategory.FOX])
        assertEquals(14, counts[InsightCategory.WINS])
        assertEquals(10, counts[InsightCategory.MILESTONES])
    }

    @Test
    fun foxLines_haveAMoodAndNoTone_othersATone() {
        for (template in bank.templates) {
            if (template.category == InsightCategory.FOX) {
                assertNotNull(template.id, template.mood)
                assertNull(template.id, template.tone)
            } else {
                assertNull(template.id, template.mood)
                assertNotNull(template.id, template.tone)
            }
        }
        assertEquals(FoxMood.HAPPY, bank.template("F01")!!.mood)
        assertEquals(FoxMood.ALERT, bank.template("F09")!!.mood)
        assertEquals(FoxMood.CONCERNED, bank.template("F18")!!.mood)
        assertEquals(FoxMood.SLEEPY, bank.template("F22")!!.mood)
    }

    @Test
    fun winsAreAllGentle() {
        assertTrue(bank.templates.filter { it.category == InsightCategory.WINS }.all { it.tone == InsightTone.GENTLE })
    }

    @Test
    fun everyPlaceholderInTheBank_isSupportedByTheEngine() {
        val used = bank.templates.flatMap { it.placeholders }.toSet()
        val unsupported = used - InsightEngine.SUPPORTED_PLACEHOLDERS
        assertTrue("Not supported: $unsupported", unsupported.isEmpty())
    }

    @Test
    fun everySupportedPlaceholder_isInTheDocsTable_andTheOtherWayRound() {
        assertEquals(40, docsTable.size)
        assertEquals(docsTable, InsightEngine.SUPPORTED_PLACEHOLDERS)
    }

    @Test
    fun everyLandmark_hasWords() {
        for (landmark in Equivalents.LANDMARKS) {
            assertNotNull(landmark.key, bank.phrases["landmark.${landmark.key}"])
        }
    }

    @Test
    fun phrases_haveEveryKeyTheFormatterUses() {
        val keys = listOf(
            "period.today", "period.week", "period.month", "period.year", "period.all", "estimate",
            "distance.m", "distance.km", "time.hours_minutes", "time.hours", "time.minutes", "time.remaining_minutes",
            "hours", "years", "apps.generic", "movies", "movies.single", "pages", "books", "books.fraction",
            "walk", "walk.vowel", "flights", "dinners", "dinners.single", "calls",
        ) + (2..10).map { "number.$it" }
        for (key in keys) assertNotNull(key, bank.phrases[key])
    }

    @Test
    fun parser_skipsCommentsAndMalformedLines() {
        val parsed = InsightBankParser.parseTemplates(
            "# comment\n\nX01\tTHUMB\tH\t-\tok {time}\nbroken line\nX02\tNOPE\tH\t-\tbad category\r\n",
        )
        assertEquals(listOf("X01"), parsed.map { it.id })
        assertEquals(listOf("time"), parsed.single().placeholders)
    }
}
