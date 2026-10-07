package com.unscroll.app.domain.blocking

/**
 * Stores a pending [LimitSettings] as a small flat JSON object in the `pendingChangeJson` column.
 * Hand-rolled to avoid adding a serialization library for a handful of fields. Swipe-limit fields
 * were added later; JSON written before them decodes with their defaults.
 */
object LimitSettingsCodec {

    fun encode(settings: LimitSettings): String = buildString {
        append('{')
        append("\"dailyLimitMinutes\":").append(settings.dailyLimitMinutes?.toString() ?: "null")
        append(",\"blockedAlways\":").append(settings.blockedAlways)
        append(",\"scheduleEnabled\":").append(settings.schedule.enabled)
        append(",\"scheduleDays\":").append(settings.schedule.daysBitmask)
        append(",\"scheduleStartMinute\":").append(settings.schedule.startMinute)
        append(",\"scheduleEndMinute\":").append(settings.schedule.endMinute)
        append(",\"swipeLimit\":").append(settings.swipeLimit?.toString() ?: "null")
        append(",\"swipeLimitPerSession\":").append(settings.swipeLimitScope == SwipeLimitScope.SESSION)
        append(",\"swipeSessionGapMinutes\":").append(settings.swipeSessionGapMinutes)
        append(",\"swipeAccessAllowed\":").append(settings.swipeAccessAllowed)
        append('}')
    }

    /** Returns null for anything it can't read, so a corrupt value never crashes the app. */
    fun decode(json: String?): LimitSettings? {
        if (json.isNullOrBlank()) return null
        val values = FIELD.findAll(json).associate { it.groupValues[1] to it.groupValues[2] }
        return try {
            LimitSettings(
                dailyLimitMinutes = values["dailyLimitMinutes"]?.takeIf { it != "null" }?.toInt(),
                blockedAlways = values.getValue("blockedAlways").toBooleanStrict(),
                schedule = BlockSchedule(
                    enabled = values.getValue("scheduleEnabled").toBooleanStrict(),
                    days = BlockSchedule.daysFromBitmask(values.getValue("scheduleDays").toInt()),
                    startMinute = values.getValue("scheduleStartMinute").toInt(),
                    endMinute = values.getValue("scheduleEndMinute").toInt(),
                ),
                swipeLimit = values["swipeLimit"]?.takeIf { it != "null" }?.toInt(),
                swipeLimitScope = if (values["swipeLimitPerSession"]?.toBooleanStrict() == true) {
                    SwipeLimitScope.SESSION
                } else {
                    SwipeLimitScope.DAY
                },
                swipeSessionGapMinutes = values["swipeSessionGapMinutes"]?.toInt()
                    ?: SwipeLimitRules.DEFAULT_SESSION_GAP_MINUTES,
                swipeAccessAllowed = values["swipeAccessAllowed"]?.toBooleanStrict() ?: false,
            )
        } catch (e: NoSuchElementException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private val FIELD = Regex("\"(\\w+)\"\\s*:\\s*(null|true|false|-?\\d+)")
}
