package com.unscroll.app.data.sample

import com.unscroll.app.data.db.SessionDao
import com.unscroll.app.data.db.SessionEntity
import com.unscroll.app.domain.sample.SampleSessionGenerator
import com.unscroll.app.domain.time.Clock
import java.time.ZoneId
import javax.inject.Inject
import kotlin.random.Random

/** Debug only: fills the database with 30 days of fake sessions so the dashboard has data. */
class SampleDataSeeder @Inject constructor(
    private val dao: SessionDao,
    private val clock: Clock,
) {
    /** Inserts the sample sessions and returns how many were added. */
    suspend fun seed(): Int {
        val now = clock.now()
        val sessions = SampleSessionGenerator
            .generate(now, ZoneId.systemDefault(), random = Random(now))
            // Leave the last few minutes empty so sample data never overlaps a live session.
            .filter { it.endTime < now - RECENT_GAP_MILLIS }
            .map {
                SessionEntity(
                    packageName = it.packageName,
                    startTime = it.startTime,
                    endTime = it.endTime,
                )
            }
        dao.insertAll(sessions)
        return sessions.size
    }

    private companion object {
        const val RECENT_GAP_MILLIS = 5 * 60_000L
    }
}
