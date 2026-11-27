@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.alexhr90.muabet.designsystem

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddReaction
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.SentimentDissatisfied
import androidx.compose.material.icons.outlined.SentimentVerySatisfied
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import androidx.compose.foundation.border
import androidx.compose.ui.unit.Dp
import com.alexhr90.muabet.R
import com.alexhr90.muabet.domain.model.Block
import com.alexhr90.muabet.domain.model.Post
import com.alexhr90.muabet.domain.model.PostAuthor
import com.alexhr90.muabet.domain.model.ReactionSummary
import com.alexhr90.muabet.domain.model.Run
import java.net.URI
import kotlin.math.roundToInt

// Muabet's own components (SPEC §5): ForumRow, ThreadRow, PostItem, QuoteBlock, ReactionSummary,
// PageJumpSheet and UnreadDot. Open rows with 1 dp rules, no cards, no icons on forums.

/** The accent dot that marks something unread. Paired with bolder text, never colour alone. */
@Composable
fun UnreadDot(modifier: Modifier = Modifier) {
    val label = stringResource(R.string.cd_unread)
    StatusDot(MaterialTheme.colorScheme.primary, modifier.semantics { contentDescription = label }, size = 8.dp)
}

/**
 * A forum in the list: two compact lines, the name and the latest thread. Unread forums are set
 * in a heavier weight with the accent dot. No icons and no counts (SPEC §4 M1).
 */
@Composable
fun ForumRow(
    title: String,
    latest: String?,
    unread: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val compact = LocalCompactRows.current
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = if (compact) 48.dp else 56.dp)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = onLongClickLabel, role = Role.Button)
                .padding(vertical = if (compact) Space.s else Space.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!latest.isNullOrBlank()) {
                    Text(
                        latest,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            if (unread) {
                Spacer(Modifier.width(Space.m))
                UnreadDot()
            }
            if (trailing != null) {
                Spacer(Modifier.width(Space.s))
                trailing()
            }
        }
        Rule()
    }
}

/** A thread in a forum's list: title, then who and when; unread in a heavier weight with the dot. */
@Composable
fun ThreadRow(
    title: String,
    meta: String,
    unread: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    prefix: String? = null,
    sticky: Boolean = false,
    locked: Boolean = false,
    pagesLabel: String? = null,
    onPages: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
) {
    val compact = LocalCompactRows.current
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = if (compact) 52.dp else 64.dp)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = onLongClickLabel, role = Role.Button)
                .padding(vertical = if (compact) Space.s else Space.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    buildAnnotatedString {
                        if (prefix != null) {
                            withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)) { append(prefix) }
                            append("  ")
                        }
                        append(title)
                    },
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = if (compact) 2 else 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (sticky) SmallIcon(Icons.Outlined.PushPin, stringResource(R.string.cd_pinned))
                    if (locked) SmallIcon(Icons.Outlined.Lock, stringResource(R.string.cd_locked))
                    Text(
                        meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (unread) {
                Spacer(Modifier.width(Space.m))
                UnreadDot()
            }
            if (pagesLabel != null && onPages != null) {
                Spacer(Modifier.width(Space.s))
                Text(
                    pagesLabel,
                    style = MaterialTheme.typography.labelMedium.numeric,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onPages, role = Role.Button)
                        .heightIn(min = 48.dp)
                        .padding(horizontal = Space.s, vertical = Space.m),
                )
            }
        }
        Rule()
    }
}

@Composable
private fun SmallIcon(icon: ImageVector, description: String) {
    Icon(icon, contentDescription = description, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
    Spacer(Modifier.width(Space.xs))
}

/** Hooks a post's content up to the app: links, images, the author card, the post menu and the fallback. */
data class PostActions(
    val onLink: (String) -> Unit,
    val onImage: (String) -> Unit,
    val onOpenOnForum: () -> Unit,
    val onAuthor: (PostAuthor) -> Unit = {},
    val onMore: (Post) -> Unit = {},
    /** Downloads an attached file with the session. */
    val onDownload: (url: String, name: String) -> Unit = { url, _ -> onLink(url) },
    /** Opens the reaction picker for the post; null hides the button. */
    val onReact: ((Post) -> Unit)? = null,
    /** Quotes the post in a reply; null hides the button. */
    val onQuote: ((Post) -> Unit)? = null,
)

/**
 * One post on its own glass card (owner's test of v0.1.0: posts need clear separation): a
 * one-line header (avatar, name, time, number) where the name opens the member card, the body as
 * blocks, and the reactions with the post menu.
 */
@Composable
fun PostItem(
    post: Post,
    actions: PostActions,
    modifier: Modifier = Modifier,
    time: String? = null,
    highlighted: Boolean = false,
) {
    val accent = MaterialTheme.colorScheme.primary
    Column(
        modifier
            .fillMaxWidth()
            .glass(MaterialTheme.shapes.large)
            .then(if (highlighted) Modifier.border(1.5.dp, accent.copy(alpha = 0.7f), MaterialTheme.shapes.large) else Modifier)
            .padding(start = Space.l, end = Space.xs, top = Space.xs, bottom = Space.s),
    ) {
        val authorLabel = stringResource(R.string.cd_member_card, post.author.name)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.small)
                    .clickable(role = Role.Button, onClickLabel = authorLabel) { actions.onAuthor(post.author) }
                    .heightIn(min = 48.dp)
                    .padding(end = Space.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(post.author.avatarUrl, post.author.name)
                Spacer(Modifier.width(Space.m))
                Column(Modifier.weight(1f, fill = false)) {
                    Text(
                        post.author.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val shownTime = time ?: post.timeText
                    val sub = listOfNotNull(post.author.title, shownTime).joinToString("  ·  ")
                    if (sub.isNotEmpty()) {
                        Text(
                            sub,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (post.unread) {
                StatusPill(stringResource(R.string.post_new), Tone.Accent, Modifier.padding(end = Space.xs))
            }
            post.number?.let {
                Text("#$it", style = MaterialTheme.typography.labelMedium.numeric, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { actions.onMore(post) }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.cd_post_menu), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(Modifier.padding(end = Space.m)) {
            Spacer(Modifier.height(Space.xs))
            BlockList(post.body, actions)
            val canReact = actions.onReact != null && post.can.react
            val canQuote = actions.onQuote != null && post.can.quote
            if (post.reactions != null || canReact || canQuote) {
                Spacer(Modifier.height(Space.s))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        post.reactions?.let {
                            ReactionSummaryRow(it, onClick = it.listUrl?.let { url -> { actions.onLink(url) } })
                        }
                    }
                    if (canReact) {
                        val mine = post.can.myReactionId
                        IconButton(onClick = { actions.onReact?.invoke(post) }) {
                            Icon(
                                if (mine != null) reactionIcon(mine, null) else Icons.Outlined.AddReaction,
                                contentDescription = stringResource(if (mine != null) R.string.cd_change_reaction else R.string.action_react),
                                tint = if (mine != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (canQuote) {
                        IconButton(onClick = { actions.onQuote?.invoke(post) }) {
                            Icon(Icons.Outlined.FormatQuote, contentDescription = stringResource(R.string.action_quote), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

/** A post hidden by a muted keyword: one quiet line that shows it on tap. */
@Composable
fun MutedPostItem(author: String, onShow: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .glass(MaterialTheme.shapes.large)
            .clickable(onClick = onShow, role = Role.Button)
            .heightIn(min = 56.dp)
            .padding(horizontal = Space.l),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.VisibilityOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(Space.m))
        Text(
            stringResource(R.string.post_muted, author),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(stringResource(R.string.action_show), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun Avatar(url: String?, name: String, size: Dp = 36.dp) {
    if (url != null) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size).clip(CircleShape).background(glassColorStrong, CircleShape),
        )
    } else {
        Box(Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Text(
                name.firstOrNull()?.uppercase() ?: "?",
                style = if (size > 48.dp) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

/**
 * The member card (owner's test of v0.1.0: names open a card, not an accordion): avatar, name,
 * title and the forum's details, with the profile, a direct message and their posts.
 */
@Composable
fun MemberSheet(
    author: PostAuthor,
    loggedIn: Boolean,
    onProfile: () -> Unit,
    onMessage: () -> Unit,
    onPosts: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = floatingGround) {
        SheetBody {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(author.avatarUrl, author.name, size = 64.dp)
                Spacer(Modifier.width(Space.l))
                Column(Modifier.weight(1f)) {
                    Text(author.name, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    author.title?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
                }
            }
            if (author.details.isNotEmpty()) {
                Column {
                    author.details.forEach { (label, value) -> InfoRow(label, value) }
                }
            }
            if (author.profileUrl != null) {
                PrimaryPill(stringResource(R.string.member_profile), onClick = onProfile, modifier = Modifier.fillMaxWidth())
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s), modifier = Modifier.fillMaxWidth()) {
                if (loggedIn) OutlinedPill(stringResource(R.string.member_message), onMessage, Modifier.weight(1f))
                if (author.userId != null) OutlinedPill(stringResource(R.string.member_posts), onPosts, Modifier.weight(1f))
            }
        }
    }
}

/** One action in a menu sheet: an icon and a label, the whole row is the target. */
data class SheetAction(val label: String, val icon: ImageVector, val tone: Tone = Tone.Neutral, val onClick: () -> Unit)

/** A bottom sheet of actions (post menu, forum and thread long-press). */
@Composable
fun ActionSheet(title: String?, actions: List<SheetAction>, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = floatingGround) {
        Column(Modifier.fillMaxWidth().padding(bottom = Space.xl)) {
            if (title != null) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = Space.xl).padding(bottom = Space.s),
                )
            }
            actions.forEach { a ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) {
                            onDismiss()
                            a.onClick()
                        }
                        .heightIn(min = 56.dp)
                        .padding(horizontal = Space.xl),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val tint = if (a.tone == Tone.Negative) a.tone.color() else MaterialTheme.colorScheme.onSurface
                    Icon(a.icon, contentDescription = null, tint = tint)
                    Spacer(Modifier.width(Space.l))
                    Text(a.label, style = MaterialTheme.typography.bodyLarge, color = tint)
                }
            }
        }
    }
}

/** Post content, block by block. */
@Composable
fun BlockList(blocks: List<Block>, actions: PostActions, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.s)) {
        blocks.forEach { block -> BlockView(block, actions) }
    }
}

@Composable
private fun BlockView(block: Block, actions: PostActions) {
    when (block) {
        is Block.Paragraph -> Text(
            runsToAnnotated(block.runs, actions.onLink),
            style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content),
            color = MaterialTheme.colorScheme.onSurface,
        )
        is Block.Quote -> QuoteBlock(block.author, block.blocks, actions)
        is Block.Image -> PostImage(block, actions)
        is Block.Link -> LinkCard(block, actions)
        is Block.Embed -> EmbedBlock(block, actions)
        is Block.Table -> TableBlock(block, actions)
        is Block.Box -> BoxBlock(block, actions)
        is Block.File -> FileBlock(block, actions)
        is Block.Spoiler -> SpoilerBlock(block.title, block.blocks, actions)
        is Block.Code -> Text(
            block.text,
            style = MaterialTheme.typography.bodySmall.mono,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .glass(MaterialTheme.shapes.small)
                .horizontalScroll(rememberScrollState())
                .padding(Space.m),
        )
        is Block.ListBlock -> Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            block.items.forEachIndexed { i, item ->
                Row {
                    Text(
                        if (block.ordered) "${i + 1}." else "•",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(24.dp),
                    )
                    BlockList(item, actions, Modifier.weight(1f))
                }
            }
        }
        // The forum's own markup, drawn in place; nothing is left out.
        is Block.Unknown -> block.html?.let { HtmlBlock(it, actions.onLink) }
    }
}

/**
 * A post image at full width. With "load images on tap" it waits behind a placeholder; an image
 * that fails to load says so and opens on tap, never an empty gap.
 */
@Composable
private fun PostImage(block: Block.Image, actions: PostActions) {
    val onTap = LocalImagesOnTap.current
    var revealed by rememberSaveable(block.url) { mutableStateOf(!onTap) }
    if (!revealed) {
        LinkLine(stringResource(R.string.image_tap_to_load), block.alt ?: hostOf(block.url), Icons.Outlined.Image) { revealed = true }
        return
    }
    SubcomposeAsyncImage(
        model = block.url,
        contentDescription = block.alt,
        contentScale = ContentScale.FillWidth,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 560.dp)
            .clip(MaterialTheme.shapes.small)
            .clickable(role = Role.Image, onClickLabel = stringResource(R.string.cd_enlarge_image)) { actions.onImage(block.link ?: block.url) },
        loading = {
            Box(Modifier.fillMaxWidth().height(160.dp).background(glassColor), contentAlignment = Alignment.Center) {
                LogoLoader(Modifier.size(28.dp))
            }
        },
        error = {
            LinkLine(stringResource(R.string.image_failed), block.alt ?: hostOf(block.url), Icons.Outlined.BrokenImage) {
                actions.onLink(block.link ?: block.url)
            }
        },
    )
}

@Composable
private fun LinkLine(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .glass(MaterialTheme.shapes.small)
            .clickable(onClick = onClick, role = Role.Button)
            .heightIn(min = 56.dp)
            .padding(horizontal = Space.m, vertical = Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(Space.m))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

/**
 * A quote, collapsed to a few lines until tapped (SPEC §4 M1). The accent rule on the left marks
 * it; the author's name heads it.
 */
@Composable
fun QuoteBlock(author: String?, blocks: List<Block>, actions: PostActions, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val long = remember(blocks) { blocks.size > 2 || blocks.any { it !is Block.Paragraph } || blocks.sumOf { b -> (b as? Block.Paragraph)?.runs?.sumOf { it.text.length } ?: 0 } > 280 }
    val bar = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
    Row(
        modifier
            .fillMaxWidth()
            .animateContentSize()
            // The accent rule on the left follows the quote's height.
            .drawBehind { drawRect(bar, size = Size(2.dp.toPx(), size.height)) },
    ) {
        Spacer(Modifier.width(2.dp + Space.m))
        Column(
            Modifier
                .weight(1f)
                .then(if (long) Modifier.clickable(role = Role.Button) { expanded = !expanded } else Modifier),
        ) {
            if (author != null) {
                Text(
                    stringResource(R.string.quote_said, author),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = Space.xs),
                )
            }
            if (expanded || !long) {
                BlockList(blocks, actions)
            } else {
                val preview = blocks.filterIsInstance<Block.Paragraph>().firstOrNull()
                if (preview != null) {
                    Text(
                        runsToAnnotated(preview.runs, actions.onLink),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (long) {
                Row(Modifier.heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(if (expanded) R.string.quote_collapse else R.string.quote_expand),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Icon(
                        if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SpoilerBlock(title: String?, blocks: List<Block>, actions: PostActions) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().glass(MaterialTheme.shapes.small).animateContentSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) { open = !open }
                .heightIn(min = 48.dp)
                .padding(horizontal = Space.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Visibility, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Space.s))
            Text(
                title?.let { stringResource(R.string.spoiler_titled, it) } ?: stringResource(R.string.spoiler),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
            Icon(if (open) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown, contentDescription = null)
        }
        if (open) BlockList(blocks, actions, Modifier.padding(start = Space.m, end = Space.m, bottom = Space.m))
    }
}

/** Styled runs as one annotated string; links are tappable and underlined in the accent. */
@Composable
fun runsToAnnotated(runs: List<Run>, onLink: (String) -> Unit): AnnotatedString {
    val linkStyle = TextLinkStyles(SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline))
    return remember(runs, linkStyle, onLink) {
        buildAnnotatedString {
            runs.forEach { r ->
                val style = SpanStyle(
                    fontWeight = if (r.bold) FontWeight.SemiBold else null,
                    fontStyle = if (r.italic) FontStyle.Italic else null,
                    textDecoration = when {
                        r.underline && r.strike -> TextDecoration.combine(listOf(TextDecoration.Underline, TextDecoration.LineThrough))
                        r.underline -> TextDecoration.Underline
                        r.strike -> TextDecoration.LineThrough
                        else -> null
                    },
                )
                val href = r.href
                if (href != null) {
                    withLink(LinkAnnotation.Clickable(tag = href, styles = linkStyle) { onLink(href) }) {
                        withStyle(style) { append(r.text) }
                    }
                } else {
                    withStyle(style) { append(r.text) }
                }
            }
        }
    }
}

/** Reactions as icons with the total, the forum's own reaction names read aloud. */
@Composable
fun ReactionSummaryRow(summary: ReactionSummary, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val description = stringResource(R.string.cd_reactions, summary.total, summary.names.joinToString(", "))
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick, role = Role.Button) else Modifier)
            .heightIn(min = 32.dp)
            .padding(horizontal = Space.xs)
            .semantics(mergeDescendants = true) { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icons = summary.reactionIds.ifEmpty { listOf(1) }.take(3)
        icons.forEachIndexed { i, id ->
            Icon(
                reactionIcon(id, summary.names.getOrNull(i)),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(2.dp))
        }
        Spacer(Modifier.width(Space.xs))
        Text("${summary.total}", style = MaterialTheme.typography.labelMedium.numeric, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (summary.names.isNotEmpty()) {
            Spacer(Modifier.width(Space.s))
            Text(
                summary.names.distinct().joinToString(", "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** XenForo's default reactions: 1 like, 2 love, 3 haha, 4 wow, 5 sad, 6 angry; names win when given. */
internal fun reactionIcon(id: Int, name: String?): ImageVector = when (name?.lowercase()) {
    "like" -> Icons.Outlined.ThumbUp
    "love" -> Icons.Outlined.Favorite
    "haha" -> Icons.Outlined.SentimentVerySatisfied
    "sad" -> Icons.Outlined.SentimentDissatisfied
    "wow", "angry" -> Icons.Outlined.EmojiEmotions
    else -> when (id) {
        2 -> Icons.Outlined.Favorite
        3 -> Icons.Outlined.SentimentVerySatisfied
        4, 6 -> Icons.Outlined.EmojiEmotions
        5 -> Icons.Outlined.SentimentDissatisfied
        else -> Icons.Outlined.ThumbUp
    }
}

/**
 * Jump to any page of a long thread (SPEC §4 M1): a slider for the rough spot, a number field for
 * the exact page, and first and last.
 */
@Composable
fun PageJumpSheet(current: Int, last: Int, onJump: (Int) -> Unit, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var slider by remember { mutableFloatStateOf(current.toFloat()) }
    var typed by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = floatingGround) {
        SheetBody {
            Text(stringResource(R.string.page_jump_title), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(R.string.page_of, slider.roundToInt(), last),
                style = MaterialTheme.typography.bodyLarge.numeric,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (last > 1) {
                Slider(
                    value = slider,
                    onValueChange = { slider = it },
                    valueRange = 1f..last.toFloat(),
                    onValueChangeFinished = {},
                )
            }
            OutlinedTextField(
                value = typed,
                onValueChange = { v -> typed = v.filter { it.isDigit() }.take(6) },
                label = { Text(stringResource(R.string.page_number)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { typed.toIntOrNull()?.let { onJump(it.coerceIn(1, last)) } }),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s), modifier = Modifier.fillMaxWidth()) {
                OutlinedPill(stringResource(R.string.page_first), { onJump(1) }, Modifier.weight(1f))
                OutlinedPill(stringResource(R.string.page_last), { onJump(last) }, Modifier.weight(1f))
            }
            PrimaryPill(
                stringResource(R.string.page_go),
                onClick = { onJump((typed.toIntOrNull() ?: slider.roundToInt()).coerceIn(1, last)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SheetBody(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Space.xl).padding(bottom = Space.xxl),
        verticalArrangement = Arrangement.spacedBy(Space.m),
        content = content,
    )
}
