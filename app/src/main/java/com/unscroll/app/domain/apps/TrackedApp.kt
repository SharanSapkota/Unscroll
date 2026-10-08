package com.unscroll.app.domain.apps

/** Whether the user tracks the app right now. Removed apps are kept in the database, but hidden. */
enum class TrackedAppStatus { ACTIVE, PAUSED }

/**
 * One app in the user's tracked list. Removing it keeps the row (and the app's sessions, limits
 * and settings in their own tables), so adding it again restores everything.
 */
data class TrackedApp(
    val packageName: String,
    /** The name when it was added or last seen installed; shown while it isn't installed. */
    val label: String?,
    val addedAt: Long,
    val status: TrackedAppStatus = TrackedAppStatus.ACTIVE,
    /** Per-app "Count swipes": swipe events differ between apps, so it can be switched off. */
    val countSwipes: Boolean = true,
    /** Installed on the phone right now; re-checked on resume. */
    val installed: Boolean = true,
)

/** An app the picker can offer: a launchable package and its name. */
data class LaunchableApp(val packageName: String, val label: String)
