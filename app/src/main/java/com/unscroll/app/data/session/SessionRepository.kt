package com.unscroll.app.data.session

import com.unscroll.app.data.db.SectionSegmentDao
import com.unscroll.app.data.db.SectionSegmentEntity
import com.unscroll.app.data.db.SessionDao
import com.unscroll.app.data.db.SessionEntity
import com.unscroll.app.data.db.toSession
import com.unscroll.app.domain.session.ScreenSection
import com.unscroll.app.domain.session.SegmentStore
import com.unscroll.app.domain.session.Session
import com.unscroll.app.domain.session.SessionStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class SessionRepository @Inject constructor(
    private val dao: SessionDao,
    private val segments: SectionSegmentDao,
) : SessionStore, SegmentStore {

    override suspend fun openSession(packageName: String, startTime: Long): Long =
        dao.insert(SessionEntity(packageName = packageName, startTime = startTime))

    override suspend fun closeSession(id: Long, endTime: Long) = dao.close(id, endTime)

    override suspend fun updateScrollCount(id: Long, scrollCount: Int) = dao.updateScrollCount(id, scrollCount)

    override suspend fun closeOrphanedSessions(endTime: Long): Int = dao.closeAllOpen(endTime)

    override suspend fun openSegment(sessionId: Long, packageName: String, section: ScreenSection, startTime: Long): Long =
        segments.insert(
            SectionSegmentEntity(sessionId = sessionId, packageName = packageName, startTime = startTime, section = section.name),
        )

    override suspend fun closeSegment(id: Long, endTime: Long) = segments.close(id, endTime)

    override suspend fun updateSegmentSwipes(id: Long, swipes: Int) = segments.updateSwipes(id, swipes)

    override suspend fun closeOrphanedSegments(endTime: Long): Int = segments.closeAllOpen(endTime)

    fun observeRecentSessions(limit: Int): Flow<List<Session>> =
        dao.observeRecent(limit).map { sessions -> sessions.map { it.toSession() } }
}
