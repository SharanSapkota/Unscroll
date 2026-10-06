package com.unscroll.app.ui

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.unscroll.app.ui.apps.AppsScreen
import com.unscroll.app.ui.dashboard.DashboardScreen
import com.unscroll.app.ui.navigation.TopLevelDestination
import com.unscroll.app.ui.scroll.AccessibilityDisclosureScreen
import com.unscroll.app.ui.scroll.RestrictedSettingHelpScreen
import com.unscroll.app.ui.settings.SettingsScreen

/** Sub-screens of Settings (M7). They keep the Settings tab selected. */
private const val ROUTE_SCROLL_DISCLOSURE = "settings/scroll-disclosure"
private const val ROUTE_RESTRICTED_HELP = "settings/restricted-setting-help"

@Composable
fun UnscrollApp(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        modifier = modifier,
        bottomBar = {
            NavigationBar {
                TopLevelDestination.entries.forEach { destination ->
                    val selected = currentDestination?.hierarchy?.any {
                        // Sub-screens ("settings/...") keep their tab selected.
                        it.route == destination.route || it.route?.startsWith("${destination.route}/") == true
                    } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                painter = painterResource(destination.iconRes),
                                contentDescription = null,
                            )
                        },
                        label = { Text(stringResource(destination.labelRes)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.DASHBOARD.route,
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        ) {
            composable(TopLevelDestination.DASHBOARD.route) { DashboardScreen() }
            composable(TopLevelDestination.APPS.route) { AppsScreen() }
            composable(TopLevelDestination.SETTINGS.route) {
                SettingsScreen(
                    onScrollCountingSetUp = { navController.navigate(ROUTE_SCROLL_DISCLOSURE) },
                    onRestrictedSettingHelp = { navController.navigate(ROUTE_RESTRICTED_HELP) },
                )
            }
            composable(ROUTE_SCROLL_DISCLOSURE) {
                AccessibilityDisclosureScreen(
                    onFinished = { navController.popBackStack() },
                    onRestrictedHelp = { navController.navigate(ROUTE_RESTRICTED_HELP) },
                )
            }
            composable(ROUTE_RESTRICTED_HELP) {
                RestrictedSettingHelpScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
