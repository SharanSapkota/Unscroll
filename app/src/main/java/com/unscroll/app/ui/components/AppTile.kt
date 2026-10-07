package com.unscroll.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.unscroll.app.ui.theme.Dimens

/**
 * A tracked app on Home: icon, name, its time (big), swipes, and a thin bar toward its daily
 * limit when it has one. Tapping it opens the app's detail page.
 */
@Composable
fun AppTile(
    packageName: String,
    time: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    swipes: String? = null,
    limitProgress: Float? = null,
    status: String? = null,
) {
    UnscrollCard(modifier = modifier.width(Dimens.tileWidth), onClick = onClick) {
        Column(
            modifier = Modifier.padding(Dimens.spaceL),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(packageName)
                Spacer(Modifier.width(Dimens.spaceS))
                Text(
                    text = rememberAppLabel(packageName),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(Dimens.spaceXs))
            Text(text = time, style = MaterialTheme.typography.headlineSmall, maxLines = 1)
            Text(
                text = swipes ?: status.orEmpty(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            if (limitProgress != null) {
                Spacer(Modifier.height(Dimens.spaceXs))
                LimitBar(progress = limitProgress)
            }
        }
    }
}
