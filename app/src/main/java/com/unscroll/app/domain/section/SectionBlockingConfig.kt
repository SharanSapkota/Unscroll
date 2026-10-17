package com.unscroll.app.domain.section

/**
 * Section blocking ("Block reels only" / "Block For You feed" / "Block Shorts only") on or off.
 * Separate from the Reels-only timer (ReelsOnlyPillConfig), so either can be switched alone.
 *
 * While off: no reels toggle in App detail (only "Block entire app"), no "Section blocking" under
 * Advanced options, no Settings row or Home banner, no "Reels blocked" status line, and the
 * section-blocking service listens to no app for blocking and never covers. Everything else of it
 * (code, rules, tests, the debug Section Inspector) stays, and stored reels blocks are kept.
 */
object SectionBlockingConfig {
    const val ENABLED = false
}
