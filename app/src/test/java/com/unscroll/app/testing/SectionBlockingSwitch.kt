package com.unscroll.app.testing

/**
 * The section-blocking master switch (SectionBlockingConfig.ENABLED, set in app/build.gradle.kts)
 * for tests. Everything it gates takes it as an argument (`@SectionBlockingEnabled` in classes, a
 * parameter in the pure rules), so each test picks its state whatever the build says: [ON] for the
 * section-blocking tests (detector, rules, reels toggle; they must keep passing for when it is
 * switched back on), [OFF] for the shipped build (no reels toggle, stored reels blocks ignored),
 * and [BOTH] for rules that must hold either way.
 */
object SectionBlockingSwitch {
    const val ON = true
    const val OFF = false
    val BOTH = listOf(ON, OFF)
}
