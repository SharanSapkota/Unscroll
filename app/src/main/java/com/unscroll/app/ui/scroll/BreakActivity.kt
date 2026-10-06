package com.unscroll.app.ui.scroll

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
import com.unscroll.app.ui.theme.UnscrollTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * "Take a break" after N swipes (M7). Opened by FrictionCoordinator on top of the home screen,
 * like the pause screen. Back does nothing, but Home always works: the user is never trapped.
 */
@AndroidEntryPoint
class BreakActivity : ComponentActivity() {

    private val viewModel: BreakViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    // Deliberately ignored, like the pause and block screens.
                }
            },
        )
        setContent {
            UnscrollTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                LaunchedEffect(state.outcome) {
                    when (state.outcome) {
                        BreakOutcome.KEEP_SCROLLING -> openApp(state.packageName)
                        BreakOutcome.DONE -> goHome()
                        null -> Unit
                    }
                }
                BreakScreen(
                    state = state,
                    onKeepScrolling = viewModel::keepScrolling,
                    onDone = viewModel::done,
                )
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations && !isFinishing) {
            viewModel.leftWithoutDeciding()
            finish()
        }
    }

    private fun openApp(packageName: String) {
        packageManager.getLaunchIntentForPackage(packageName)?.let {
            startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        finish()
    }

    private fun goHome() {
        if (isFinishing) return
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        finish()
    }

    companion object {
        const val EXTRA_PACKAGE = "package"
        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_SWIPES = "swipes"
        const val EXTRA_SESSION_MILLIS = "session_millis"

        fun intent(context: Context, packageName: String, sessionId: Long, swipes: Int, sessionMillis: Long): Intent =
            Intent(context, BreakActivity::class.java)
                .putExtra(EXTRA_PACKAGE, packageName)
                .putExtra(EXTRA_SESSION_ID, sessionId)
                .putExtra(EXTRA_SWIPES, swipes)
                .putExtra(EXTRA_SESSION_MILLIS, sessionMillis)
    }
}
