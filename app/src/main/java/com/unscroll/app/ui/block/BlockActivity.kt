package com.unscroll.app.ui.block

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.unscroll.app.domain.blocking.BlockReason
import com.unscroll.app.ui.theme.UnscrollTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Full-screen block screen, opened by BlockEnforcer on top of the home screen when a blocked app
 * comes to the front. Back does nothing, so it can't be used to slip back into the app.
 */
@AndroidEntryPoint
class BlockActivity : ComponentActivity() {

    private val viewModel: BlockViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    // Deliberately ignored.
                }
            },
        )
        setContent {
            UnscrollTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                LaunchedEffect(state.accessRequest) {
                    if (state.accessRequest == AccessRequest.Granted) openBlockedApp(state.packageName)
                }
                BlockScreen(
                    state = state,
                    onGoHome = ::goHome,
                    onRequestAccess = viewModel::requestAccess,
                    onCancelAccess = viewModel::cancelAccessRequest,
                    onFrictionPassed = viewModel::frictionPassed,
                )
            }
        }
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        finish()
    }

    private fun openBlockedApp(packageName: String) {
        val launch = packageManager.getLaunchIntentForPackage(packageName)
        if (launch != null) startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    companion object {
        const val EXTRA_PACKAGE = "package"
        const val EXTRA_REASON = "reason"
        const val EXTRA_UNTIL = "until"

        fun intent(context: Context, packageName: String, reason: BlockReason, until: Long?): Intent =
            Intent(context, BlockActivity::class.java)
                .putExtra(EXTRA_PACKAGE, packageName)
                .putExtra(EXTRA_REASON, reason.name)
                .putExtra(EXTRA_UNTIL, until ?: -1L)
    }
}
