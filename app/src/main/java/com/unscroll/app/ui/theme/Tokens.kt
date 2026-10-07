package com.unscroll.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Rounded, soft shapes: 20–28 dp for cards and sheets. */
val Shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Spacing and size tokens. Screens use these, never raw dp values. */
object Dimens {
    val spaceXxs = 2.dp
    val spaceXs = 4.dp
    val spaceS = 8.dp
    val spaceM = 12.dp
    val spaceL = 16.dp
    val spaceXl = 24.dp
    val spaceXxl = 32.dp
    val spaceHuge = 48.dp

    /** Horizontal padding of every screen. */
    val screenPadding = 20.dp

    /** Minimum touch target. */
    val touchTarget = 48.dp

    val iconSmall = 18.dp
    val icon = 24.dp
    val appIcon = 40.dp
    val appIconLarge = 64.dp
    val illustration = 96.dp

    val tileWidth = 156.dp
    val progressBar = 6.dp
    val dot = 8.dp
    val dotLarge = 10.dp

    val sparklineHeight = 64.dp
    val chartHeight = 128.dp
    val heatmapHeight = 32.dp
    val heatmapGap = 2.dp
    val barCorner = 6.dp
    val barGap = 6.dp
    val minBar = 3.dp

    val placeholderLine = 16.dp
    val placeholderBlock = 96.dp

    /** Soft card elevation. */
    val elevation = 1.dp
    val sheetMaxWidth = 640.dp

    /** The floating pill. */
    val pillDotSmall = 14.dp
    val pillDotMedium = 18.dp
    val pillFoxSmall = 22.dp
    val pillFoxMedium = 28.dp
    val pillFoxInset = 2.dp
    val pillPaddingHSmall = 10.dp
    val pillPaddingHMedium = 14.dp
    val pillPaddingVSmall = 5.dp
    val pillPaddingVMedium = 7.dp
    val pillShadow = 6.dp
    val pillMessageMaxWidth = 280.dp
    val pillPreviewHeight = 72.dp

    /** The fox mascot. */
    val foxSmall = 44.dp
    val foxHome = 76.dp
    val foxLarge = 120.dp
    val foxBubbleMaxWidth = 220.dp

    /** The breathing circle on the break screen. */
    val breathingCircle = 220.dp
}

/** Animation durations (ms): short for small state changes, medium for screens and sheets. */
object Motion {
    const val SHORT = 200
    const val MEDIUM = 300

    /** The pill's color change between levels: slower, so it isn't startling over another app. */
    const val PILL_COLOR = 600
}
