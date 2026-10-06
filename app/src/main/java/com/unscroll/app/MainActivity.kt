package com.unscroll.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.unscroll.app.data.permission.PermissionRepository
import com.unscroll.app.ui.UnscrollRoot
import com.unscroll.app.ui.theme.UnscrollTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var permissionRepository: PermissionRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            UnscrollTheme {
                UnscrollRoot()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Users grant special access in Settings, and Android sends no callback. Re-check whenever
        // they come back.
        permissionRepository.refresh()
    }
}
