package com.unscroll.app.data.usage

import com.unscroll.app.data.db.SessionDao
import com.unscroll.app.data.db.toSession
import com.unscroll.app.domain.insights.AppSessionStats
import com.unscroll.app.domain.insights.TimeRange
import com.unscroll.app.domain.insights.UsageDataSource
import com.unscroll.app.domain.session.Session
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@Singleton
class UsageRepository @Inject constructor(
    private val dao: SessionDao,
) : UsageDataSource {

    override suspend fun appTotals(range: TimeRange, now: Long): Map<String, Long> =
        dao.appTotals(range.from, range.to, now).associate { it.packageName to it.totalMillis }

    override suspend fun appSessionStats(range: TimeRange, now: Long): List<AppSessionStats> =
        dao.appSessionStats(range.from, range.to, now).map {
            AppSessionStats(it.packageName, it.opens, it.totalDurationMillis, it.longestMillis)
        }

    override suspend fun sessionsOverlapping(range: TimeRange): List<Session> =
        dao.sessionsOverlapping(range.from, range.to).map { it.toSession() }

    override fun observeChanges(): Flow<Unit> =
        dao.observeChangeToken().distinctUntilChanged().map { }
}
