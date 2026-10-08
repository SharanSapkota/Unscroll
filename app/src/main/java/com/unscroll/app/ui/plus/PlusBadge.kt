package com.unscroll.app.ui.plus

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.unscroll.app.R
import com.unscroll.app.ui.theme.Dimens

/** A small "Plus" label on paused apps. */
@Composable
fun PlusBadge(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.plus_badge),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = modifier
            .background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.small)
            .padding(horizontal = Dimens.spaceS, vertical = Dimens.spaceXxs),
    )
}
