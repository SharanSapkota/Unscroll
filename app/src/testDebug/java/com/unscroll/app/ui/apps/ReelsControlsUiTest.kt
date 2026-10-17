package com.unscroll.app.ui.apps

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.unscroll.app.R
import com.unscroll.app.data.reels.ReelsToday
import com.unscroll.app.domain.blocking.BlockDecision
import com.unscroll.app.domain.blocking.LimitSettings
import com.unscroll.app.domain.reels.ReelsTimerStatus
import com.unscroll.app.domain.reels.TimerVisibility
import com.unscroll.app.ui.theme.UnscrollTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Reels-specific controls appear for Instagram and TikTok only: the Apps tab's Reels line, and on
 * App detail "Timer visibility" and the fail-open note. Every other app shows whole-app controls,
 * even if its state carried Reels data.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "w411dp-h2400dp")
class ReelsControlsUiTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val reelsToday = ReelsToday(millis = 12 * 60_000L, swipes = 86)

    private fun row(packageName: String) = AppRowState(
        packageName = packageName,
        settings = LimitSettings(dailyLimitMinutes = 30),
        decision = BlockDecision.Allowed(remainingMillis = 18 * 60_000L),
        todayMillis = 20 * 60_000L,
        label = packageName,
        reels = reelsToday,
    )

    @Test
    fun appsTab_showsTheReelsLine_onlyForInstagramAndTikTok() {
        compose.setContent {
            UnscrollTheme {
                AppsContent(
                    state = AppsUiState(
                        isLoading = false,
                        apps = listOf(INSTAGRAM, TIKTOK, YOUTUBE, REDDIT).map(::row),
                    ),
                    onOpenApp = {},
                    onOpenPlus = {},
                    onAddApps = {},
                    onBlock = { _, _ -> },
                    onBlockAll = {},
                )
            }
        }
        compose.onAllNodesWithTag(TAG_REELS_LINE, useUnmergedTree = true).assertCountEquals(2)
    }

    private fun detail(packageName: String, availability: ReelsTimerStatus = ReelsTimerStatus.ACTIVE) = AppDetailUiState(
        packageName = packageName,
        isLoading = false,
        settings = LimitSettings(dailyLimitMinutes = 30),
        scrollCountingActive = true,
        reels = ReelsTimerUi(
            forYou = false,
            visibility = TimerVisibility.ALWAYS,
            availability = availability,
            todayMillis = reelsToday.millis,
            todaySwipes = reelsToday.swipes,
        ),
    )

    private val shown = mutableStateOf<AppDetailUiState?>(null)

    /** App detail for [state], with Advanced options open. Content is set once per test. */
    private fun showDetail(state: AppDetailUiState) {
        val first = shown.value == null
        shown.value = state
        if (first) {
            compose.setContent {
                UnscrollTheme { shown.value?.let { AppDetailContent(stateIn = it, actions = DetailActions(), onBack = {}) } }
            }
            compose.onNodeWithText(context.getString(R.string.advanced_options)).performClick()
        }
        compose.waitForIdle()
    }

    @Test
    fun appDetail_instagram_hasTimerVisibility_andBothLimits() {
        showDetail(detail(INSTAGRAM))
        compose.onAllNodesWithTag(TAG_TIMER_VISIBILITY, useUnmergedTree = true).assertCountEquals(1)
        compose.onNodeWithTag(TAG_TIME_LIMIT, useUnmergedTree = true).assertExists()
        compose.onNodeWithTag(TAG_SWIPE_LIMIT, useUnmergedTree = true).assertExists()
    }

    @Test
    fun appDetail_otherApps_haveNoReelsControls() {
        listOf(YOUTUBE, REDDIT, FACEBOOK).forEach { pkg ->
            showDetail(detail(pkg, availability = ReelsTimerStatus.UNSUPPORTED_VERSION))
            compose.onAllNodesWithTag(TAG_TIMER_VISIBILITY, useUnmergedTree = true).assertCountEquals(0)
            compose.onAllNodesWithTag(TAG_REELS_NOTE, useUnmergedTree = true).assertCountEquals(0)
            // Whole-app limits are still there.
            compose.onNodeWithTag(TAG_TIME_LIMIT, useUnmergedTree = true).assertExists()
        }
    }

    @Test
    fun appDetail_failOpenNote_onlyWhenDetectionIsUnavailable() {
        showDetail(detail(TIKTOK, availability = ReelsTimerStatus.UNSUPPORTED_VERSION))
        compose.onAllNodesWithTag(TAG_REELS_NOTE, useUnmergedTree = true).assertCountEquals(1)
    }

    private companion object {
        const val INSTAGRAM = "com.instagram.android"
        const val TIKTOK = "com.zhiliaoapp.musically"
        const val YOUTUBE = "com.google.android.youtube"
        const val FACEBOOK = "com.facebook.katana"
        const val REDDIT = "com.reddit.frontpage"
    }
}
