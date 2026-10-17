package com.unscroll.app.domain.permission

import com.unscroll.app.domain.scroll.ScrollConsent
import com.unscroll.app.domain.scroll.ScrollCountingStatus

/** One line of the Settings permission checklist. */
enum class HealthItem {
    USAGE_ACCESS,
    OVERLAY,
    NOTIFICATIONS,
    BATTERY,

    /** "Accessibility (swipe counting)": always listed, fine only while the service counts. */
    ACCESSIBILITY,
}

/** Settings › "All set" or "Fix 2 issues". Pure. */
object PermissionHealth {

    /**
     * Every checklist line, in order, with whether it is fine (a green check). Accessibility (swipe
     * counting) is always listed, so it can't be missed: green "On" only while the service is
     * counting, red "Off, tap to enable" otherwise.
     */
    fun checklist(permissions: PermissionState, scroll: ScrollCountingStatus): List<Pair<HealthItem, Boolean>> = listOf(
        HealthItem.USAGE_ACCESS to permissions.isGranted(AppPermission.USAGE_ACCESS),
        HealthItem.OVERLAY to permissions.isGranted(AppPermission.OVERLAY),
        HealthItem.NOTIFICATIONS to permissions.isGranted(AppPermission.NOTIFICATIONS),
        HealthItem.BATTERY to permissions.isGranted(AppPermission.IGNORE_BATTERY_OPTIMIZATIONS),
        HealthItem.ACCESSIBILITY to (scroll == ScrollCountingStatus.ACTIVE),
    )

    /** The lines that need fixing, swipe counting included while it isn't running. */
    fun issues(permissions: PermissionState, scroll: ScrollCountingStatus): List<HealthItem> =
        checklist(permissions, scroll).filterNot { it.second }.map { it.first }

    /**
     * Where the accessibility line's button goes: the disclosure first while the user hasn't agreed
     * in Unscroll (Play policy: the prominent disclosure always comes before the system screen),
     * else straight to Accessibility settings.
     */
    fun accessibilityNeedsDisclosure(consent: ScrollConsent): Boolean = consent != ScrollConsent.AGREED
}
