package com.unscroll.app.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.unscroll.app.R
import com.unscroll.app.ui.theme.Dimens

/**
 * A small section title. With [info], an (i) button opens a bottom sheet with the help text,
 * so the screen itself stays free of paragraphs.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    info: String? = null,
    trailing: @Composable () -> Unit = {},
) {
    var showInfo by rememberSaveable { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = Dimens.spaceXs, top = Dimens.spaceS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (info != null) InfoButton(onClick = { showInfo = true })
        trailing()
    }
    if (showInfo && info != null) {
        InfoSheet(title = title, body = info, onDismiss = { showInfo = false })
    }
}

/** The (i) icon button, 48 dp. */
@Composable
fun InfoButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            painter = painterResource(R.drawable.ic_info),
            contentDescription = stringResource(R.string.cd_more_info),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(Dimens.iconSmall),
        )
    }
}
