package com.unscroll.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Dp
import androidx.core.graphics.drawable.toBitmap
import com.unscroll.app.ui.theme.Dimens
import com.unscroll.app.util.appLabel

/** The installed app's name, or its package name if it can't be read. */
@Composable
fun rememberAppLabel(packageName: String): String {
    val packageManager = LocalContext.current.packageManager
    return remember(packageName) { packageManager.appLabel(packageName) }
}

/** The installed app's icon, loaded once per package; null if it isn't installed. */
@Composable
fun rememberAppIcon(packageName: String): ImageBitmap? {
    val packageManager = LocalContext.current.packageManager
    if (LocalInspectionMode.current) return null
    return remember(packageName) {
        runCatching { packageManager.getApplicationIcon(packageName).toBitmap().asImageBitmap() }.getOrNull()
    }
}

/**
 * The app's icon, or a circle with its first letter when the icon can't be loaded (previews,
 * uninstalled apps). Decorative: the name is always shown next to it.
 */
@Composable
fun AppIcon(packageName: String, modifier: Modifier = Modifier, size: Dp = Dimens.appIcon) {
    val icon = rememberAppIcon(packageName)
    if (icon != null) {
        Image(bitmap = icon, contentDescription = null, modifier = modifier.size(size).clip(CircleShape))
        return
    }
    val label = rememberAppLabel(packageName)
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label.take(1).uppercase(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}
