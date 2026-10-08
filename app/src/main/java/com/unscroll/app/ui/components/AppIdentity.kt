package com.unscroll.app.ui.components

import android.content.pm.PackageManager
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The installed app's name, or its package name if it can't be read. */
@Composable
fun rememberAppLabel(packageName: String): String {
    val packageManager = LocalContext.current.packageManager
    return remember(packageName) { packageManager.appLabel(packageName) }
}

/**
 * Decoded app icons, shared by every screen, so long lists (the add-apps picker) scroll without
 * decoding the same icon again. Icons are scaled to [ICON_PX] and the cache is capped by size.
 */
private object AppIconCache {
    private const val MAX_BYTES = 8 * 1024 * 1024

    private val cache = object : LruCache<String, ImageBitmap>(MAX_BYTES) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * BYTES_PER_PIXEL
    }

    fun get(packageName: String): ImageBitmap? = cache.get(packageName)

    fun load(packageManager: PackageManager, packageName: String): ImageBitmap? =
        get(packageName) ?: runCatching {
            packageManager.getApplicationIcon(packageName).toBitmap(ICON_PX, ICON_PX).asImageBitmap()
        }.getOrNull()?.also { cache.put(packageName, it) }

    const val ICON_PX = 144
    private const val BYTES_PER_PIXEL = 4
}

/**
 * The installed app's icon; null while loading or if it isn't installed. Decoded off the main
 * thread and cached, so a cached icon shows on the first frame.
 */
@Composable
fun rememberAppIcon(packageName: String): ImageBitmap? {
    val packageManager = LocalContext.current.packageManager
    if (LocalInspectionMode.current) return null
    val icon by produceState(initialValue = AppIconCache.get(packageName), packageName) {
        if (value == null) value = withContext(Dispatchers.IO) { AppIconCache.load(packageManager, packageName) }
    }
    return icon
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
