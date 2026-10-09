package com.unscroll.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.unscroll.app.BuildConfig
import com.unscroll.app.ui.apps.AddAppsScreen
import com.unscroll.app.ui.apps.AppDetailScreen
import com.unscroll.app.ui.apps.AppDetailViewModel
import com.unscroll.app.ui.apps.AppsScreen
import com.unscroll.app.ui.components.rememberHaptics
import com.unscroll.app.ui.home.HomeScreen
import com.unscroll.app.ui.navigation.AppDetailRoute
import com.unscroll.app.ui.navigation.TopLevelDestination
import com.unscroll.app.ui.plus.PaywallScreen
import com.unscroll.app.ui.plus.PickAppsScreen
import com.unscroll.app.ui.scroll.AccessibilityDisclosureScreen
import com.unscroll.app.ui.scroll.RestrictedSettingHelpScreen
import com.unscroll.app.ui.section.SectionBlockingDisclosureScreen
import com.unscroll.app.ui.section.SectionInspectorScreen
import com.unscroll.app.ui.settings.SettingsScreen
import com.unscroll.app.ui.theme.Motion

/** Sub-screens of Settings (M7). They keep the Settings tab selected. */
private const val ROUTE_SCROLL_DISCLOSURE = "settings/scroll-disclosure"
private const val ROUTE_RESTRICTED_HELP = "settings/restricted-setting-help"
private const val ROUTE_PICK_APPS = "settings/pick-apps"
private const val ROUTE_SECTION_DISCLOSURE = "settings/section-disclosure"

/** Debug builds only. */
private const val ROUTE_SECTION_INSPECTOR = "settings/section-inspector"

/** The "+" picker on the Apps tab. */
private const val ROUTE_ADD_APPS = "apps/add"

/** Unscroll Plus, full screen. */
private const val ROUTE_PLUS = "plus"

/** Full-screen pages: no bottom bar. */
private val FULL_SCREEN_ROUTES = setOf(AppDetailRoute.ROUTE, ROUTE_PLUS, ROUTE_PICK_APPS, ROUTE_ADD_APPS)

@Composable
fun UnscrollApp(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val haptics = rememberHaptics()
    // App detail, Plus, the free-app pick and the add-apps picker are full-screen pages: no bottom bar.
    val showBottomBar = currentDestination?.route !in FULL_SCREEN_ROUTES
    val openApp: (String) -> Unit = { navController.navigate(AppDetailRoute.of(it)) }
    val openPlus: () -> Unit = { navController.navigate(ROUTE_PLUS) { launchSingleTop = true } }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically(tween(Motion.MEDIUM)) { it } + fadeIn(tween(Motion.MEDIUM)),
                exit = slideOutVertically(tween(Motion.SHORT)) { it } + fadeOut(tween(Motion.SHORT)),
            ) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                    TopLevelDestination.entries.forEach { destination ->
                        val selected = currentDestination?.hierarchy?.any {
                            // Sub-screens ("settings/...") keep their tab selected.
                            it.route == destination.route || it.route?.startsWith("${destination.route}/") == true
                        } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (!selected) haptics.tick()
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(painter = painterResource(destination.iconRes), contentDescription = null) },
                            label = { Text(stringResource(destination.labelRes)) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.HOME.route,
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
            // Fade-through between screens.
            enterTransition = { fadeIn(tween(Motion.MEDIUM)) + scaleIn(tween(Motion.MEDIUM), initialScale = ENTER_SCALE) },
            exitTransition = { fadeOut(tween(Motion.SHORT)) },
            popEnterTransition = { fadeIn(tween(Motion.MEDIUM)) },
            popExitTransition = { fadeOut(tween(Motion.SHORT)) },
        ) {
            composable(TopLevelDestination.HOME.route) { HomeScreen(onOpenApp = openApp) }
            composable(TopLevelDestination.APPS.route) {
                AppsScreen(
                    onOpenApp = openApp,
                    onOpenPlus = openPlus,
                    onAddApps = { navController.navigate(ROUTE_ADD_APPS) { launchSingleTop = true } },
                )
            }
            composable(ROUTE_ADD_APPS) {
                AddAppsScreen(onBack = { navController.popBackStack() }, onOpenPlus = openPlus)
            }
            composable(TopLevelDestination.SETTINGS.route) {
                SettingsScreen(
                    onScrollCountingSetUp = { navController.navigate(ROUTE_SCROLL_DISCLOSURE) },
                    onRestrictedSettingHelp = { navController.navigate(ROUTE_RESTRICTED_HELP) },
                    onOpenPlus = openPlus,
                    onPickApps = { navController.navigate(ROUTE_PICK_APPS) },
                    onSectionSetUp = { navController.navigate(ROUTE_SECTION_DISCLOSURE) },
                    onSectionInspector = { navController.navigate(ROUTE_SECTION_INSPECTOR) },
                )
            }
            composable(ROUTE_PLUS) { PaywallScreen(onClose = { navController.popBackStack() }) }
            composable(ROUTE_PICK_APPS) {
                PickAppsScreen(
                    onDone = { navController.popBackStack() },
                    onPlus = { navController.navigate(ROUTE_PLUS) { popUpTo(ROUTE_PICK_APPS) { inclusive = true } } },
                )
            }
            composable(
                route = AppDetailRoute.ROUTE,
                arguments = listOf(navArgument(AppDetailViewModel.ARG_PACKAGE) { type = NavType.StringType }),
            ) {
                AppDetailScreen(
                    onBack = { navController.popBackStack() },
                    onOpenPlus = openPlus,
                    onSectionSetUp = { navController.navigate(ROUTE_SECTION_DISCLOSURE) },
                )
            }
            composable(ROUTE_SCROLL_DISCLOSURE) {
                AccessibilityDisclosureScreen(
                    onFinished = { navController.popBackStack() },
                    onRestrictedHelp = { navController.navigate(ROUTE_RESTRICTED_HELP) },
                )
            }
            composable(ROUTE_SECTION_DISCLOSURE) {
                SectionBlockingDisclosureScreen(
                    onFinished = { navController.popBackStack() },
                    onRestrictedHelp = { navController.navigate(ROUTE_RESTRICTED_HELP) },
                )
            }
            if (BuildConfig.DEBUG) {
                composable(ROUTE_SECTION_INSPECTOR) {
                    SectionInspectorScreen(onBack = { navController.popBackStack() })
                }
            }
            composable(ROUTE_RESTRICTED_HELP) {
                RestrictedSettingHelpScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

private const val ENTER_SCALE = 0.96f
