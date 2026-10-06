package com.unscroll.app.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.unscroll.app.R

enum class TopLevelDestination(
    val route: String,
    @StringRes val labelRes: Int,
    @DrawableRes val iconRes: Int,
) {
    DASHBOARD("dashboard", R.string.nav_dashboard, R.drawable.ic_nav_dashboard),
    APPS("apps", R.string.nav_apps, R.drawable.ic_nav_apps),
    SETTINGS("settings", R.string.nav_settings, R.drawable.ic_nav_settings),
}
