package com.unscroll.app.testing

/**
 * The payments master switch (PlusConfig.BILLING_ENABLED, set in app/build.gradle.kts) for tests.
 * The classes it gates take it as a constructor argument (`@BillingEnabled`), so each test picks
 * its state whatever the build says: [ON] for the billing tests (they must keep passing for when
 * payments are switched back on), [OFF] for the free-for-everyone build, and [BOTH] for rules that
 * must hold either way.
 */
object BillingSwitch {
    const val ON = true
    const val OFF = false
    val BOTH = listOf(ON, OFF)
}
