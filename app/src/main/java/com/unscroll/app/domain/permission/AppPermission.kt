package com.unscroll.app.domain.permission

/**
 * Special access the app asks the user for during onboarding.
 *
 * Required permissions gate the dashboard. Optional ones can be skipped.
 */
enum class AppPermission(val required: Boolean) {
    /** Lets the app see which app is in the foreground (package names and timestamps only). */
    USAGE_ACCESS(required = true),

    /** Lets the app draw the timer overlay and block screen on top of other apps. */
    OVERLAY(required = true),

    /** Status notification for the tracking service, plus nudges and break reminders. */
    NOTIFICATIONS(required = false),

    /** Exempts the app from battery optimization so OEM battery savers don't kill tracking. */
    IGNORE_BATTERY_OPTIMIZATIONS(required = false),
}
