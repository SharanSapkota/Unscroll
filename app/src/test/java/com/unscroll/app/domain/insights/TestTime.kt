package com.unscroll.app.domain.insights

import com.unscroll.app.domain.session.Session
import java.time.LocalDateTime
import java.time.ZoneId

/** Helpers for writing times in tests as local wall-clock times. */
internal val BERLIN: ZoneId = ZoneId.of("Europe/Berlin")

internal const val MINUTE = 60_000L
internal const val HOUR = 60 * MINUTE

internal fun at(text: String, zone: ZoneId = BERLIN): Long =
    LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

internal fun session(
    start: String,
    end: String?,
    packageName: String = "com.instagram.android",
    id: Long = 0,
) = Session(id, packageName, at(start), end?.let { at(it) }, scrollCount = 0)
