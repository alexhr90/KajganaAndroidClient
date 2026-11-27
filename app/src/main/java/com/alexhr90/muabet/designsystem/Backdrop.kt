package com.alexhr90.muabet.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import com.alexhr90.muabet.data.settings.BackdropStyle
import kotlinx.coroutines.delay
import java.time.LocalTime
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * True while a painted backdrop is drawn behind the app. Screens then leave their container
 * transparent so the scene shows through the theme scrim (the same approach as Vidik).
 */
val LocalBackdrop = staticCompositionLocalOf { false }

/** The screen ground: transparent over a backdrop, the theme background otherwise. */
val screenGround: Color
    @Composable get() = if (LocalBackdrop.current) Color.Transparent else MaterialTheme.colorScheme.background

/**
 * The ground for top, bottom and form bars: the theme background, a touch translucent over a
 * painted backdrop so the scene still tints it, but opaque enough that text scrolling under a bar
 * never shows through it (owner's test of v0.1.0).
 */
val barGround: Color
    @Composable get() = if (LocalBackdrop.current) MaterialTheme.colorScheme.background.copy(alpha = 0.96f) else MaterialTheme.colorScheme.background

/** One painted scene: a sky gradient and soft colour fields that drift. */
private data class Scene(
    val skyTop: Color,
    val skyBottom: Color,
    val blobs: List<Color>,
    val stars: Boolean = false,
    /** Aurora ribbons: blobs stretched sideways. */
    val ribbons: Boolean = false,
)

private val Dawn = Scene(Color(0xFF3B2A5C), Color(0xFFF2A58A), listOf(Color(0xFFF28AA0), Color(0xFFFFC58A), Color(0xFF9C88D8), Color(0xFFFFE2B8)))
private val Day = Scene(Color(0xFF4C8FD6), Color(0xFFBFE6F5), listOf(Color(0xFFA9E3FF), Color(0xFFFFB27A), Color(0xFF5BA7E8), Color(0xFFFFF3C4)))
private val Dusk = Scene(Color(0xFF1F2A5A), Color(0xFFFF8A50), listOf(Color(0xFFD2477A), Color(0xFFFFA25C), Color(0xFF4B3A8C), Color(0xFFFF6F61)))
private val Night = Scene(Color(0xFF060B1E), Color(0xFF1A2152), listOf(Color(0xFF2B2F7A), Color(0xFF123A5C), Color(0xFF3B2466), Color(0xFF1C4E80)), stars = true)
private val Aurora = Scene(Color(0xFF05101A), Color(0xFF0B2233), listOf(Color(0xFF3DF2A0), Color(0xFF1FB5C9), Color(0xFF8A5CF6), Color(0xFF6CFFCB)), stars = true, ribbons = true)
// Izvor's lime "spring" scene, recoloured to Kajgana orange (SPEC §5).
private val EmberScene = Scene(Color(0xFF101112), Color(0xFF2A1608), listOf(Color(0xFFFF6D10), Color(0xFFA34300), Color(0xFFF2E6DA), Color(0xFFFF9A4D)))

/** Which scene AUTO shows at this hour: dawn 5 to 8, day 8 to 17, dusk 17 to 20, night otherwise. */
fun sceneForHour(hour: Int): BackdropStyle = when (hour) {
    in 5..7 -> BackdropStyle.DAWN
    in 8..16 -> BackdropStyle.DAY
    in 17..19 -> BackdropStyle.DUSK
    else -> BackdropStyle.NIGHT
}

private fun sceneOf(style: BackdropStyle, hour: Int): Scene? = when (style) {
    BackdropStyle.NONE -> null
    BackdropStyle.AUTO -> sceneOf(sceneForHour(hour), hour)
    BackdropStyle.DAWN -> Dawn
    BackdropStyle.DAY -> Day
    BackdropStyle.DUSK -> Dusk
    BackdropStyle.NIGHT -> Night
    BackdropStyle.AURORA -> Aurora
    BackdropStyle.EMBER -> EmberScene
}

/**
 * The painted backdrop behind the whole app: soft, blurred colour fields drawn in code (no
 * bitmaps), under a theme-coloured scrim so text keeps its contrast. Drifts slowly at about
 * 20 frames a second while the app is visible; a still picture under reduced motion.
 */
@Composable
fun MuabetBackdrop(style: BackdropStyle, modifier: Modifier = Modifier) {
    val reduced = LocalReducedMotion.current
    var hour by remember { mutableIntStateOf(LocalTime.now().hour) }
    LaunchedEffect(style) {
        while (style == BackdropStyle.AUTO) {
            hour = LocalTime.now().hour
            delay(60_000)
        }
    }
    val scene = sceneOf(style, hour) ?: return
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(reduced) {
        if (reduced) {
            t = 0f
            return@LaunchedEffect
        }
        var start = -1L
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (start < 0) start = now
                if (now - last >= 50_000_000L) {
                    last = now
                    t = (now - start) / 1_000_000_000f
                }
            }
        }
    }
    val ground = MaterialTheme.colorScheme.background
    val dark = MaterialTheme.extended.isDark
    Canvas(modifier.fillMaxSize()) {
        drawScene(scene, t)
        drawRect(ground.copy(alpha = if (dark) 0.58f else 0.66f))
    }
}

private fun DrawScope.drawScene(scene: Scene, t: Float) {
    val w = size.width
    val h = size.height
    drawRect(Brush.verticalGradient(listOf(scene.skyTop, scene.skyBottom)))
    val radius = maxOf(w, h) * 0.55f
    scene.blobs.forEachIndexed { i, color ->
        // Each field circles its own anchor on a slow, different period: 70 to 130 s.
        val period = 70f + i * 20f
        val phase = i * 1.7f
        val a = (t / period) * 2f * PI.toFloat() + phase
        val anchorX = w * (0.2f + 0.6f * ((i * 37) % 100) / 100f)
        val anchorY = h * (0.15f + 0.7f * ((i * 61 + 23) % 100) / 100f)
        val center = Offset(anchorX + cos(a) * w * 0.18f, anchorY + sin(a * 0.8f) * h * 0.12f)
        val r = radius * (0.85f + 0.15f * sin(a * 1.3f))
        val brush = Brush.radialGradient(listOf(color.copy(alpha = 0.75f), color.copy(alpha = 0f)), center = center, radius = r)
        if (scene.ribbons) {
            scale(scaleX = 1.8f, scaleY = 0.45f, pivot = center) { drawCircle(brush, r, center) }
        } else {
            drawCircle(brush, r, center)
        }
    }
    if (scene.stars) {
        for (i in 0 until 36) {
            val x = ((i * 7919) % 1000) / 1000f * w
            val y = ((i * 104729) % 1000) / 1000f * h * 0.7f
            val twinkle = 0.35f + 0.35f * sin(t * (0.6f + (i % 5) * 0.2f) + i)
            drawCircle(Color.White.copy(alpha = twinkle.coerceIn(0.05f, 0.8f)), radius = 1.2f + (i % 3) * 0.6f, center = Offset(x, y))
        }
    }
}
