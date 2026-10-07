package com.unscroll.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.unscroll.app.data.appearance.AppearancePreferences
import com.unscroll.app.data.permission.PermissionRepository
import com.unscroll.app.data.scroll.ScrollCountingRepository
import com.unscroll.app.service.TrackingController
import com.unscroll.app.ui.UnscrollRoot
import com.unscroll.app.ui.fox.ProvideFoxSettings
import com.unscroll.app.ui.theme.UnscrollTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var permissionRepository: PermissionRepository

    @Inject
    lateinit var trackingController: TrackingController

    @Inject
    lateinit var scrollCountingRepository: ScrollCountingRepository

    @Inject
    lateinit var appearancePreferences: AppearancePreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val dynamicColor by appearancePreferences.dynamicColor.collectAsStateWithLifecycle(initialValue = false)
            UnscrollTheme(dynamicColor = dynamicColor) {
                ProvideFoxSettings(appearancePreferences.fox) {
                    UnscrollRoot()
                }
            }
        }
        // Tracking is on by default, so nobody has to find a switch: start the service whenever
        // it should run and isn't running, i.e. when the app opens, when onboarding finishes and
        // when a missing permission gets granted. Only while resumed, where Android allows starting
        // a foreground service; if it refuses anyway, the next resume tries again.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                trackingController.startTriggers.collect { trackingController.startIfReady() }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Users grant special access in Settings, and Android sends no callback. Re-check whenever
        // they come back.
        permissionRepository.refresh()
        // Same for the optional accessibility service (scroll counting).
        scrollCountingRepository.refresh()
        // Starting tracking (also after a force stop) is handled by the collector in onCreate.
    }
}
