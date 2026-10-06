package com.unscroll.app.domain.insights

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** A half-open time range [from, to) in epoch milliseconds. */
data class TimeRange(val from: Long, val to: Long)

/** The periods the dashboard can show. Day boundaries are local midnights. */
enum class UsagePeriod {
    TODAY,

    /** Today and the 6 days before it. */
    WEEK,

    /** Today and the 29 days before it. */
    MONTH,
    ALL_TIME,
    ;

    /** From the start of the period up to the end of today. */
    fun range(now: Long, zone: ZoneId): TimeRange {
        val today = localDate(now, zone)
        val from = when (this) {
            TODAY -> startOfDay(today, zone)
            WEEK -> startOfDay(today.minusDays(6), zone)
            MONTH -> startOfDay(today.minusDays(29), zone)
            ALL_TIME -> 0L
        }
        return TimeRange(from, startOfDay(today.plusDays(1), zone))
    }
}

fun localDate(epochMillis: Long, zone: ZoneId): LocalDate =
    Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()

fun startOfDay(date: LocalDate, zone: ZoneId): Long =
    date.atStartOfDay(zone).toInstant().toEpochMilli()
