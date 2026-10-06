package com.unscroll.app.testing

import com.unscroll.app.data.onboarding.OnboardingRepository
import com.unscroll.app.data.permission.PermissionChecker
import com.unscroll.app.domain.permission.AppPermission
import com.unscroll.app.domain.permission.PermissionState
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
