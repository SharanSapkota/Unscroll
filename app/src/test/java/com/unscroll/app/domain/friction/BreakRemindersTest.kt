package com.unscroll.app.domain.friction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BreakRemindersTest {

    private val start = 10_000_000L
    private val interval = 15 * 60_000L

    @Test
    fun firstReminderAfterOneInterval() {
        assertNull(BreakReminders.dueReminder(start, interval, start + interval - 1, lastShown = 0))
        assertEquals(1, BreakReminders.dueReminder(start, interval, start + interval, lastShown = 0))
    }

    @Test
    fun keepGoing_repeatsAtTheNextInterval_notBefore() {
        // Simulate a fake clock ticking every 10 s through a 50-minute session.
        var lastShown = 0
        val shownAt = mutableListOf<Long>()
        var now = start
        while (now <= start + 50 * 60_000L) {
            BreakReminders.dueReminder(start, interval, now, lastShown)?.let {
                lastShown = it // "Keep going"
                shownAt += now - start
            }
            now += 10_000
        }
        assertEquals(listOf(15 * 60_000L, 30 * 60_000L, 45 * 60_000L), shownAt)
    }

    @Test
    fun nextReminderAt_followsTheLastShown() {
        assertEquals(start + interval, BreakReminders.nextReminderAt(start, interval, lastShown = 0))
        assertEquals(start + 3 * interval, BreakReminders.nextReminderAt(start, interval, lastShown = 2))
    }

    @Test
    fun wakingUpLate_showsOnlyTheLatestReminder() {
        assertEquals(3, BreakReminders.dueReminder(start, interval, start + 50 * 60_000L, lastShown = 0))
    }
}
