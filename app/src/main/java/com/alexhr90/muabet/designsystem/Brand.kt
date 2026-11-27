package com.alexhr90.muabet.designsystem

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The Muabet mark: a speech bubble with two lines of talk, in the family's monoline construction
 * (7-unit stroke on a 100-unit square, butt caps). Like Izvor's drop and Cena's tag, the outline
 * is left open at its upper right; the tail sits at the lower left. An original mark: it never
 * copies the Kajgana logo. Same numbers as `res/drawable/ic_launcher_foreground.xml`.
 */
object MuabetMarkGeometry {
    /** The bubble's outline, drawn clockwise from the open upper right. */
    val bubbleStart = Offset(64f, 18f)
    val bubble: List<Seg> = listOf(
        Seg.Line(Offset(30f, 18f)),
        Seg.Cubic(Offset(21.2f, 18f), Offset(14f, 25.2f), Offset(14f, 34f)),
        Seg.Line(Offset(14f, 54f)),
        Seg.Cubic(Offset(14f, 62.8f), Offset(21.2f, 70f), Offset(30f, 70f)),
        Seg.Line(Offset(28f, 86f)),
        Seg.Line(Offset(46f, 70f)),
        Seg.Line(Offset(70f, 70f)),
        Seg.Cubic(Offset(78.8f, 70f), Offset(86f, 62.8f), Offset(86f, 54f)),
        Seg.Line(Offset(86f, 32f)),
    )
    /** The two lines of talk inside the bubble, both starting at the left. */
    val lineTop = Offset(30f, 38f) to Offset(66f, 38f)
    val lineBottom = Offset(30f, 52f) to Offset(54f, 52f)
    const val STROKE = 7f
}

/** One piece of the outline: a straight line or a cubic curve to its end point. */
sealed interface Seg {
    data class Line(val end: Offset) : Seg
    data class Cubic(val c1: Offset, val c2: Offset, val end: Offset) : Seg
}

/** The bubble's outline at scale [k] with its 100-unit square's origin at ([ox], [oy]). */
fun bubblePath(path: Path, k: Float, ox: Float, oy: Float): Path {
    path.reset()
    val s = MuabetMarkGeometry.bubbleStart
    path.moveTo(ox + s.x * k, oy + s.y * k)
    MuabetMarkGeometry.bubble.forEach { seg ->
        when (seg) {
            is Seg.Line -> path.lineTo(ox + seg.end.x * k, oy + seg.end.y * k)
            is Seg.Cubic -> path.cubicTo(
                ox + seg.c1.x * k, oy + seg.c1.y * k,
                ox + seg.c2.x * k, oy + seg.c2.y * k,
                ox + seg.end.x * k, oy + seg.end.y * k,
            )
        }
    }
    return path
}

fun DrawScope.drawMuabetMark(color: Color, lines: Float = 1f) {
    val k = size.minDimension / 100f
    val dx = (size.width - 100f * k) / 2f
    val dy = (size.height - 100f * k) / 2f
    val stroke = Stroke(width = MuabetMarkGeometry.STROKE * k, cap = StrokeCap.Butt, join = StrokeJoin.Round)
    drawPath(bubblePath(Path(), k, dx, dy), color, style = stroke)
    drawChatLines(color, k, dx, dy, lines)
}

/** The two lines of talk, typed from the left by [progress] (0..1): the top line first. */
fun DrawScope.drawChatLines(color: Color, k: Float, ox: Float, oy: Float, progress: Float) {
    if (progress <= 0f) return
    val w = MuabetMarkGeometry.STROKE * k
    val top = (progress / 0.6f).coerceIn(0f, 1f)
    val bottom = ((progress - 0.4f) / 0.6f).coerceIn(0f, 1f)
    val (t0, t1) = MuabetMarkGeometry.lineTop
    val (b0, b1) = MuabetMarkGeometry.lineBottom
    if (top > 0f) drawLine(color, Offset(ox + t0.x * k, oy + t0.y * k), Offset(ox + (t0.x + (t1.x - t0.x) * top) * k, oy + t1.y * k), w, StrokeCap.Butt)
    if (bottom > 0f) drawLine(color, Offset(ox + b0.x * k, oy + b0.y * k), Offset(ox + (b0.x + (b1.x - b0.x) * bottom) * k, oy + b1.y * k), w, StrokeCap.Butt)
}

/** The mark, tinted to its ground: orange on charcoal, burnt orange on paper. */
@Composable
fun MuabetMark(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Canvas(modifier.clearAndSetSemantics { }) { drawMuabetMark(color) }
}

/** Mark plus the lowercase name with the accent full stop. */
@Composable
fun MuabetWordmark(modifier: Modifier = Modifier, height: Dp = 24.dp) {
    Row(modifier.semantics(mergeDescendants = true) { contentDescription = "Muabet" }, verticalAlignment = Alignment.CenterVertically) {
        MuabetMark(Modifier.size(height))
        Spacer(Modifier.width(height / 3))
        MuabetName(height = height)
    }
}

/** The lowercase name with the accent full stop, sized to [height]. */
@Composable
fun MuabetName(modifier: Modifier = Modifier, height: Dp = 24.dp) {
    val accent = MaterialTheme.colorScheme.primary
    Text(
        buildAnnotatedString {
            append("muabet")
            withStyle(SpanStyle(color = accent)) { append(".") }
        },
        modifier = modifier,
        style = MaterialTheme.typography.titleLarge.copy(fontSize = (height.value * 0.85f).sp, letterSpacing = (-0.4).sp),
        color = MaterialTheme.colorScheme.onBackground,
        maxLines = 1,
    )
}

/** Loading placeholder: the logo loader (the bubble draws itself, then the talk); still under reduced motion. */
@Composable
fun LoadingMark(modifier: Modifier = Modifier) {
    LogoLoader(modifier)
}
