package com.unscroll.app.domain.time

/** Wall-clock time in epoch milliseconds. Injected so session logic can be tested with fake time. */
fun interface Clock {
    fun now(): Long
}

object SystemClock : Clock {
    override fun now(): Long = System.currentTimeMillis()
}
