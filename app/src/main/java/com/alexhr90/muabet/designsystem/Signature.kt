package com.alexhr90.muabet.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin

/** True while the hosting lifecycle is resumed, so ambient animation stops off screen. */
@Composable
fun rememberIsResumed(): Boolean {
    val owner = LocalLifecycleOwner.current
    var resumed by remember { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, _ ->
            resumed = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return resumed
}

/**
 * Muabet's signature, the sibling of Jazol's weave, Oko's iris and Cena's ledger: the spring.
 * Fine lines rise from one point at the bottom and spread outward into streams that sway
 * slowly, like water welling up and running off.
 *
 * Decorative only: it never shows real data. [activity] (0..1) quickens it while a refresh
 * runs. Hidden from accessibility, about 30 fps only while resumed, a still frame under reduced
 * motion.
 */
@Composable
fun Spring(
    modifier: Modifier = Modifier,
    activity: Float = 0f,
    color: Color = MaterialTheme.extended.weave,
    lines: Int = 17,
) {
    val reduced = LocalReducedMotion.current
    val resumed = rememberIsResumed()
    val animate = !reduced && resumed
    val energy by animateFloatAsState(activity.coerceIn(0f, 1f), tween(1000), label = "springEnergy")

    var phase by remember { mutableFloatStateOf(0.6f) }
    LaunchedEffect(animate) {
        if (!animate) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last == 0L) last = now
                val elapsed = now - last
                if (elapsed >= FRAME_NANOS) {
                    phase += (elapsed / 1_000_000_000f) * 0.45f * (1f + 1.8f * energy)
                    last = now
                }
            }
        }
    }

    val path = remember { Path() }
    Canvas(modifier.clearAndSetSemantics { }) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        val stroke = Stroke(width = 0.9.dp.toPx(), cap = StrokeCap.Butt, join = StrokeJoin.Round)
        val cx = w / 2f
        val baseY = h * 1.02f
        val steps = 40
        val sway = w * (0.018f + 0.02f * energy)
        val p = phase
        for (j in 0 until lines) {
            // -1..1 across the fan; the outer streams bend further and lie lower.
            val a = if (lines == 1) 0f else j / (lines - 1f) * 2f - 1f
            path.reset()
            for (i in 0..steps) {
                val t = i / steps.toFloat()
                val spread = a * w * 0.52f * t.pow(1.35f)
                val rise = h * (1.02f - (0.95f - 0.35f * a * a) * (1f - (1f - t) * (1f - t)))
                val wave = sin(t * 7.0f - p * 2.2f + j * 0.55f) * sway * t
                val x = cx + spread + wave
                val y = rise.coerceAtMost(baseY)
                if (i == 0) path.moveTo(cx, baseY) else path.lineTo(x, y)
            }
            val alpha = 0.3f + 0.55f * (1f - abs(a))
            drawPath(path, color.copy(alpha = color.alpha * alpha), style = stroke)
        }
    }
}

private const val FRAME_NANOS = 33_000_000L

/**
 * Hands out the delays that make a screen's content rise one piece after
 * another when it opens (55 ms apart in the first 700 ms, as in Oko).
 */
class RevealSequence internal constructor(private val openedNanos: Long) {
    private var next = 0

    internal fun delayMs(): Int {
        val elapsedMs = (System.nanoTime() - openedNanos) / 1_000_000
        return if (elapsedMs < STAGGER_WINDOW_MS) (next++).coerceAtMost(MAX_STAGGERED) * STAGGER_MS else 0
    }
}

/** The screen's [RevealSequence]; [ScreenScaffold] provides one. */
val LocalReveal = staticCompositionLocalOf<RevealSequence?> { null }

@Composable
fun rememberRevealSequence(): RevealSequence = remember { RevealSequence(System.nanoTime()) }

/** Fades this in and lifts it into place the first time it is composed. */
fun Modifier.reveal(rise: Dp = 16.dp): Modifier = composed {
    if (LocalReducedMotion.current) return@composed Modifier
    val sequence = LocalReveal.current
    val delay = remember { sequence?.delayMs() ?: 0 }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(progress) {
        progress.animateTo(1f, tween(REVEAL_MS, delayMillis = delay, easing = FastOutSlowInEasing))
    }
    Modifier.graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * rise.toPx()
    }
}

private const val REVEAL_MS = 420
private const val STAGGER_MS = 55
private const val MAX_STAGGERED = 10
private const val STAGGER_WINDOW_MS = 700L

private fun smoothstep(x: Float): Float {
    val t = x.coerceIn(0f, 1f)
    return t * t * (3 - 2 * t)
}

/**
 * The launch sequence, timed like Jazol's and Oko's: the bubble drops into place, its two lines
 * of talk are typed, the mark slides into a lockup with the name, and everything fades
 * out over the first screen, all within one second. The first screen is composed underneath from
 * the first frame and a tap skips it.
 */
@Composable
fun LaunchIntro(onFinished: () -> Unit) {
    val time = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        time.animateTo(INTRO_MS, tween(INTRO_MS.toInt(), easing = LinearEasing))
        onFinished()
    }
    val accent = MaterialTheme.colorScheme.primary
    val background = MaterialTheme.colorScheme.background
    val t = time.value
    val fall = smoothstep(t / 360f)
    val ripple = smoothstep((t - 300f) / 220f)
    val move = smoothstep((t - 450f) / 300f)
    val wordAlpha = smoothstep((t - 550f) / 250f)
    val exit = smoothstep((t - (INTRO_MS - MuabetMotion.EXIT)) / MuabetMotion.EXIT)

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 1f - exit }
            .background(background)
            .clearAndSetSemantics { }
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onFinished),
    ) {
        val startSize = 140.dp
        val endSize = 44.dp
        val wordHeight = 44.dp
        // Lockup: mark, gap, wordmark text (about 2.6x its height wide).
        val lockupWidth = endSize + 14.dp + wordHeight * 3.1f
        val lockupLeft = (maxWidth - lockupWidth) / 2
        val endCenterX = lockupLeft + endSize / 2
        val drop = remember { Path() }
        Canvas(Modifier.fillMaxSize()) {
            val markPx = (startSize + (endSize - startSize) * move).toPx()
            val cx = size.width / 2 + (endCenterX.toPx() - size.width / 2) * move
            val cy = size.height / 2
            val k = markPx / 100f
            val ox = cx - 50f * k
            // The drop falls from above into place; it lands as the ripple starts.
            val dropOffset = (1f - fall) * -size.height * 0.35f
            val oy = cy - 50f * k
            val stroke = Stroke(MuabetMarkGeometry.STROKE * k, cap = StrokeCap.Butt, join = StrokeJoin.Round)
            drawPath(bubblePath(drop, k, ox, oy + dropOffset), accent.copy(alpha = 0.25f + 0.75f * fall), style = stroke)
            drawChatLines(accent, k, ox, oy, ripple)
        }
        Box(
            Modifier
                .offset(x = lockupLeft + endSize + 14.dp, y = maxHeight / 2 - wordHeight / 2)
                .graphicsLayer {
                    alpha = wordAlpha
                    translationX = (1f - wordAlpha) * 12.dp.toPx()
                },
        ) {
            MuabetName(height = wordHeight)
        }
    }
}

private const val INTRO_MS = 1000f
