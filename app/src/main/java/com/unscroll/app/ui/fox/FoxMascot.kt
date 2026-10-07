package com.unscroll.app.ui.fox

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.unscroll.app.domain.fox.FoxMood
import com.unscroll.app.ui.theme.FoxColors

/**
 * The fox mascot: the launcher icon's geometric fox face (assets/icon/icon.svg), with an optional
 * tail, drawn on a Canvas. Orange fur, darker inner ears, cream lower face, dark eyes and nose.
 * This file holds every part of the drawing, so it can be swapped for commissioned artwork by
 * replacing [drawFox] and keeping this API.
 *
 * With [animate], the fox blinks, twitches an ear and wags its tail, slowly. Animations stop
 * while the screen isn't resumed (no battery use in the background) and when the system
 * "Remove animations" setting is on.
 */
@Composable
fun FoxMascot(
    mood: FoxMood,
    modifier: Modifier = Modifier,
    showTail: Boolean = true,
    animate: Boolean = true,
    fur: Color = FoxColors.fur,
    contentDescription: String? = null,
) {
    val motion = rememberFoxMotion(animate)
    Canvas(
        modifier = modifier.clearAndSetSemantics {
            if (contentDescription != null) this.contentDescription = contentDescription
        },
    ) {
        val viewportWidth = if (showTail) VIEW_WITH_TAIL else VIEW
        val factor = minOf(size.width / viewportWidth, size.height / VIEW)
        val dx = (size.width - viewportWidth * factor) / 2
        val dy = (size.height - VIEW * factor) / 2
        // The drawing uses the icon's own 512 x 512 coordinates; show the box around the face.
        withTransform({
            translate(dx, dy)
            scale(factor, factor, pivot = Offset.Zero)
            translate(-VIEW_LEFT, -VIEW_TOP)
        }) {
            drawFox(mood, fur, motion, showTail)
        }
    }
}

/** Idle motion: eye openness (1 = open), right-ear twitch and tail angle, in degrees. */
private class FoxMotion(val blink: State<Float>, val earTwitch: State<Float>, val tail: State<Float>)

private val Still = FoxMotion(mutableFloatStateOf(1f), mutableFloatStateOf(0f), mutableFloatStateOf(0f))

@Composable
private fun rememberFoxMotion(animate: Boolean): FoxMotion {
    val context = LocalContext.current
    val animationsOff = remember(context) { systemAnimationsOff(context) }
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val visible = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    if (!animate || animationsOff || !visible || LocalInspectionMode.current) return Still
    val transition = rememberInfiniteTransition(label = "fox")
    return FoxMotion(
        blink = transition.blink(),
        earTwitch = transition.earTwitch(),
        tail = transition.animateFloat(
            initialValue = -TAIL_SWING,
            targetValue = TAIL_SWING,
            animationSpec = infiniteRepeatable(tween(TAIL_MILLIS, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "tail",
        ),
    )
}

@Composable
private fun InfiniteTransition.blink(): State<Float> = animateFloat(
    initialValue = 1f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
        keyframes {
            durationMillis = BLINK_CYCLE_MILLIS
            1f at BLINK_AT
            BLINK_CLOSED at BLINK_AT + BLINK_STEP
            1f at BLINK_AT + BLINK_STEP * 2
        },
    ),
    label = "blink",
)

@Composable
private fun InfiniteTransition.earTwitch(): State<Float> = animateFloat(
    initialValue = 0f,
    targetValue = 0f,
    animationSpec = infiniteRepeatable(
        keyframes {
            durationMillis = EAR_CYCLE_MILLIS
            0f at EAR_AT
            -EAR_TWITCH at EAR_AT + EAR_STEP
            0f at EAR_AT + EAR_STEP * 2
            -EAR_TWITCH / 2 at EAR_AT + EAR_STEP * 3
            0f at EAR_AT + EAR_STEP * 4
        },
    ),
    label = "ear",
)

/** The system "Remove animations" switch sets the animator scale to 0. */
private fun systemAnimationsOff(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

// The drawing, in the launcher icon's 512 x 512 coordinates (its fox group, before the icon's own
// placement). The face fills VIEW x VIEW from (VIEW_LEFT, VIEW_TOP); the tail adds room on the right.

private fun DrawScope.drawFox(mood: FoxMood, fur: Color, motion: FoxMotion, showTail: Boolean) {
    if (showTail) {
        rotate(motion.tail.value, pivot = Offset(TAIL_PIVOT_X, TAIL_PIVOT_Y)) {
            drawPath(tailPath(), fur)
            drawPath(tailTipPath(), FoxColors.cream)
        }
    }
    val earDroop = when (mood) {
        FoxMood.HAPPY -> 0f
        FoxMood.ALERT -> -EAR_PERK
        FoxMood.CONCERNED -> EAR_DROOP
        FoxMood.SLEEPY -> EAR_DROOP / 2
    }
    // The ears are separate from the face so they can droop: the left one turns counter-clockwise,
    // the right one clockwise. Each overlaps the face a little, so no gap opens at its base.
    rotate(-earDroop, pivot = Offset(EAR_PIVOT_X, EAR_PIVOT_Y)) {
        drawPath(path(LEFT_EAR), fur)
        drawPath(path(LEFT_EAR_INNER), FoxColors.innerEar)
    }
    rotate(earDroop + motion.earTwitch.value, pivot = Offset(ICON - EAR_PIVOT_X, EAR_PIVOT_Y)) {
        drawPath(path(mirror(LEFT_EAR)), fur)
        drawPath(path(mirror(LEFT_EAR_INNER)), FoxColors.innerEar)
    }
    drawPath(facePath(), fur)
    drawPath(lowerFacePath(), FoxColors.cream)
    if (mood == FoxMood.HAPPY) {
        drawCircle(FoxColors.blush, radius = CHEEK_RADIUS, center = Offset(CHEEK_X, CHEEK_Y))
        drawCircle(FoxColors.blush, radius = CHEEK_RADIUS, center = Offset(ICON - CHEEK_X, CHEEK_Y))
    }
    drawOval(
        FoxColors.ink,
        topLeft = Offset(CENTER_X - NOSE_RX, NOSE_Y - NOSE_RY),
        size = Size(NOSE_RX * 2, NOSE_RY * 2),
    )
    drawEyes(mood, motion.blink.value)
    drawMouth(mood)
    if (mood == FoxMood.SLEEPY) {
        // A small "z" between the ears, in the fur color.
        drawPath(path(SNOOZE, closed = false), fur, style = Stroke(LINE, cap = StrokeCap.Round))
    }
}

/** A bushy tail curling up behind the right cheek. */
private fun tailPath(): Path = Path().apply {
    moveTo(330f, 405f)
    quadraticTo(440f, 420f, 482f, 330f)
    quadraticTo(505f, 270f, TAIL_TIP_X, TAIL_TIP_Y)
    quadraticTo(462f, 275f, 420f, 300f)
    quadraticTo(380f, 322f, 350f, 330f)
    close()
}

/** The tail's cream tip. */
private fun tailTipPath(): Path = Path().apply {
    moveTo(TAIL_TIP_X, TAIL_TIP_Y)
    quadraticTo(503f, 262f, 490f, 300f)
    quadraticTo(470f, 290f, 452f, 282f)
    quadraticTo(475f, 255f, TAIL_TIP_X, TAIL_TIP_Y)
    close()
}

/** The head without the ears: the icon's head outline, cut at the base of each ear. */
private fun facePath(): Path = Path().apply {
    moveTo(EAR_OUTER_BASE_X, EAR_OUTER_BASE_Y)
    lineTo(200f, 190f)
    quadraticTo(CENTER_X, 176f, 312f, 190f)
    lineTo(ICON - EAR_OUTER_BASE_X, EAR_OUTER_BASE_Y)
    lineTo(402f, 272f)
    quadraticTo(392f, 342f, CENTER_X, CHIN_Y)
    quadraticTo(120f, 342f, 110f, 272f)
    close()
}

/** The cream lower face, as in the icon. */
private fun lowerFacePath(): Path = Path().apply {
    moveTo(110f, 272f)
    quadraticTo(150f, 332f, CENTER_X, CHIN_Y)
    quadraticTo(362f, 332f, 402f, 272f)
    quadraticTo(332f, 304f, CENTER_X, 304f)
    quadraticTo(180f, 304f, 110f, 272f)
    close()
}

private fun DrawScope.drawEyes(mood: FoxMood, open: Float) {
    val stroke = Stroke(width = LINE * 1.3f, cap = StrokeCap.Round)
    listOf(EYE_X, ICON - EYE_X).forEach { x ->
        when (mood) {
            // Happy: curved "^" eyes; a blink flattens them.
            FoxMood.HAPPY -> drawPath(
                Path().apply {
                    moveTo(x - EYE_HALF_WIDTH, EYE_Y + EYE_ARC_BASE)
                    quadraticTo(x, EYE_Y + EYE_ARC_BASE - HAPPY_EYE_LIFT * open, x + EYE_HALF_WIDTH, EYE_Y + EYE_ARC_BASE)
                },
                FoxColors.ink,
                style = stroke,
            )
            // Sleepy: closed, gently curved lids.
            FoxMood.SLEEPY -> drawPath(
                Path().apply {
                    moveTo(x - EYE_HALF_WIDTH, EYE_Y)
                    quadraticTo(x, EYE_Y + SLEEPY_LID_DROP, x + EYE_HALF_WIDTH, EYE_Y)
                },
                FoxColors.ink,
                style = stroke,
            )
            // Alert: the icon's open eyes. Concerned: a little smaller, under worried brows.
            FoxMood.ALERT, FoxMood.CONCERNED -> {
                val scale = if (mood == FoxMood.ALERT) 1f else CONCERNED_EYE_SCALE
                val rx = EYE_RX * scale
                val ry = EYE_RY * scale * open.coerceAtLeast(BLINK_CLOSED)
                drawOval(FoxColors.ink, topLeft = Offset(x - rx, EYE_Y - ry), size = Size(rx * 2, ry * 2))
                if (open > HALF) {
                    drawCircle(
                        FoxColors.highlight,
                        radius = HIGHLIGHT * scale,
                        center = Offset(x + HIGHLIGHT_DX * scale, EYE_Y - HIGHLIGHT_DY * scale),
                    )
                }
            }
        }
    }
    if (mood == FoxMood.CONCERNED) {
        // Worried brows: the inner ends raised.
        drawLine(FoxColors.ink, Offset(170f, 222f), Offset(208f, 208f), strokeWidth = LINE, cap = StrokeCap.Round)
        drawLine(FoxColors.ink, Offset(342f, 222f), Offset(304f, 208f), strokeWidth = LINE, cap = StrokeCap.Round)
    }
}

/** A small mouth under the nose: a smile when happy, a frown when concerned, none otherwise. */
private fun DrawScope.drawMouth(mood: FoxMood) {
    val mouth = when (mood) {
        FoxMood.HAPPY -> Path().apply {
            moveTo(242f, 409f)
            quadraticTo(CENTER_X, 417f, 270f, 409f)
        }
        FoxMood.CONCERNED -> Path().apply {
            moveTo(244f, 415f)
            quadraticTo(CENTER_X, 408f, 268f, 415f)
        }
        FoxMood.ALERT, FoxMood.SLEEPY -> return
    }
    drawPath(mouth, FoxColors.ink, style = Stroke(width = LINE * 0.8f, cap = StrokeCap.Round))
}

/** A polygon from x, y pairs, closed unless [closed] is false. */
private fun path(points: FloatArray, closed: Boolean = true): Path = Path().apply {
    moveTo(points[0], points[1])
    var i = 2
    while (i < points.size) {
        lineTo(points[i], points[i + 1])
        i += 2
    }
    if (closed) close()
}

/** The same polygon mirrored around the face's vertical center line. */
private fun mirror(points: FloatArray): FloatArray =
    FloatArray(points.size) { i -> if (i % 2 == 0) ICON - points[i] else points[i] }

// Coordinates: the icon's 512-unit space.
private const val ICON = 512f
private const val CENTER_X = ICON / 2
private const val VIEW_LEFT = 80f
private const val VIEW_TOP = 95f
private const val VIEW = 352f
private const val VIEW_WITH_TAIL = 422f
private const val LINE = 7.7f
private const val HALF = 0.5f
private const val CHIN_Y = 422f

// Left ear: tip, inner base on the head's top edge, a point inside the face, and a point down the
// head's side, so a tilted ear never opens a notch at its base. The face starts at the outer base.
private const val EAR_OUTER_BASE_X = 107f
private const val EAR_OUTER_BASE_Y = 236f
private val LEFT_EAR = floatArrayOf(96f, 120f, 200f, 190f, 175f, 250f, 109f, 262f)
private val LEFT_EAR_INNER = floatArrayOf(132f, 166f, 190f, 206f, 128f, 238f)
private const val EAR_PIVOT_X = 153f
private const val EAR_PIVOT_Y = 213f
private const val EAR_DROOP = 18f
private const val EAR_PERK = 4f

private const val TAIL_TIP_X = 488f
private const val TAIL_TIP_Y = 215f
private const val TAIL_PIVOT_X = 360f
private const val TAIL_PIVOT_Y = 390f
private val SNOOZE = floatArrayOf(300f, 112f, 330f, 112f, 300f, 142f, 330f, 142f)

private const val EYE_X = 192f
private const val EYE_Y = 252f
private const val EYE_RX = 15f
private const val EYE_RY = 19f
private const val EYE_HALF_WIDTH = 17f
private const val EYE_ARC_BASE = 7f
private const val HAPPY_EYE_LIFT = 26f
private const val SLEEPY_LID_DROP = 13f
private const val CONCERNED_EYE_SCALE = 0.8f
private const val HIGHLIGHT = 5f
private const val HIGHLIGHT_DX = 5f
private const val HIGHLIGHT_DY = 7f
private const val NOSE_Y = 392f
private const val NOSE_RX = 22f
private const val NOSE_RY = 15f
private const val CHEEK_X = 152f
private const val CHEEK_Y = 292f
private const val CHEEK_RADIUS = 17f

private const val BLINK_CYCLE_MILLIS = 4_200
private const val BLINK_AT = 3_800
private const val BLINK_STEP = 90
private const val BLINK_CLOSED = 0.1f
private const val EAR_CYCLE_MILLIS = 6_500
private const val EAR_AT = 5_600
private const val EAR_STEP = 120
private const val EAR_TWITCH = 9f
private const val TAIL_MILLIS = 2_600
private const val TAIL_SWING = 6f
