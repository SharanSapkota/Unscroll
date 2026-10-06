package com.unscroll.app.data.permission

import com.unscroll.app.domain.permission.PermissionState

/** Reads the current permission state from the system. */
fun interface PermissionChecker {
    fun check(): PermissionState
}
