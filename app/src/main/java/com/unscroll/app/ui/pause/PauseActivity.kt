package com.unscroll.app.ui.pause

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
import com.unscroll.app.data.friction.PauseOutcome
import com.unscroll.app.ui.theme.UnscrollTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * The "mindful gate": a breathing pause before a tracked app opens. Opened by FrictionCoordinator
 * on top of the home screen. Back does nothing; Home always works, and leaving without choosing
 * counts as "Never mind".
 */
@AndroidEntryPoint
class PauseActivity : ComponentActivity() {

    private val viewModel: PauseViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    // Deliberately ignored, like the block screen.
                }
            },
        )
        setContent {
            UnscrollTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                LaunchedEffect(state.outcome) {
                    when (state.outcome) {
                        PauseOutcome.CONTINUED -> openApp(state.packageName)
                        PauseOutcome.ABANDONED -> goHome()
                        null -> Unit
                    }
                }
                PauseScreen(
                    state = state,
                    onContinue = viewModel::continueToApp,
                    onNeverMind = viewModel::neverMind,
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
        const val EXTRA_SECONDS = "seconds"
        const val EXTRA_SHOWN_AT = "shown_at"

        fun intent(context: Context, packageName: String, seconds: Int, shownAt: Long): Intent =
            Intent(context, PauseActivity::class.java)
                .putExtra(EXTRA_PACKAGE, packageName)
                .putExtra(EXTRA_SECONDS, seconds)
                .putExtra(EXTRA_SHOWN_AT, shownAt)
    }
}
