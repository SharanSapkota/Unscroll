package com.unscroll.app.ui.home

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.core.content.FileProvider
import com.unscroll.app.R
import com.unscroll.app.domain.fox.FoxMood
import com.unscroll.app.domain.insights.Insight
import com.unscroll.app.domain.insights.InsightCategory
import com.unscroll.app.domain.insights.InsightTone
import com.unscroll.app.ui.components.UnscrollSheet
import com.unscroll.app.ui.fox.FoxMascot
import com.unscroll.app.ui.fox.LocalFoxSettings
import com.unscroll.app.ui.theme.Dimens
import com.unscroll.app.ui.theme.ShareCardColors
import com.unscroll.app.ui.theme.UnscrollTheme
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Share an insight as an image. The sheet shows exactly the picture that will be shared; "Share"
 * draws it into a bitmap on the phone, saves it in the app's cache and opens the system share
 * sheet through the FileProvider. Unscroll uploads nothing: the user picks where it goes.
 */
@Composable
internal fun InsightShareSheet(insight: Insight, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val layer = rememberGraphicsLayer()
    var busy by remember { mutableStateOf(false) }
    val chooserTitle = stringResource(R.string.insights_share_chooser)
    val failed = stringResource(R.string.insights_share_failed)
    UnscrollSheet(title = stringResource(R.string.insights_share_title), onDismiss = onDismiss) {
        Box(
            modifier = Modifier.drawWithContent {
                layer.record { this@drawWithContent.drawContent() }
                drawLayer(layer)
            },
        ) {
            InsightShareImage(insight)
        }
        Text(
            text = stringResource(R.string.insights_share_note),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = {
                busy = true
                scope.launch {
                    val shared = try {
                        val bitmap = layer.toImageBitmap().asAndroidBitmap()
                        shareImage(context, bitmap, chooserTitle)
                        true
                    } catch (e: IOException) {
                        Log.w(TAG, "Could not share the insight", e)
                        false
                    } catch (e: IllegalArgumentException) {
                        // FileProvider path or an empty layer.
                        Log.w(TAG, "Could not share the insight", e)
                        false
                    }
                    busy = false
                    if (shared) onDismiss() else Toast.makeText(context, failed, Toast.LENGTH_SHORT).show()
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(painter = painterResource(R.drawable.ic_share), contentDescription = null, modifier = Modifier.size(Dimens.iconSmall))
            Spacer(modifier = Modifier.width(Dimens.spaceS))
            Text(stringResource(R.string.insights_share))
        }
    }
}

/** The picture: fox, big number, the line, and a small "Unscroll". Fixed colors, any theme. */
@Composable
internal fun InsightShareImage(insight: Insight, modifier: Modifier = Modifier) {
    val fox = LocalFoxSettings.current.showFox
    val share = insight.share
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(ShareCardColors.background)
            .padding(Dimens.spaceXl),
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceM),
    ) {
        if (fox) FoxMascot(mood = insight.mood, showTail = true, animate = false, modifier = Modifier.size(Dimens.foxShare))
        share?.number?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = ShareCardColors.number,
            )
        }
        Text(text = share?.line ?: insight.text, style = MaterialTheme.typography.titleMedium, color = ShareCardColors.text)
        Text(
            text = stringResource(R.string.insights_share_brand),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = ShareCardColors.muted,
        )
    }
}

/** Saves [bitmap] in the cache (one file, replaced each time) and opens the share sheet. */
private suspend fun shareImage(context: Context, bitmap: Bitmap, chooserTitle: String) {
    val file = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, SHARE_DIR).apply { mkdirs() }
        // A layer may give a hardware bitmap; encode a software copy.
        val pixels = if (bitmap.config == Bitmap.Config.HARDWARE) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            bitmap
        }
        File(dir, SHARE_FILE).also { file ->
            FileOutputStream(file).use { out ->
                if (!pixels.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, out)) throw IOException("PNG encoding failed")
            }
        }
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("image/png")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    send.clipData = ClipData.newRawUri(null, uri)
    context.startActivity(Intent.createChooser(send, chooserTitle))
}

private const val TAG = "Insights"

/** Must match res/xml/file_paths.xml. */
private const val SHARE_DIR = "shared"
private const val SHARE_FILE = "unscroll-insight.png"
private const val PNG_QUALITY = 100

@PreviewLightDark
@Composable
private fun InsightShareImagePreview() {
    UnscrollTheme {
        InsightShareImage(
            Insight(
                templateId = "T01",
                category = InsightCategory.THUMB,
                tone = InsightTone.HONEST,
                text = "Your thumb scrolled 1.4 km today. That's 4 Eiffel Towers stacked up.",
                highlight = "1.4 km",
                mood = FoxMood.ALERT,
            ),
        )
    }
}
