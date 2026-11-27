@file:OptIn(ExperimentalMaterial3Api::class)

package com.alexhr90.muabet.designsystem

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.alexhr90.muabet.R

// --- Spacing ---------------------------------------------------------------

/** The 4dp spacing scale from the handoff. */
object Space {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp
    val huge = 64.dp
}

/** 24dp content inset, 16dp on narrow windows. */
@Composable
fun contentInset(): Dp = if (LocalConfiguration.current.screenWidthDp < 360) Space.l else Space.xl

// --- Scaffolding -----------------------------------------------------------

/**
 * Every screen's frame: a flat header bar and content padded clear of it.
 *
 * Root screens ([onBack] null) show the Muabet mark on the left and, when
 * [onOpenSettings] is given, the settings gear on the right. Detail screens show
 * Back, the mark centred (swapped for [title] once content scrolls under the
 * bar, or always with [titleInBar]) and their own [actions].
 *
 * The large page title is part of the content: lists start with [pageTitle].
 */
@Composable
fun ScreenScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
    titleInBar: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val barColors = TopAppBarDefaults.topAppBarColors(
        containerColor = screenGround,
        // Over the backdrop, a scrolled bar gets a soft scrim so content reads beneath it.
        scrolledContainerColor = barGround
    )
    val scrolled by remember(scrollBehavior) { derivedStateOf { scrollBehavior.state.overlappedFraction > 0.01f } }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = screenGround,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            Column {
                if (onBack == null) {
                    TopAppBar(
                        title = { MuabetWordmark(height = 26.dp) },
                        actions = {
                            actions()
                            if (onOpenSettings != null) {
                                IconButton(onClick = onOpenSettings) {
                                    Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.cd_settings))
                                }
                            }
                        },
                        colors = barColors,
                        scrollBehavior = scrollBehavior
                    )
                } else {
                    CenterAlignedTopAppBar(
                        title = {
                            AnimatedContent(
                                targetState = scrolled || titleInBar,
                                transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(160)) },
                                label = "barTitle"
                            ) { showTitle ->
                                if (showTitle) {
                                    Text(
                                        title,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                } else {
                                    MuabetMark(Modifier.size(24.dp))
                                }
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                            }
                        },
                        actions = actions,
                        colors = barColors,
                        scrollBehavior = scrollBehavior
                    )
                }
                if (scrolled) Rule()
            }
        },
        bottomBar = bottomBar,
        floatingActionButton = floatingActionButton,
        snackbarHost = snackbarHost
    ) { padding ->
        // Each screen's pieces rise in one after another when it opens.
        CompositionLocalProvider(LocalReveal provides rememberRevealSequence()) {
            content(padding)
        }
    }
}

/** Standard content padding for a screen's scrolling list. */
@Composable
fun PaddingValues.screenContent(extraBottom: Dp = Space.xl): PaddingValues {
    val inset = contentInset()
    return PaddingValues(
        start = inset,
        end = inset,
        top = calculateTopPadding() + Space.xs,
        bottom = calculateBottomPadding() + extraBottom
    )
}

/**
 * The large title that opens a page, with the accent full stop from the brand.
 * [hero] is the 48sp root-screen size; detail pages use the 36sp page title.
 */
@Composable
fun PageTitle(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    hero: Boolean = false,
    trailing: @Composable (() -> Unit)? = null
) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = modifier.reveal()
            .fillMaxWidth()
            .padding(top = Space.l, bottom = Space.xl),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = buildAnnotatedString {
                    // The closing mark is the accent: the full stop.
                    val body = title.trimEnd('.', '?', '!')
                    val mark = if (title.endsWith("?")) "?" else "."
                    append(body)
                    withStyle(SpanStyle(color = accent)) { append(mark) }
                },
                style = if (hero) MaterialTheme.typography.displayMedium else MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.semantics { heading() }
            )
            if (subtitle != null) {
                Spacer(Modifier.height(Space.xs))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(Space.m))
            trailing()
        }
    }
}

/** [PageTitle] as the first item of a lazy list. */
fun LazyListScope.pageTitle(title: String, subtitle: String? = null, hero: Boolean = false) {
    item(key = "page-title", contentType = "page-title") { PageTitle(title, subtitle = subtitle, hero = hero) }
}

// --- Tone ------------------------------------------------------------------

enum class Tone { Positive, Warning, Negative, Info, Neutral, Accent }

@Composable
fun Tone.color(): Color = when (this) {
    Tone.Positive -> MaterialTheme.extended.success
    Tone.Warning -> MaterialTheme.extended.warning
    Tone.Negative -> MaterialTheme.extended.danger
    Tone.Info -> MaterialTheme.extended.info
    Tone.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
    Tone.Accent -> MaterialTheme.colorScheme.primary
}

@Composable
fun Tone.container(): Color = when (this) {
    Tone.Positive -> MaterialTheme.extended.successContainer
    Tone.Warning -> MaterialTheme.extended.warningContainer
    Tone.Negative -> MaterialTheme.extended.dangerContainer
    Tone.Info -> glassColorStrong
    Tone.Neutral -> glassColorStrong
    Tone.Accent -> MaterialTheme.colorScheme.primaryContainer
}

// --- Rules and rows --------------------------------------------------------

/** The 1dp decorative rule between rows. */
@Composable
fun Rule(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier, thickness = 1.dp, color = MaterialTheme.extended.divider)
}

/**
 * A plain row that leads somewhere: optional icon, title and subtitle, the
 * chevron, and a rule underneath. No card; the row is the target.
 */
@Composable
fun NavigationRow(
    title: String,
    subtitle: String,
    icon: ImageVector?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: Tone = Tone.Accent,
    trailing: @Composable (() -> Unit)? = null,
    showRule: Boolean = true
) {
    Column(modifier.reveal().fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .clickable(onClick = onClick, role = Role.Button)
                .padding(vertical = Space.m),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (tone == Tone.Negative) tone.color() else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(Modifier.width(Space.l + Space.xs))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize),
                    color = if (tone == Tone.Negative) tone.color() else MaterialTheme.colorScheme.onSurface
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(Space.s))
            if (trailing != null) {
                trailing()
            } else {
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (showRule) Rule()
    }
}

/** A settings-style row: title, a muted current value and a chevron. */
@Composable
fun ValueRow(
    title: String,
    value: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailingIcon: ImageVector? = null,
    trailingIconDescription: String? = null
) {
    Column(modifier.reveal().fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(onClick = onClick, role = Role.Button)
                .padding(vertical = Space.m),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (value != null) {
                Spacer(Modifier.width(Space.m))
                Text(
                    value,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
            if (trailingIcon != null) {
                Spacer(Modifier.width(Space.m))
                Icon(
                    trailingIcon,
                    contentDescription = trailingIconDescription,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(Space.s))
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Rule()
    }
}

/** A row with a switch; the whole row toggles it. */
@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    Column(modifier.reveal().fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
                .padding(vertical = Space.m),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(Space.m))
            Switch(checked = checked, onCheckedChange = null)
        }
        Rule()
    }
}

// --- Surfaces --------------------------------------------------------------

/**
 * A quiet container, for the few things that deserve one: a warning, a form,
 * an explanation. Ordinary rows sit on the background instead.
 */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: Dp = Space.l,
    color: Color = glassColor,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = MaterialTheme.shapes.large
    val border = glassBorder
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = shape, color = color, border = border) {
            Column(modifier = Modifier.padding(contentPadding), content = content)
        }
    } else {
        Surface(modifier = modifier.fillMaxWidth(), shape = shape, color = color, border = border) {
            Column(modifier = Modifier.padding(contentPadding), content = content)
        }
    }
}

/**
 * A titled section: a rule, a header line with a small icon and an optional
 * trailing slot, then the content. Open, not boxed.
 */
@Composable
fun TitledCard(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tone: Tone = Tone.Accent,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Rule()
        Spacer(Modifier.height(Space.l))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = tone.color(), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Space.m))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() }
            )
            trailing?.invoke()
        }
        Spacer(Modifier.height(Space.m))
        content()
        Spacer(Modifier.height(Space.s))
    }
}

/** A small muted label that opens a group of rows. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = modifier.reveal()
            .fillMaxWidth()
            .padding(top = Space.xl, bottom = Space.s),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge.copy(letterSpacing = MaterialTheme.typography.labelSmall.letterSpacing),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() }
        )
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

// --- Small pieces ----------------------------------------------------------

/** An outline icon in a soft tonal square, for list leading slots. */
@Composable
fun IconBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tone: Tone = Tone.Accent,
    size: Dp = 40.dp,
    tint: Color = tone.color(),
    background: Color = tone.container()
) {
    Box(
        modifier = modifier
            .size(size)
            .background(background, RoundedCornerShape(size * 0.3f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.55f))
    }
}

/** A status word with a coloured dot. Colour never carries the status alone. */
@Composable
fun StatusPill(text: String, tone: Tone, modifier: Modifier = Modifier, showDot: Boolean = true) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (showDot) {
            StatusDot(tone.color(), size = 8.dp)
            Spacer(Modifier.width(Space.s))
        }
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (showDot) MaterialTheme.colorScheme.onSurface else tone.color()
        )
    }
}

@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier, size: Dp = 8.dp) {
    Box(
        modifier
            .size(size)
            .background(color, CircleShape)
    )
}

/** Label on the left, value on the right. */
@Composable
fun InfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = modifier.reveal()
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(Space.m))
        Text(
            text = value,
            style = valueStyle.copy(fontWeight = FontWeight.Medium),
            color = valueColor,
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** A single figure with its unit and caption, set in the open. */
@Composable
fun MetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    icon: ImageVector? = null,
    tone: Tone = Tone.Accent,
    caption: String? = null,
    onClick: (() -> Unit)? = null
) {
    Column(
        modifier = modifier.reveal()
            .clip(MaterialTheme.shapes.medium)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = Space.s, horizontal = Space.xs)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = tone.color(), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(Space.xs))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium.numeric,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            if (unit != null) {
                Spacer(Modifier.width(Space.xs))
                Text(
                    text = unit,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
        }
        if (caption != null) {
            Text(
                text = caption,
                style = MaterialTheme.typography.labelMedium,
                color = tone.color(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** A horizontal meter, 0..1. */
@Composable
fun Meter(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp
) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(600), label = "meter")
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(glassColorStrong)
    ) {
        Box(
            Modifier
                .fillMaxWidth(animated)
                .height(height)
                .clip(CircleShape)
                .background(color)
        )
    }
}

// --- Buttons ---------------------------------------------------------------

/** The filled accent pill: one per screen, for its main action. */
@Composable
fun PrimaryPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(PillHeight),
        contentPadding = PaddingValues(horizontal = Space.xl, vertical = 0.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Space.s))
        }
        Text(text, style = MaterialTheme.typography.titleSmall, maxLines = 1)
    }
}

/** The outlined pill, for secondary or stopping actions. */
@Composable
fun OutlinedPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(PillHeight),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = glassColor, contentColor = MaterialTheme.colorScheme.onSurface),
        contentPadding = PaddingValues(horizontal = Space.xl, vertical = 0.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Space.s))
        }
        Text(text, style = MaterialTheme.typography.titleSmall, maxLines = 1)
    }
}

/**
 * The action at the end of a list row (Block, Unblock, Remove): one fixed
 * width whatever its word, so a column of them lines up, and slimmer than a
 * page's pill so the row's text keeps the room.
 */
@Composable
fun RowActionPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.width(ROW_ACTION_WIDTH).heightIn(min = 44.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = glassColor, contentColor = MaterialTheme.colorScheme.onSurface),
        contentPadding = PaddingValues(horizontal = Space.s, vertical = Space.xs),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

private val ROW_ACTION_WIDTH = 104.dp

// --- Dialogs ---------------------------------------------------------------

/** Pick one of a few options, radio-style. Selecting closes the dialog. */
@Composable
fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    footnote: String? = null
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                Modifier
                    .selectableGroup()
                    .verticalScroll(rememberScrollState())
            ) {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(
                                selected = option == selected,
                                role = Role.RadioButton,
                                onClick = {
                                    onSelect(option)
                                    onDismiss()
                                }
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = option == selected, onClick = null)
                        Spacer(Modifier.width(Space.m))
                        Text(label(option), style = MaterialTheme.typography.bodyLarge)
                    }
                }
                if (footnote != null) {
                    Spacer(Modifier.height(Space.s))
                    Text(footnote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } }
    )
}

// --- Whole-screen states ---------------------------------------------------

/**
 * A whole-screen empty state. With no [icon] it shows the Muabet mark.
 */
@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tone: Tone = Tone.Neutral,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier.reveal()
            .fillMaxWidth()
            .padding(horizontal = Space.xxl, vertical = Space.xxxl),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = tone.color(), modifier = Modifier.size(40.dp))
        } else {
            MuabetMark(Modifier.size(48.dp), color = tone.color())
        }
        Spacer(Modifier.height(Space.l))
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(Space.s))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(Space.xl))
            PrimaryPill(actionLabel, onAction)
        }
    }
}

/**
 * Quiet placeholder while data loads: the mark breathes (still under
 * reduced motion).
 */
@Composable
fun LoadingState(message: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LoadingMark(Modifier.size(36.dp))
            Spacer(Modifier.height(Space.l))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Explanatory copy, set small and muted. */
@Composable
fun NoteCard(text: String, modifier: Modifier = Modifier) {
    SectionCard(modifier.padding(vertical = Space.s), contentPadding = 0.dp) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(Space.l)
        )
    }
}

// --- Charts ----------------------------------------------------------------

/**
 * A smooth area chart. Scaled to its own data unless [minValue]/[maxValue] fix
 * the range, which keeps a signal-strength chart from exaggerating 2 dB of noise.
 */
@Composable
fun AreaChart(
    values: List<Float>,
    color: Color,
    modifier: Modifier = Modifier,
    minValue: Float? = null,
    maxValue: Float? = null,
    showGrid: Boolean = true
) {
    val gridColor = MaterialTheme.extended.divider
    Canvas(modifier = modifier) {
        if (showGrid) {
            for (i in 0..3) {
                val y = size.height * i / 3f
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }
        }
        if (values.size < 2) return@Canvas

        val lo = minValue ?: values.min()
        val hiRaw = maxValue ?: values.max()
        val hi = if (hiRaw - lo < 0.0001f) lo + 1f else hiRaw
        val stepX = size.width / (values.size - 1)
        val pad = 4.dp.toPx()
        val usable = size.height - pad * 2

        fun yFor(v: Float) = pad + usable * (1f - ((v - lo) / (hi - lo)).coerceIn(0f, 1f))

        val line = Path()
        values.forEachIndexed { i, v ->
            val x = i * stepX
            val y = yFor(v)
            if (i == 0) {
                line.moveTo(x, y)
            } else {
                val prevX = (i - 1) * stepX
                val prevY = yFor(values[i - 1])
                val midX = (prevX + x) / 2f
                line.cubicTo(midX, prevY, midX, y, x, y)
            }
        }

        val fill = Path().apply {
            addPath(line)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(
            fill,
            brush = Brush.verticalGradient(listOf(color.copy(alpha = 0.2f), color.copy(alpha = 0f)))
        )
        drawPath(line, color = color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(color = color, radius = 3.5.dp.toPx(), center = Offset(size.width, yFor(values.last())))
    }
}
