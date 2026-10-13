package com.unscroll.app.domain.insights

import com.unscroll.app.domain.fox.FoxMood

/** The sections of docs/insight-templates.md. */
enum class InsightCategory {
    /** 1. Thumb distance. */
    THUMB,

    /** 2. What you could have done. */
    COULD_HAVE,

    /** 3. Life projection: the weekly card only. */
    LIFE,

    /** 4. Trends and patterns. */
    TRENDS,

    /** 5. Fox voice: the fox's speech bubbles, by mood. */
    FOX,

    /** 6. Wins: only when the data is good. */
    WINS,

    /** 7. Milestones. */
    MILESTONES,
    ;

    /** Categories 1 to 4 can carry bad news; with an [InsightTone.HONEST] or [InsightTone.BRUTAL] tag they are "negative". */
    val canBeNegative: Boolean get() = this == THUMB || this == COULD_HAVE || this == LIFE || this == TRENDS
}

/** Tone tags [G], [H], [B], and the user's tone setting (default Honest). */
enum class InsightTone(val tag: Char) {
    GENTLE('G'),
    HONEST('H'),
    BRUTAL('B'),
    ;

    /** Whether a message with this tag may be shown with the user's [setting]. */
    fun allowedWith(setting: InsightTone): Boolean = when (this) {
        GENTLE -> true
        HONEST -> setting != GENTLE
        BRUTAL -> setting == BRUTAL
    }

    companion object {
        val DEFAULT = HONEST

        fun fromTag(tag: Char): InsightTone? = entries.firstOrNull { it.tag == tag }
    }
}

/** One message from the bank. [tone] is null for fox lines, [mood] is set only for them. */
data class InsightTemplate(
    val id: String,
    val category: InsightCategory,
    val tone: InsightTone?,
    val mood: FoxMood?,
    val text: String,
) {
    /** The placeholder names in [text], without braces, in order of appearance. */
    val placeholders: List<String> by lazy { PLACEHOLDER.findAll(text).map { it.groupValues[1] }.toList() }

    /** Categories 1 to 4 with [H] or [B]: not shown on a day under the limit. */
    val isNegative: Boolean
        get() = category.canBeNegative && (tone == InsightTone.HONEST || tone == InsightTone.BRUTAL)

    companion object {
        val PLACEHOLDER = Regex("""\{([a-z_]+)\}""")
    }
}

/** A word with its form for exactly one and for any other count; `{n}` is the formatted number. */
data class Phrase(val one: String, val other: String)

/** All templates and the words that fill them. Loaded from assets/insights (InsightBankRepository). */
data class InsightBank(
    val templates: List<InsightTemplate>,
    val phrases: Map<String, Phrase>,
) {
    fun template(id: String): InsightTemplate? = templates.firstOrNull { it.id == id }

    companion object {
        val EMPTY = InsightBank(emptyList(), emptyMap())
    }
}

/**
 * Reads the bank's two tab-separated files (assets/insights/templates.tsv and phrases.tsv). Lines
 * starting with '#' and blank lines are skipped; malformed lines are skipped too, so a bad
 * translation can never crash the app (InsightBankTest checks the shipped files are complete).
 */
object InsightBankParser {

    fun parse(templates: String, phrases: String): InsightBank =
        InsightBank(parseTemplates(templates), parsePhrases(phrases))

    fun parseTemplates(text: String): List<InsightTemplate> = lines(text).mapNotNull { columns ->
        if (columns.size != 5) return@mapNotNull null
        val (id, category, tone, mood, body) = columns
        InsightTemplate(
            id = id,
            category = InsightCategory.entries.firstOrNull { it.name == category } ?: return@mapNotNull null,
            tone = tone.singleOrNull()?.let { InsightTone.fromTag(it) },
            mood = FoxMood.entries.firstOrNull { it.name == mood },
            text = body,
        )
    }

    fun parsePhrases(text: String): Map<String, Phrase> = lines(text)
        .filter { it.size == 3 }
        .associate { (key, one, other) -> key to Phrase(one, other) }

    private fun lines(text: String): List<List<String>> = text.lineSequence()
        .map { it.trimEnd('\r') }
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .map { it.split('\t') }
        .toList()
}
