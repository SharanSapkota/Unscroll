package com.unscroll.app.domain.friction

import java.time.Instant
import java.time.ZoneId

/**
 * Global quiet hours: nudges, break reminders and limit warnings stay silent. The pause screen and
 * blocking are not affected. A range may cross midnight (22:00 to 07:00). Start equal to end
 * means all day.
 */
data class QuietHours(
    val enabled: Boolean = false,
    val startMinute: Int = 22 * 60,
    val endMinute: Int = 7 * 60,
) {
    fun isQuiet(now: Long, zone: ZoneId): Boolean {
        if (!enabled) return false
        val time = Instant.ofEpochMilli(now).atZone(zone)
        val minute = time.hour * 60 + time.minute
        return when {
            startMinute == endMinute -> true
            startMinute < endMinute -> minute in startMinute until endMinute
            else -> minute >= startMinute || minute < endMinute
        }
    }
}
