package com.unscroll.app.domain.permission

import com.unscroll.app.domain.scroll.ScrollCountingStatus

/** One line of the Settings permission checklist. */
enum class HealthItem {
    USAGE_ACCESS,
    OVERLAY,
    NOTIFICATIONS,
    BATTERY,

    /** Only listed once the user opted into scroll counting; an issue while it isn't running. */
    ACCESSIBILITY,
}

/** Settings › "All set" or "Fix 2 issues". Pure. */
object PermissionHealth {

    /**
     * Every checklist line, in order, with whether it is fine (a green check). Accessibility is
     * only listed when the user turned scroll counting on.
     */
    fun checklist(permissions: PermissionState, scroll: ScrollCountingStatus): List<Pair<HealthItem, Boolean>> = buildList {
        add(HealthItem.USAGE_ACCESS to permissions.isGranted(AppPermission.USAGE_ACCESS))
        add(HealthItem.OVERLAY to permissions.isGranted(AppPermission.OVERLAY))
        add(HealthItem.NOTIFICATIONS to permissions.isGranted(AppPermission.NOTIFICATIONS))
        add(HealthItem.BATTERY to permissions.isGranted(AppPermission.IGNORE_BATTERY_OPTIMIZATIONS))
        if (scrollCountingOn(scroll)) add(HealthItem.ACCESSIBILITY to !needsAccessibility(scroll))
    }

    /** The lines that need fixing. Scroll counting that is simply off is not an issue. */
    fun issues(permissions: PermissionState, scroll: ScrollCountingStatus): List<HealthItem> =
        checklist(permissions, scroll).filterNot { it.second }.map { it.first }

    private fun scrollCountingOn(scroll: ScrollCountingStatus): Boolean =
        scroll == ScrollCountingStatus.ACTIVE || needsAccessibility(scroll)

    private fun needsAccessibility(scroll: ScrollCountingStatus): Boolean =
        scroll == ScrollCountingStatus.NEEDS_ENABLING || scroll == ScrollCountingStatus.NEEDS_REENABLE
}
