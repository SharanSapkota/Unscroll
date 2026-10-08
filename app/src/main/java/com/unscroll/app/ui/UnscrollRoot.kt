package com.unscroll.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.unscroll.app.domain.onboarding.AppDestination
import com.unscroll.app.ui.onboarding.OnboardingScreen
import com.unscroll.app.ui.plus.PaywallScreen
import com.unscroll.app.ui.plus.PickAppsScreen

@Composable
fun UnscrollRoot(viewModel: AppViewModel = hiltViewModel()) {
    val destination by viewModel.destination.collectAsStateWithLifecycle()
    when (destination) {
        AppDestination.ONBOARDING -> OnboardingScreen()
        AppDestination.PICK_APPS -> PickFreeApps()
        AppDestination.MAIN -> UnscrollApp()
        null -> Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        )
    }
}

/** First launch (or more apps than the free tier): pick the free app, or get Plus for all. */
@Composable
private fun PickFreeApps() {
    var showPaywall by rememberSaveable { mutableStateOf(false) }
    Surface(modifier = Modifier.fillMaxSize()) {
        if (showPaywall) {
            BackHandler { showPaywall = false }
            PaywallScreen(onClose = { showPaywall = false }, modifier = Modifier.safeDrawingPadding())
        } else {
            // Picking (or getting Plus) updates the gate, which then opens the main app.
            PickAppsScreen(onDone = {}, onPlus = { showPaywall = true }, modifier = Modifier.safeDrawingPadding())
        }
    }
}
