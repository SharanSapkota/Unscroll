package com.unscroll.app.ui.fox

import androidx.annotation.ArrayRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.Popup
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.unscroll.app.R
import com.unscroll.app.domain.fox.FoxMood
import com.unscroll.app.domain.fox.FoxSettings
import com.unscroll.app.ui.theme.Dimens
import com.unscroll.app.ui.theme.Motion
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow

/** The user's fox settings (Settings › Appearance), provided at the top of each window. */
val LocalFoxSettings = compositionLocalOf { FoxSettings() }

/**
 * Provides [LocalFoxSettings] from the saved settings, so switches in Settings apply at once.
 * Uses plain `collectAsState`: overlay windows have no activity lifecycle to pause on.
 */
@Composable
fun ProvideFoxSettings(settings: Flow<FoxSettings>, content: @Composable () -> Unit) {
    val current by settings.collectAsState(initial = FoxSettings())
    CompositionLocalProvider(LocalFoxSettings provides current, content = content)
}

/**
 * The fox in a screen corner, in the mood today's usage calls for. With messages on, a tap shows
 * a speech bubble with one short line for that mood (never the same twice in a row), which goes
 * away by itself. Nothing at all when the user hid the fox.
 */
@Composable
fun FoxCorner(
    size: Dp,
    modifier: Modifier = Modifier,
    showTail: Boolean = true,
    viewModel: FoxViewModel = hiltViewModel(),
) {
    val settings = LocalFoxSettings.current
    if (!settings.showFox) return
    val mood by viewModel.mood.collectAsStateWithLifecycle()
    val messages = stringArrayResource(mood.messagesRes)
    var message by remember { mutableStateOf<String?>(null) }
    // Bumped on every tap, so a new message restarts the timer.
    var shownAt by remember { mutableIntStateOf(0) }
    LaunchedEffect(shownAt) {
        if (message != null) {
            delay(BUBBLE_MILLIS)
            message = null
        }
    }
    LaunchedEffect(settings.messages) { if (!settings.messages) message = null }
    val description = stringResource(
        if (settings.messages) R.string.fox_description_tap else R.string.fox_description,
        stringResource(mood.labelRes),
    )
    Box(modifier = modifier) {
        FoxMascot(
            mood = mood,
            showTail = showTail,
            contentDescription = description,
            modifier = Modifier
                .size(size)
                .then(
                    if (settings.messages) {
                        Modifier.clickable(role = Role.Button) {
                            message = messages[viewModel.nextMessage(mood, messages.size)]
                            shownAt++
                        }
                    } else {
                        Modifier
                    },
                ),
        )
        message?.let { SpeechBubble(text = it, foxSize = size) }
    }
}

@Composable
private fun SpeechBubble(text: String, foxSize: Dp) {
    val visible = remember { MutableTransitionState(false) }.apply { targetState = true }
    Popup(alignment = Alignment.TopEnd) {
        Box(modifier = Modifier.padding(top = foxSize)) {
            AnimatedVisibility(
                visibleState = visible,
                enter = fadeIn(tween(Motion.SHORT)) + scaleIn(tween(Motion.SHORT), initialScale = BUBBLE_START_SCALE),
                exit = fadeOut(tween(Motion.SHORT)) + scaleOut(tween(Motion.SHORT)),
            ) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shadowElevation = Dimens.elevation * 3,
                    modifier = Modifier.widthIn(max = Dimens.foxBubbleMaxWidth),
                ) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .padding(horizontal = Dimens.spaceL, vertical = Dimens.spaceM)
                            .semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
        }
    }
}

/** Twenty-plus short lines per mood, in strings.xml. */
@get:ArrayRes
val FoxMood.messagesRes: Int
    get() = when (this) {
        FoxMood.HAPPY -> R.array.fox_messages_happy
        FoxMood.ALERT -> R.array.fox_messages_alert
        FoxMood.CONCERNED -> R.array.fox_messages_concerned
        FoxMood.SLEEPY -> R.array.fox_messages_sleepy
    }

val FoxMood.labelRes: Int
    get() = when (this) {
        FoxMood.HAPPY -> R.string.fox_mood_happy
        FoxMood.ALERT -> R.string.fox_mood_alert
        FoxMood.CONCERNED -> R.string.fox_mood_concerned
        FoxMood.SLEEPY -> R.string.fox_mood_sleepy
    }

private const val BUBBLE_MILLIS = 4_500L
private const val BUBBLE_START_SCALE = 0.8f
