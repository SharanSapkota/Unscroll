package com.unscroll.app.domain.blocking

/**
 * The typed-phrase friction. The phrase itself is a string resource; this only compares. It must
 * match exactly (case and punctuation included); only leading and trailing spaces are forgiven,
 * because keyboards like to add one.
 */
object UnlockPhrase {
    fun matches(input: String, expected: String): Boolean =
        expected.isNotBlank() && input.trim() == expected.trim()
}
