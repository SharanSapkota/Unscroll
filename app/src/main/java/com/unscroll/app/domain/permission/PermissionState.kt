package com.unscroll.app.domain.permission

/** Snapshot of which [AppPermission]s are currently granted. */
data class PermissionState(val granted: Set<AppPermission>) {

    fun isGranted(permission: AppPermission): Boolean = permission in granted

    /** True when every required permission is granted, so the dashboard can be shown. */
    val requiredGranted: Boolean
        get() = AppPermission.entries.filter { it.required }.all { it in granted }

    /** The first required permission that is still missing, in onboarding order. */
    val firstMissingRequired: AppPermission?
        get() = AppPermission.entries.firstOrNull { it.required && it !in granted }

    companion object {
        val NONE = PermissionState(emptySet())
        val ALL = PermissionState(AppPermission.entries.toSet())
    }
}
