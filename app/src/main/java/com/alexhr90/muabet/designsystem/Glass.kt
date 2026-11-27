package com.alexhr90.muabet.designsystem

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// --- Glass (v1.2.1) ----------------------------------------------------------------------------

/**
 * The ground of cards, pills and bars: a translucent tint of the theme with a hairline border,
 * so over a painted backdrop they read as glass in the same light or dark as the rest of the
 * app, never as a dark box. Without a backdrop it is the theme's quiet container colour.
 */
val glassColor: Color
    @Composable get() {
        val scheme = MaterialTheme.colorScheme
        if (!LocalBackdrop.current) return scheme.surfaceContainerLow
        val dark = scheme.background.luminance() < 0.5f
        return if (dark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.55f)
    }

/** A slightly stronger glass for small things that sit on top of glass (chips, thumbnails). */
val glassColorStrong: Color
    @Composable get() {
        val scheme = MaterialTheme.colorScheme
        if (!LocalBackdrop.current) return scheme.surfaceContainerHigh
        val dark = scheme.background.luminance() < 0.5f
        return if (dark) Color.White.copy(alpha = 0.13f) else Color.White.copy(alpha = 0.7f)
    }

/** The hairline that outlines glass. */
val glassBorder: BorderStroke
    @Composable get() = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = if (LocalBackdrop.current) 0.14f else 0.08f))

/** Glass ground plus its hairline, in [shape]. */
@Composable
fun Modifier.glass(shape: Shape = MaterialTheme.shapes.large, strong: Boolean = false): Modifier {
    val color = if (strong) glassColorStrong else glassColor
    val border = glassBorder
    return this.clip(shape).background(color, shape).border(border, shape)
}

/**
 * A bar that floats over content (the mini player, message bars): opaque enough to read over
 * any text, tinted by the theme, outlined like glass.
 */
val floatingGround: Color
    @Composable get() {
        val scheme = MaterialTheme.colorScheme
        val dark = scheme.background.luminance() < 0.5f
        return if (LocalBackdrop.current) scheme.surfaceContainerHigh.copy(alpha = if (dark) 0.92f else 0.94f) else scheme.surfaceContainerHigh
    }

// --- Logo loader -------------------------------------------------------------------------------

/**
 * Muabet's loading animation (from Izvor): the bubble of the mark is drawn in one stroke, then
 * its two lines of talk are typed, then it fades and starts again. It replaces every spinner. Under reduced
 * motion it is the still mark.
 */
@Composable
fun LogoLoader(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    if (LocalReducedMotion.current) {
        MuabetMark(modifier.graphicsLayer { alpha = 0.8f }, color)
        return
    }
    val transition = rememberInfiniteTransition(label = "logoLoader")
    val t by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1_700, easing = LinearEasing), RepeatMode.Restart), label = "t")
    LogoFrame(modifier, color, t)
}

/** One frame of the loader at time [t] (0..1); also used to follow a pull-to-refresh drag. */
@Composable
fun LogoFrame(modifier: Modifier, color: Color, t: Float) {
    Canvas(modifier.clearAndSetSemantics { }) {
        val k = size.minDimension / 100f
        val dx = (size.width - 100f * k) / 2f
        val dy = (size.height - 100f * k) / 2f
        val full = bubblePath(Path(), k, dx, dy)
        val measure = PathMeasure().apply { setPath(full, false) }
        val draw = FastOutSlowInEasing.transform((t / 0.55f).coerceIn(0f, 1f))
        val fade = if (t > 0.82f) 1f - (t - 0.82f) / 0.18f else 1f
        val tint = color.copy(alpha = color.alpha * fade.coerceIn(0f, 1f))
        val segment = Path()
        measure.getSegment(0f, measure.length * draw, segment, true)
        drawPath(segment, tint, style = Stroke(width = MuabetMarkGeometry.STROKE * k, cap = StrokeCap.Round, join = StrokeJoin.Round))
        val ripple = FastOutSlowInEasing.transform(((t - 0.5f) / 0.3f).coerceIn(0f, 1f))
        drawChatLines(tint, k, dx, dy, ripple)
    }
}

/** Pull to refresh with the logo instead of Material's spinner. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MuabetPullToRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val state = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        state = state,
        indicator = {
            val pulled = state.distanceFraction
            if (isRefreshing || pulled > 0f) {
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .graphicsLayer {
                            val f = if (isRefreshing) 1f else pulled.coerceIn(0f, 1.2f)
                            translationY = (f * 72.dp.toPx()) - 44.dp.toPx() / 2
                            alpha = f.coerceIn(0f, 1f)
                        }
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(floatingGround, CircleShape)
                        .border(glassBorder, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isRefreshing) {
                        LogoLoader(Modifier.size(26.dp))
                    } else {
                        // The bubble draws itself as you pull; it closes when a release will refresh.
                        LogoFrame(Modifier.size(26.dp), MaterialTheme.colorScheme.primary, (pulled * 0.8f).coerceIn(0f, 0.8f))
                    }
                }
            }
        },
        content = content,
    )
}

// --- Message bar -------------------------------------------------------------------------------

/**
 * In-app messages ("8 sources added", "Hidden · Undo") in the app's own design: a floating
 * glass bar with the mark, the text and an accent action, instead of Material's dark snackbar.
 */
@Composable
fun MuabetSnackbarHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(state, modifier) { data -> MuabetSnackbar(data) }
}

@Composable
fun MuabetSnackbar(data: SnackbarData) {
    Surface(
        modifier = Modifier
            .padding(horizontal = Space.l, vertical = Space.m)
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .heightIn(min = 52.dp),
        shape = RoundedCornerShape(18.dp),
        color = floatingGround,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = glassBorder,
        shadowElevation = 0.dp,
    ) {
        Row(Modifier.padding(start = Space.l, end = Space.xs), verticalAlignment = Alignment.CenterVertically) {
            MuabetMark(Modifier.size(18.dp))
            Spacer(Modifier.width(Space.m))
            Text(
                data.visuals.message,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f).padding(vertical = Space.m),
                maxLines = 3,
            )
            data.visuals.actionLabel?.let { label ->
                TextButton(onClick = { data.performAction() }) { Text(label, style = MaterialTheme.typography.labelLarge) }
            }
            if (data.visuals.actionLabel == null) Spacer(Modifier.width(Space.m))
        }
    }
}

/** A uniform size for pill buttons, so pills next to each other always match (v1.2.1). */
val PillHeight: Dp = 52.dp
