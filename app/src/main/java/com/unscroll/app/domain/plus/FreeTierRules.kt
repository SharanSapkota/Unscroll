package com.unscroll.app.domain.plus

/**
 * Which tracked apps are active. Paused apps keep their history and settings, but get no tracking,
 * pill, limits or blocking until the user picks them again or gets Plus.
 */
data class TrackedAppsState(
    /** The apps shown to the user (installed tracked apps). */
    val apps: List<String>,
    /** The apps that are tracked. With Plus, every tracked app, installed later or not. */
    val active: Set<String>,
    val isPlus: Boolean,
    /** A free user with more apps than the free tier who hasn't picked yet. */
    val needsPick: Boolean,
) {
    fun isPaused(packageName: String): Boolean = packageName !in active

    val paused: List<String> get() = apps.filter(::isPaused)
}

/** Pure free-tier rules. The count comes from [FreeTier.FREE_APPS]. */
object FreeTierRules {

    /**
     * @param apps installed tracked apps, in display order
     * @param allTracked every package Unscroll knows (Plus tracks them all)
     * @param picked the user's picks, or empty if none were made
     * @param mostUsed packages ordered by recent use, most used first
     */
    fun resolve(
        isPlus: Boolean,
        apps: List<String>,
        allTracked: Set<String>,
        picked: Set<String>,
        mostUsed: List<String>,
        freeApps: Int = FreeTier.FREE_APPS,
    ): TrackedAppsState {
        if (isPlus) return TrackedAppsState(apps, allTracked, isPlus = true, needsPick = false)
        val validPicks = apps.filter { it in picked }
        val active = validPicks.ifEmpty { byUse(apps, mostUsed) }.take(freeApps).toSet()
        return TrackedAppsState(
            apps = apps,
            active = active,
            isPlus = false,
            needsPick = validPicks.isEmpty() && apps.size > freeApps,
        )
    }

    /**
     * The apps kept when Plus ends: the user's picks if they made any, else the most used.
     * Nothing is deleted; the others are only paused.
     */
    fun keepOnDowngrade(
        apps: List<String>,
        picked: Set<String>,
        mostUsed: List<String>,
        freeApps: Int = FreeTier.FREE_APPS,
    ): Set<String> = apps.filter { it in picked }.ifEmpty { byUse(apps, mostUsed) }.take(freeApps).toSet()

    /**
     * Tapping an app on the pick screen. Under the limit it is added (or removed); at the limit the
     * oldest choice makes room, so with one free app it behaves like a radio button.
     */
    fun toggle(selection: List<String>, packageName: String, freeApps: Int = FreeTier.FREE_APPS): List<String> = when {
        packageName in selection -> selection - packageName
        selection.size < freeApps -> selection + packageName
        else -> selection.drop(selection.size - freeApps + 1) + packageName
    }

    /** "Continue" on the pick screen: a full selection (or every app, if there are fewer). */
    fun canConfirm(selection: List<String>, apps: List<String>, freeApps: Int = FreeTier.FREE_APPS): Boolean =
        selection.isNotEmpty() && selection.size == minOf(freeApps, apps.size) && selection.all { it in apps }

    /** The pre-selection on the pick screen: the most used apps. */
    fun suggested(apps: List<String>, mostUsed: List<String>, freeApps: Int = FreeTier.FREE_APPS): List<String> =
        byUse(apps, mostUsed).take(freeApps)

    /** [apps] ordered by use; unused apps keep their order at the end. */
    private fun byUse(apps: List<String>, mostUsed: List<String>): List<String> =
        mostUsed.filter { it in apps } + apps.filter { it !in mostUsed }
}
