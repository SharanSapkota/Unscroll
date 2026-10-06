package com.unscroll.app.data.permission

import com.unscroll.app.domain.permission.AppPermission
import com.unscroll.app.domain.permission.PermissionState
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Source of truth for permission state.
 *
 * Android has no callback for special access changes, so [refresh] must be called whenever the
 * app returns to the foreground (MainActivity does this in `onResume`).
 */
@Singleton
class PermissionRepository @Inject constructor(
    private val checker: PermissionChecker,
) {
    private val state = MutableStateFlow(checker.check())

    val permissions: StateFlow<PermissionState> = state.asStateFlow()

    fun isGranted(permission: AppPermission): Flow<Boolean> =
        permissions.map { it.isGranted(permission) }.distinctUntilChanged()

    fun refresh() {
        state.value = checker.check()
    }
}
