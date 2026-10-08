package com.unscroll.app.domain.apps

/** Pure rules for the "Add apps" picker. */
object AppPicker {

    /** Popular apps, shown at the top when installed. Matched by package name. */
    val POPULAR: List<String> = listOf(
        "com.google.android.youtube",
        "com.reddit.frontpage",
        "com.twitter.android",
        "com.snapchat.android",
        "com.pinterest",
        "com.instagram.barcelona",
        "com.discord",
        "org.telegram.messenger",
        "com.whatsapp",
        "com.linkedin.android",
        "tv.twitch.android.app",
        "com.netflix.mediaclient",
    )

    /** Debug builds add Chrome, so tracking can be tested without social apps. */
    const val CHROME = "com.android.chrome"

    fun popular(debug: Boolean): List<String> = if (debug) POPULAR + CHROME else POPULAR

    /**
     * The picker's list: launchable, not excluded, one entry per package (variants of the same
     * product, like TikTok's two packages, stay separate), sorted by name.
     */
    fun candidates(launchable: List<LaunchableApp>, isExcluded: (String) -> Boolean): List<LaunchableApp> =
        launchable
            .distinctBy { it.packageName }
            .filterNot { isExcluded(it.packageName) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })

    /** Case-insensitive match on the app's name; a blank query matches everything. */
    fun search(apps: List<LaunchableApp>, query: String): List<LaunchableApp> {
        val q = query.trim()
        if (q.isEmpty()) return apps
        return apps.filter { it.label.contains(q, ignoreCase = true) }
    }

    /** The "Popular" row: installed popular apps (in [popular] order) that match the search. */
    fun popularRow(candidates: List<LaunchableApp>, popular: List<String>, query: String): List<LaunchableApp> {
        val byPackage = search(candidates, query).associateBy { it.packageName }
        return popular.mapNotNull { byPackage[it] }
    }
}

/** What happened when the user tapped "Add". */
enum class AddResult { ADDED, NEEDS_PLUS, EXCLUDED }

/** The free-tier limit on adding, through one number (unlimited with Plus). */
object AddRules {
    fun canAdd(activeCount: Int, maxActiveApps: Int): Boolean = activeCount < maxActiveApps
}
