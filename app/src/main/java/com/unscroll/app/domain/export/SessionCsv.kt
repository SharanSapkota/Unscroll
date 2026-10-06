package com.unscroll.app.domain.export

import com.unscroll.app.domain.session.Session
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Sessions as CSV (RFC 4180): one row per session, times in the device's time zone as ISO 8601
 * with offset. Open sessions have an empty end and run until [now] for the duration.
 */
object SessionCsv {
    const val MIME_TYPE = "text/csv"

    val HEADER = listOf("id", "app", "package", "start", "end", "duration_seconds", "swipes")

    fun write(
        sessions: List<Session>,
        appName: (String) -> String,
        zone: ZoneId,
        now: Long,
        out: Appendable,
    ) {
        out.appendRow(HEADER)
        for (session in sessions) {
            out.appendRow(
                listOf(
                    session.id.toString(),
                    appName(session.packageName),
                    session.packageName,
                    format(session.startTime, zone),
                    session.endTime?.let { format(it, zone) }.orEmpty(),
                    (((session.endTime ?: now) - session.startTime).coerceAtLeast(0) / 1_000).toString(),
                    session.scrollCount.toString(),
                ),
            )
        }
    }

    /** Quotes a field when it holds a comma, quote or line break, doubling inner quotes. */
    fun escape(field: String): String =
        if (field.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + field.replace("\"", "\"\"") + "\""
        } else {
            field
        }

    private fun Appendable.appendRow(fields: List<String>) {
        append(fields.joinToString(",") { escape(it) })
        append("\r\n")
    }

    private fun format(epochMillis: Long, zone: ZoneId): String =
        DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(Instant.ofEpochMilli(epochMillis).atZone(zone).withNano(0))

    /** "unscroll-sessions-2026-10-06.csv" */
    fun fileName(today: LocalDate): String = "unscroll-sessions-$today.csv"
}
