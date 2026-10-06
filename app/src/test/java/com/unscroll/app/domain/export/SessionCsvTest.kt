package com.unscroll.app.domain.export

import com.unscroll.app.domain.session.Session
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionCsvTest {

    private val zone = ZoneId.of("Europe/Berlin")
    private fun at(hour: Int, minute: Int, second: Int = 0) =
        LocalDateTime.of(2026, 10, 6, hour, minute, second).atZone(zone).toInstant().toEpochMilli()

    private fun csv(sessions: List<Session>, now: Long = at(23, 0), names: Map<String, String> = emptyMap()) =
        buildString { SessionCsv.write(sessions, { names[it] ?: it }, zone, now, this) }

    @Test
    fun headerOnly_whenThereAreNoSessions() {
        assertEquals("id,app,package,start,end,duration_seconds,swipes\r\n", csv(emptyList()))
    }

    @Test
    fun closedAndOpenSessions() {
        val text = csv(
            listOf(
                Session(1, "com.instagram.android", at(9, 0), at(9, 12, 30), scrollCount = 42),
                Session(2, "com.facebook.katana", at(22, 50), null, scrollCount = 0),
            ),
            now = at(23, 0),
            names = mapOf("com.instagram.android" to "Instagram", "com.facebook.katana" to "Facebook"),
        )
        assertEquals(
            listOf(
                "id,app,package,start,end,duration_seconds,swipes",
                "1,Instagram,com.instagram.android,2026-10-06T09:00:00+02:00,2026-10-06T09:12:30+02:00,750,42",
                "2,Facebook,com.facebook.katana,2026-10-06T22:50:00+02:00,,600,0",
            ),
            text.split("\r\n").dropLast(1),
        )
    }

    @Test
    fun fieldsWithCommasQuotesOrLineBreaks_areQuoted() {
        assertEquals("plain", SessionCsv.escape("plain"))
        assertEquals("\"a,b\"", SessionCsv.escape("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", SessionCsv.escape("say \"hi\""))
        assertEquals("\"two\nlines\"", SessionCsv.escape("two\nlines"))
    }

    @Test
    fun appNamesAreEscaped() {
        val text = csv(listOf(Session(1, "x", at(9, 0), at(9, 1), 0)), names = mapOf("x" to "Tik, Tok"))
        assertEquals("1,\"Tik, Tok\",x,2026-10-06T09:00:00+02:00,2026-10-06T09:01:00+02:00,60,0", text.split("\r\n")[1])
    }

    @Test
    fun fileNameHasTheDate() {
        assertEquals("unscroll-sessions-2026-10-06.csv", SessionCsv.fileName(LocalDate.of(2026, 10, 6)))
    }
}
