package com.unscroll.app.testing

import com.unscroll.app.data.onboarding.OnboardingRepository
import com.unscroll.app.data.permission.PermissionChecker
import com.unscroll.app.domain.permission.AppPermission
import com.unscroll.app.domain.permission.PermissionState
import com.unscroll.app.domain.session.HeartbeatStore
import com.unscroll.app.domain.session.Session
import com.unscroll.app.domain.session.SessionStore
import com.unscroll.app.domain.tracking.ForegroundAppDetector
import com.unscroll.app.domain.tracking.ScreenStateSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

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
