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
import androidx.compose.material3.MaterialTheme
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
 * The fox mascot: a minimal geometric fox face (and tail) drawn on a Canvas in the accent color.
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
    fur: Color = MaterialTheme.colorScheme.primary,
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
        withTransform({
            translate(dx, dy)
            scale(factor, factor, pivot = Offset.Zero)
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

// The drawing, in a 100 x 100 box for the face (120 wide with the tail).

private fun DrawScope.drawFox(mood: FoxMood, fur: Color, motion: FoxMotion, showTail: Boolean) {
    if (showTail) {
        rotate(motion.tail.value, pivot = Offset(TAIL_PIVOT_X, TAIL_PIVOT_Y)) {
            drawPath(path(TAIL), fur)
            drawPath(path(TAIL_TIP), FoxColors.cream)
        }
    }
    val earDroop = when (mood) {
        FoxMood.HAPPY -> 0f
        FoxMood.ALERT -> -EAR_PERK
        FoxMood.CONCERNED -> EAR_DROOP
        FoxMood.SLEEPY -> EAR_DROOP / 2
    }
    // Ears tilt outward when drooping: the left one counter-clockwise, the right one clockwise.
    rotate(-earDroop, pivot = Offset(LEFT_EAR_PIVOT, EAR_PIVOT_Y)) {
        drawPath(path(LEFT_EAR), fur)
        drawPath(path(LEFT_EAR_INNER), FoxColors.cream)
    }
    rotate(earDroop + motion.earTwitch.value, pivot = Offset(VIEW - LEFT_EAR_PIVOT, EAR_PIVOT_Y)) {
        drawPath(path(mirror(LEFT_EAR)), fur)
        drawPath(path(mirror(LEFT_EAR_INNER)), FoxColors.cream)
    }
    drawPath(path(HEAD), fur)
    drawPath(path(MUZZLE), FoxColors.cream)
    if (mood == FoxMood.HAPPY) {
        drawCircle(FoxColors.blush, radius = CHEEK_RADIUS, center = Offset(CHEEK_X, CHEEK_Y))
        drawCircle(FoxColors.blush, radius = CHEEK_RADIUS, center = Offset(VIEW - CHEEK_X, CHEEK_Y))
    }
    drawPath(path(NOSE), FoxColors.ink)
    drawEyes(mood, motion.blink.value)
    drawMouth(mood)
    if (mood == FoxMood.SLEEPY) {
        // A small "z", in the fur color so it reads on light and dark backgrounds.
        drawPath(path(SNOOZE, closed = false), fur, style = Stroke(LINE, cap = StrokeCap.Round))
    }
}

private fun DrawScope.drawEyes(mood: FoxMood, open: Float) {
    val stroke = Stroke(width = LINE * 1.5f, cap = StrokeCap.Round)
    listOf(EYE_X, VIEW - EYE_X).forEach { x ->
        when (mood) {
            // Happy: curved "^" eyes; a blink flattens them.
            FoxMood.HAPPY -> drawPath(
                Path().apply {
                    moveTo(x - EYE_W, EYE_Y + 2)
                    quadraticTo(x, EYE_Y + 2 - HAPPY_EYE_LIFT * open, x + EYE_W, EYE_Y + 2)
                },
                FoxColors.ink,
                style = stroke,
            )
            // Sleepy: closed, gently curved lids.
            FoxMood.SLEEPY -> drawPath(
                Path().apply {
                    moveTo(x - EYE_W, EYE_Y)
                    quadraticTo(x, EYE_Y + SLEEPY_LID_DROP, x + EYE_W, EYE_Y)
                },
                FoxColors.ink,
                style = stroke,
            )
            // Alert and concerned: open eyes, alert wider, with a highlight.
            FoxMood.ALERT, FoxMood.CONCERNED -> {
                val radius = if (mood == FoxMood.ALERT) ALERT_EYE else CONCERNED_EYE
                val height = radius * 2 * open.coerceAtLeast(BLINK_CLOSED)
                drawOval(
                    FoxColors.ink,
                    topLeft = Offset(x - radius, EYE_Y - height / 2),
                    size = Size(radius * 2, height),
                )
                if (open > HALF) {
                    drawCircle(FoxColors.cream, radius = HIGHLIGHT, center = Offset(x + 1.5f, EYE_Y - 1.5f))
                }
            }
        }
    }
    if (mood == FoxMood.CONCERNED) {
        // Worried brows: the inner ends raised.
        drawLine(FoxColors.ink, Offset(29f, 43f), Offset(41f, 39f), strokeWidth = LINE, cap = StrokeCap.Round)
        drawLine(FoxColors.ink, Offset(71f, 43f), Offset(59f, 39f), strokeWidth = LINE, cap = StrokeCap.Round)
    }
}

private fun DrawScope.drawMouth(mood: FoxMood) {
    val mouth = Path().apply {
        when (mood) {
            FoxMood.HAPPY -> {
                moveTo(44f, 84f)
                quadraticTo(50f, 89f, 56f, 84f)
            }
            FoxMood.CONCERNED -> {
                moveTo(45f, 88f)
                quadraticTo(50f, 84f, 55f, 88f)
            }
            FoxMood.ALERT, FoxMood.SLEEPY -> {
                moveTo(47.5f, 86f)
                lineTo(52.5f, 86f)
            }
        }
    }
    drawPath(mouth, FoxColors.ink, style = Stroke(width = LINE, cap = StrokeCap.Round))
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
    FloatArray(points.size) { i -> if (i % 2 == 0) VIEW - points[i] else points[i] }

private const val VIEW = 100f
private const val VIEW_WITH_TAIL = 120f
private const val LINE = 2.2f
private const val HALF = 0.5f

private val HEAD = floatArrayOf(10f, 40f, 50f, 26f, 90f, 40f, 82f, 68f, 50f, 92f, 18f, 68f)
private val MUZZLE = floatArrayOf(19f, 64f, 36f, 58f, 50f, 67f, 64f, 58f, 81f, 64f, 50f, 91f)
private val LEFT_EAR = floatArrayOf(13f, 46f, 20f, 4f, 46f, 31f)
private val LEFT_EAR_INNER = floatArrayOf(21f, 37f, 23f, 15f, 38f, 30f)
private val NOSE = floatArrayOf(45.5f, 76f, 54.5f, 76f, 50f, 81.5f)

// A curved tail behind the face, bottom right, as a polygon close to a teardrop.
private val TAIL = floatArrayOf(
    78f, 86f, 88f, 88f, 100f, 86f, 110f, 79f, 116f, 70f, 118f, 60f,
    111f, 66f, 103f, 69f, 95f, 70f, 99f, 76f, 93f, 82f, 84f, 84f,
)
private val TAIL_TIP = floatArrayOf(118f, 60f, 116f, 70f, 110f, 79f, 106f, 72f, 111f, 66f)
private val SNOOZE = floatArrayOf(84f, 12f, 92f, 12f, 84f, 20f, 92f, 20f)

private const val TAIL_PIVOT_X = 82f
private const val TAIL_PIVOT_Y = 85f
private const val LEFT_EAR_PIVOT = 26f
private const val EAR_PIVOT_Y = 38f
private const val EAR_DROOP = 20f
private const val EAR_PERK = 4f

private const val EYE_X = 36f
private const val EYE_Y = 50f
private const val EYE_W = 5f
private const val HAPPY_EYE_LIFT = 8f
private const val SLEEPY_LID_DROP = 4f
private const val ALERT_EYE = 5f
private const val CONCERNED_EYE = 4f
private const val HIGHLIGHT = 1.4f
private const val CHEEK_X = 28f
private const val CHEEK_Y = 66f
private const val CHEEK_RADIUS = 5f

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
