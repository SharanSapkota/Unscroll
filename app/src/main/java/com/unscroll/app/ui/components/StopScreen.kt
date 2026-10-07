package com.unscroll.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.unscroll.app.domain.fox.FoxMood
import com.unscroll.app.ui.fox.FoxMascot
import com.unscroll.app.ui.fox.LocalFoxSettings
import com.unscroll.app.ui.theme.Dimens

/**
 * The block screen and the swipe-limit cover: a calm dark page with a concerned fox, the app, one bold headline,
 * the key number in the accent color, and one big "Go home". No paragraphs. [secondary] holds
 * an optional quiet action ("I need access").
 */
@Composable
fun StopScreen(
    packageName: String,
    headline: String,
    number: String,
    numberLabel: String,
    goHomeLabel: String,
    onGoHome: () -> Unit,
    modifier: Modifier = Modifier,
    secondary: @Composable ColumnScope.() -> Unit = {},
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(Dimens.spaceXl),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceL, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // A concerned fox above the headline (unless the user hid the fox). Still, no idle motion.
            if (LocalFoxSettings.current.showFox) {
                FoxMascot(
                    mood = FoxMood.CONCERNED,
                    animate = false,
                    modifier = Modifier.size(Dimens.foxLarge),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.spaceS)) {
                AppIcon(packageName, size = Dimens.icon + Dimens.spaceS)
                Text(
                    text = rememberAppLabel(packageName),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = headline,
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Text(text = number, style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary)
            Text(
                text = numberLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Dimens.spaceXl))
            Button(
                onClick = onGoHome,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Dimens.touchTarget + Dimens.spaceS),
                shape = MaterialTheme.shapes.large,
            ) {
                Text(text = goHomeLabel, style = MaterialTheme.typography.titleMedium)
            }
            secondary()
        }
    }
}
