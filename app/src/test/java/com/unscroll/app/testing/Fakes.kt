package com.unscroll.app.testing

import com.unscroll.app.data.onboarding.OnboardingRepository
import com.unscroll.app.data.permission.PermissionChecker
import com.unscroll.app.domain.insights.AppSessionStats
import com.unscroll.app.domain.insights.TimeRange
import com.unscroll.app.domain.insights.UsageDataSource
import com.unscroll.app.domain.insights.UsageMath
import com.unscroll.app.domain.permission.AppPermission
import com.unscroll.app.domain.permission.PermissionState
import com.unscroll.app.domain.plus.TrackedAppsSource
import com.unscroll.app.domain.plus.TrackedAppsState
import com.unscroll.app.domain.reels.ReelsLive
import com.unscroll.app.domain.reels.ReelsState
import com.unscroll.app.domain.reels.ReelsTimerStatus
import com.unscroll.app.domain.reels.ReelsTracking
import com.unscroll.app.domain.scroll.AppScrollStats
import com.unscroll.app.domain.session.HeartbeatStore
import com.unscroll.app.domain.session.ScreenSection
import com.unscroll.app.domain.session.SegmentStore
import com.unscroll.app.domain.session.Session
import com.unscroll.app.domain.session.SessionStore
import com.unscroll.app.domain.tracking.DefaultTrackedApps
import com.unscroll.app.domain.tracking.ForegroundAppDetector
import com.unscroll.app.domain.tracking.ScreenStateSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class FakePermissionChecker(var state: PermissionState = PermissionState.NONE) : PermissionChecker {
    override fun check(): PermissionState = state
}

class FakeOnboardingRepository(completed: Boolean = false) : OnboardingRepository {
    val completed = MutableStateFlow(completed)

    override val onboardingCompleted: Flow<Boolean> = this.completed

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        this.completed.value = completed
    }
}

fun permissions(vararg granted: AppPermission) = PermissionState(granted.toSet())

val REQUIRED_ONLY = permissions(AppPermission.USAGE_ACCESS, AppPermission.OVERLAY)

class FakeSessionStore : SessionStore {
    val sessions = mutableListOf<Session>()

    override suspend fun openSession(packageName: String, startTime: Long): Long {
        val id = (sessions.maxOfOrNull { it.id } ?: 0L) + 1
        sessions += Session(id, packageName, startTime, endTime = null, scrollCount = 0)
        return id
    }

    override suspend fun closeSession(id: Long, endTime: Long) {
        sessions.replaceAll { if (it.id == id) it.copy(endTime = endTime) else it }
    }

    override suspend fun updateScrollCount(id: Long, scrollCount: Int) {
        sessions.replaceAll { if (it.id == id) it.copy(scrollCount = scrollCount) else it }
    }

    override suspend fun closeOrphanedSessions(endTime: Long): Int {
        val count = sessions.count { it.endTime == null }
        sessions.replaceAll {
            if (it.endTime == null) it.copy(endTime = maxOf(it.startTime, endTime)) else it
        }
        return count
    }
}

/** A stored section segment, as [FakeSegmentStore] keeps it. */
data class FakeSegment(
    val id: Long,
    val sessionId: Long,
    val packageName: String,
    val section: ScreenSection,
    val startTime: Long,
    val endTime: Long? = null,
    val swipes: Int = 0,
)

class FakeSegmentStore : SegmentStore {
    val segments = mutableListOf<FakeSegment>()

    override suspend fun openSegment(sessionId: Long, packageName: String, section: ScreenSection, startTime: Long): Long {
        val id = (segments.maxOfOrNull { it.id } ?: 0L) + 1
        segments += FakeSegment(id, sessionId, packageName, section, startTime)
        return id
    }

    override suspend fun closeSegment(id: Long, endTime: Long) {
        segments.replaceAll { if (it.id == id) it.copy(endTime = maxOf(it.startTime, endTime)) else it }
    }

    override suspend fun updateSegmentSwipes(id: Long, swipes: Int) {
        segments.replaceAll { if (it.id == id) it.copy(swipes = swipes) else it }
    }

    override suspend fun closeOrphanedSegments(endTime: Long): Int {
        val count = segments.count { it.endTime == null }
        segments.replaceAll { if (it.endTime == null) it.copy(endTime = maxOf(it.startTime, endTime)) else it }
        return count
    }
}

/** Reels-only timer state under test control. Empty by default: every app works as before. */
class FakeReelsTracking : ReelsTracking {
    val state = MutableStateFlow<Map<String, ReelsLive>>(emptyMap())
    override val live: StateFlow<Map<String, ReelsLive>> = state
    val left = mutableListOf<String>()

    override fun onAppLeft(packageName: String) {
        left += packageName
        state.value[packageName]?.let { state.value = state.value + (packageName to it.copy(state = null, since = 0)) }
    }

    /** The timer runs for [packageName] with [section] confirmed, which began at [since]. */
    fun set(packageName: String, section: ReelsState?, since: Long, status: ReelsTimerStatus = ReelsTimerStatus.ACTIVE) {
        state.value = state.value + (packageName to ReelsLive(packageName, status, section, since))
    }
}

class FakeHeartbeatStore(var last: Long? = null) : HeartbeatStore {
    val saved = mutableListOf<Long>()

    override suspend fun saveHeartbeat(timestamp: Long) {
        saved += timestamp
        last = timestamp
    }

    override suspend fun lastHeartbeat(): Long? = last
}

class FakeForegroundAppDetector : ForegroundAppDetector {
    val app = MutableStateFlow<String?>(null)
    override val foregroundApp: Flow<String?> = app
}

class FakeScreenState : ScreenStateSource {
    val screenOn = MutableStateFlow(true)
    override val isScreenOn: Flow<Boolean> = screenOn
}

/** In-memory [UsageDataSource] with the same semantics as the Room queries. */
class FakeUsageDataSource(val sessions: MutableList<Session> = mutableListOf()) : UsageDataSource {
    val changes = MutableStateFlow(0)

    override suspend fun appTotals(range: TimeRange, now: Long): Map<String, Long> =
        sessions.groupBy { it.packageName }
            .mapValues { (_, list) -> list.sumOf { UsageMath.overlap(it, range, now) } }
            .filter { (packageName, _) ->
                sessions.any {
                    it.packageName == packageName &&
                        it.startTime < range.to &&
                        (it.endTime ?: now) > range.from
                }
            }

    override suspend fun appSessionStats(range: TimeRange, now: Long): List<AppSessionStats> =
        sessions.filter { it.startTime >= range.from && it.startTime < range.to }
            .groupBy { it.packageName }
            .map { (packageName, list) ->
                val durations = list.map { ((it.endTime ?: now) - it.startTime).coerceAtLeast(0) }
                AppSessionStats(packageName, list.size, durations.sum(), durations.max())
            }

    override suspend fun appScrollStats(range: TimeRange, now: Long): List<AppScrollStats> =
        sessions.filter { it.startTime >= range.from && it.startTime < range.to }
            .groupBy { it.packageName }
            .map { (packageName, list) ->
                AppScrollStats(
                    packageName = packageName,
                    swipes = list.sumOf { it.scrollCount },
                    sessions = list.size,
                    durationMillis = list.sumOf { ((it.endTime ?: now) - it.startTime).coerceAtLeast(0) },
                )
            }

    override suspend fun sessionsOverlapping(range: TimeRange): List<Session> =
        sessions.filter { it.startTime < range.to && (it.endTime ?: Long.MAX_VALUE) > range.from }
            .sortedBy { it.startTime }

    override suspend fun firstSessionStart(): Long? = sessions.minOfOrNull { it.startTime }

    override fun observeChanges(): Flow<Unit> = changes.map { }
}

/** Active apps under test control. Every tracked app is active by default (like Plus). */
class FakeTrackedAppsSource(
    initial: TrackedAppsState = TrackedAppsState(
        apps = DefaultTrackedApps.packageNames,
        active = DefaultTrackedApps.packageNames.toSet(),
        isPlus = true,
        needsPick = false,
    ),
) : TrackedAppsSource {
    val current = MutableStateFlow(initial)
    override val state: Flow<TrackedAppsState> = current

    override suspend fun isActive(packageName: String): Boolean = packageName in state.first().active

    override suspend fun countsSwipes(packageName: String): Boolean = state.first().countsSwipes(packageName)
}
