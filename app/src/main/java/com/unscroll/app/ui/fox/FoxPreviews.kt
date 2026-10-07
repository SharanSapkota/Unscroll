package com.unscroll.app.ui.fox

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.unscroll.app.domain.fox.FoxMood
import com.unscroll.app.ui.theme.Dimens
import com.unscroll.app.ui.theme.UnscrollTheme

@Composable
private fun FoxPreview(mood: FoxMood) {
    UnscrollTheme {
        Surface {
            Row(modifier = Modifier.padding(Dimens.spaceL)) {
                FoxMascot(mood = mood, modifier = Modifier.size(Dimens.foxLarge))
                FoxMascot(mood = mood, showTail = false, modifier = Modifier.size(Dimens.foxSmall))
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun FoxHappyPreview() = FoxPreview(FoxMood.HAPPY)

@PreviewLightDark
@Composable
private fun FoxAlertPreview() = FoxPreview(FoxMood.ALERT)

@PreviewLightDark
@Composable
private fun FoxConcernedPreview() = FoxPreview(FoxMood.CONCERNED)

@PreviewLightDark
@Composable
private fun FoxSleepyPreview() = FoxPreview(FoxMood.SLEEPY)
