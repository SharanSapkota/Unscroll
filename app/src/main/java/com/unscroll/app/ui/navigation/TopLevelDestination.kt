package com.unscroll.app.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.unscroll.app.R

enum class TopLevelDestination(
    val route: String,
    @StringRes val labelRes: Int,
    @DrawableRes val iconRes: Int,
) {
    HOME("home", R.string.nav_home, R.drawable.ic_nav_home),
    APPS("apps", R.string.nav_apps, R.drawable.ic_nav_apps),
    SETTINGS("settings", R.string.nav_settings, R.drawable.ic_nav_settings),
}

/** App detail, full screen over the tabs: "app/{packageName}". */
object AppDetailRoute {
    const val ROUTE = "app/{packageName}"
    fun of(packageName: String): String = "app/$packageName"
}
