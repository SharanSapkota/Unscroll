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
import com.unscroll.app.domain.scroll.AppScrollStats
import com.unscroll.app.domain.session.HeartbeatStore
import com.unscroll.app.domain.session.Session
import com.unscroll.app.domain.session.SessionStore
import com.unscroll.app.domain.tracking.ForegroundAppDetector
import com.unscroll.app.domain.tracking.ScreenStateSource
import com.unscroll.app.domain.tracking.TrackedApps
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
        apps = TrackedApps.packageNames.toList(),
        active = TrackedApps.packageNames,
        isPlus = true,
        needsPick = false,
    ),
) : TrackedAppsSource {
    val current = MutableStateFlow(initial)
    override val state: Flow<TrackedAppsState> = current

    override suspend fun isActive(packageName: String): Boolean = packageName in state.first().active
}
