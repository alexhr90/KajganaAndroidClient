package com.alexhr90.muabet.ui.thread

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AddReaction
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FirstPage
import androidx.compose.material.icons.rounded.LastPage
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.alexhr90.muabet.R
import com.alexhr90.muabet.data.repo.Loaded
import com.alexhr90.muabet.designsystem.ActionSheet
import com.alexhr90.muabet.designsystem.LocalReducedMotion
import com.alexhr90.muabet.designsystem.MemberSheet
import com.alexhr90.muabet.designsystem.MuabetPullToRefresh
import com.alexhr90.muabet.designsystem.MuabetSnackbarHost
import com.alexhr90.muabet.designsystem.MutedPostItem
import com.alexhr90.muabet.designsystem.NoteCard
import com.alexhr90.muabet.designsystem.OutlinedPill
import com.alexhr90.muabet.designsystem.PageJumpSheet
import com.alexhr90.muabet.designsystem.PageTitle
import com.alexhr90.muabet.designsystem.PostActions
import com.alexhr90.muabet.designsystem.PostItem
import com.alexhr90.muabet.designsystem.PrimaryPill
import com.alexhr90.muabet.designsystem.Rule
import com.alexhr90.muabet.designsystem.ScreenScaffold
import com.alexhr90.muabet.designsystem.SheetAction
import com.alexhr90.muabet.designsystem.Space
import com.alexhr90.muabet.designsystem.barGround
import com.alexhr90.muabet.designsystem.contentInset
import com.alexhr90.muabet.designsystem.floatingGround
import com.alexhr90.muabet.designsystem.numeric
import com.alexhr90.muabet.domain.filter.MuteRules
import com.alexhr90.muabet.domain.filter.PostText
import com.alexhr90.muabet.domain.model.Post
import com.alexhr90.muabet.domain.model.PostAuthor
import com.alexhr90.muabet.domain.model.ThreadPage
import com.alexhr90.muabet.domain.url.ForumUrls
import com.alexhr90.muabet.ui.common.LoadedContent
import com.alexhr90.muabet.ui.common.LocalOpenLink
import com.alexhr90.muabet.ui.common.LocalOpenWeb
import com.alexhr90.muabet.ui.common.MessageEffect
import com.alexhr90.muabet.ui.common.UiState
import com.alexhr90.muabet.ui.common.copyText
import com.alexhr90.muabet.ui.common.relativeTime
import com.alexhr90.muabet.ui.common.shareLink
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

/**
 * A thread, one page per screen: swipe sideways between pages (SPEC §4 M1), the page bar for
 * first, previous, the page jump sheet, next and last. Each post sits on its own card; its name
 * opens the member card, its menu quotes, reacts, bookmarks, copies or shares.
 */
@Composable
fun ThreadScreen(
    onBack: () -> Unit,
    /** Opens a forum form (reply, quote, react, watch, bookmark) that returns to the thread. */
    onCompose: (String) -> Unit,
    onSearchInThread: (Long, String) -> Unit,
    onSearchUrl: (String) -> Unit,
    openAtRequest: String?,
    onOpenAtHandled: () -> Unit,
    viewModel: ThreadViewModel = hiltViewModel(),
) {
    val infoState by viewModel.info.collectAsStateWithLifecycle()
    val loggedIn by viewModel.loggedIn.collectAsStateWithLifecycle()
    val muted by viewModel.muted.collectAsStateWithLifecycle()
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    val info = ((infoState as? UiState.Done)?.result as? Loaded.Ok)?.value
    val openWeb = LocalOpenWeb.current
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    MessageEffect(viewModel.messages, snackbar)
    LaunchedEffect(openAtRequest) {
        if (openAtRequest != null) {
            viewModel.openLink(openAtRequest)
            onOpenAtHandled()
        }
    }

    var jumpOpen by rememberSaveable { mutableStateOf(false) }
    var saveOpen by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var image by rememberSaveable { mutableStateOf<String?>(null) }
    var member by remember { mutableStateOf<PostAuthor?>(null) }
    var postMenu by remember { mutableStateOf<Post?>(null) }
    val scope = rememberCoroutineScope()
    val reduced = LocalReducedMotion.current

    // The pager lives as long as the thread; created once its first page has loaded.
    var pager by remember { mutableStateOf<PagerState?>(null) }
    val current = pager?.let { it.currentPage + 1 } ?: viewModel.currentPage
    val last = info?.lastPage ?: current
    val go: (Int) -> Unit = { target ->
        val p = pager
        val t = target.coerceIn(1, last)
        if (p != null) scope.launch { if (!reduced && abs(p.currentPage + 1 - t) <= 2) p.animateScrollToPage(t - 1) else p.scrollToPage(t - 1) }
    }
    // A jump after a reply may land on a page the pager does not have yet: wait for the new count.
    LaunchedEffect(viewModel) {
        viewModel.jumps.collect { target ->
            val p = snapshotFlow { pager }.filterNotNull().first()
            withTimeoutOrNull(2_000) { snapshotFlow { p.pageCount }.first { it >= target } }
            p.scrollToPage((target - 1).coerceIn(0, (p.pageCount - 1).coerceAtLeast(0)))
        }
    }
    val title = info?.title ?: viewModel.initialTitle
    val pageUrl = info?.let { ForumUrls.thread(it.slug, it.threadId, current) }

    ScreenScaffold(
        title = title,
        onBack = onBack,
        snackbarHost = { MuabetSnackbarHost(snackbar) },
        actions = {
            if (info != null && info.canReply && loggedIn) {
                IconButton(onClick = { onCompose(ForumUrls.reply(info.slug, info.threadId)) }) {
                    Icon(Icons.AutoMirrored.Outlined.Reply, contentDescription = stringResource(R.string.action_reply))
                }
            }
            if (info != null) {
                Box {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.cd_more)) }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, containerColor = floatingGround) {
                        MenuItem(stringResource(R.string.search_in_thread), Icons.Outlined.Search) { menuOpen = false; onSearchInThread(info.threadId, info.title) }
                        if (loggedIn) {
                            MenuItem(
                                stringResource(if (info.watched) R.string.action_unwatch else R.string.action_watch),
                                Icons.Outlined.Notifications,
                            ) { menuOpen = false; onCompose(ForumUrls.watchThread(info.slug, info.threadId)) }
                        }
                        MenuItem(stringResource(R.string.action_save_offline), Icons.Outlined.DownloadForOffline) { menuOpen = false; saveOpen = true }
                        MenuItem(
                            stringResource(if (muted) R.string.action_unmute_thread else R.string.action_mute_thread),
                            Icons.Outlined.NotificationsOff,
                        ) { menuOpen = false; viewModel.toggleMute() }
                        MenuItem(stringResource(R.string.action_share), Icons.Outlined.Share) { menuOpen = false; shareLink(context, pageUrl ?: "", info.title) }
                        MenuItem(stringResource(R.string.action_copy_link), Icons.Outlined.Link) { menuOpen = false; copyText(context, info.title, pageUrl ?: "") }
                        MenuItem(stringResource(R.string.action_open_on_forum), Icons.Outlined.OpenInBrowser) { menuOpen = false; pageUrl?.let(openWeb) }
                    }
                }
            }
        },
        bottomBar = {
            if (info != null) PageBar(current, last, onGo = go, onJump = { jumpOpen = true })
        },
    ) { padding ->
        LoadedContent(infoState, onRetry = { viewModel.open() }, modifier = Modifier.padding(padding)) { ti ->
            key(ti.threadId) {
                val state = rememberPagerState(initialPage = ti.startPage - 1) { ti.lastPage }
                LaunchedEffect(state) { pager = state }
                LaunchedEffect(state) {
                    snapshotFlow { state.settledPage }.collect { viewModel.onPageShown(it + 1) }
                }
                val actions = PostActions(
                    onLink = LocalOpenLink.current,
                    onImage = { image = it },
                    onOpenOnForum = { pageUrl?.let(openWeb) },
                    onAuthor = { member = it },
                    onMore = { postMenu = it },
                )
                HorizontalPager(
                    state = state,
                    key = { it },
                    beyondViewportPageCount = 0,
                    modifier = Modifier.fillMaxSize(),
                ) { index ->
                    ThreadPageContent(
                        page = index + 1,
                        info = ti,
                        viewModel = viewModel,
                        padding = padding,
                        actions = actions,
                        loggedIn = loggedIn,
                        onReply = { onCompose(ForumUrls.reply(ti.slug, ti.threadId)) },
                        onNext = { go(index + 2) },
                    )
                }
            }
        }
    }

    if (jumpOpen) {
        PageJumpSheet(current, last, onJump = { jumpOpen = false; go(it) }, onDismiss = { jumpOpen = false })
    }
    if (saveOpen && info != null) {
        SaveSheet(current, info.lastPage, saving, onSave = { from, to -> saveOpen = false; viewModel.saveOffline(from, to) }, onDismiss = { saveOpen = false })
    }
    image?.let { url -> ImageViewer(url, onDismiss = { image = null }) }
    member?.let { m ->
        MemberSheet(
            author = m,
            loggedIn = loggedIn,
            onProfile = { member = null; m.profileUrl?.let(openWeb) },
            onMessage = { member = null; openWeb(ForumUrls.messageTo(m.name)) },
            onPosts = { member = null; m.userId?.let { onSearchUrl(ForumUrls.postsBy(it)) } },
            onDismiss = { member = null },
        )
    }
    postMenu?.let { post ->
        val canPost = loggedIn && info?.canReply == true
        val postUrl = ForumUrls.post(post.id)
        val actions = buildList {
            if (canPost && info != null) add(SheetAction(stringResource(R.string.action_quote), Icons.Outlined.FormatQuote) { onCompose(ForumUrls.quote(info.slug, info.threadId, post.id)) })
            if (loggedIn) {
                add(SheetAction(stringResource(R.string.action_react), Icons.Outlined.AddReaction) { onCompose(ForumUrls.react(post.id)) })
                add(SheetAction(stringResource(R.string.action_bookmark), Icons.Outlined.BookmarkAdd) { onCompose(ForumUrls.bookmarkPost(post.id)) })
            }
            add(SheetAction(stringResource(R.string.action_copy_text), Icons.Outlined.ContentCopy) { copyText(context, post.author.name, PostText.plain(post.body)) })
            add(SheetAction(stringResource(R.string.action_share), Icons.Outlined.Share) { shareLink(context, postUrl, title) })
            add(SheetAction(stringResource(R.string.action_copy_link), Icons.Outlined.Link) { copyText(context, title, postUrl) })
            add(SheetAction(stringResource(R.string.action_open_on_forum), Icons.Outlined.OpenInBrowser) { openWeb(postUrl) })
        }
        val heading = listOfNotNull(post.author.name, post.number?.let { "#$it" }).joinToString("  ·  ")
        ActionSheet(title = heading, actions = actions, onDismiss = { postMenu = null })
    }
}

@Composable
private fun MenuItem(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        onClick = onClick,
    )
}

/** One page of the pager: its posts, pull to refresh, the remembered position and scroll targets. */
@OptIn(FlowPreview::class)
@Composable
private fun ThreadPageContent(
    page: Int,
    info: ThreadInfo,
    viewModel: ThreadViewModel,
    padding: PaddingValues,
    actions: PostActions,
    loggedIn: Boolean,
    onReply: () -> Unit,
    onNext: () -> Unit,
) {
    val pages by viewModel.pages.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val target by viewModel.target.collectAsStateWithLifecycle()
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    LaunchedEffect(page) { viewModel.ensure(page) }
    val state = pages[page] ?: UiState.Loading
    LoadedContent(state, onRetry = { viewModel.retry(page) }, modifier = Modifier.padding(padding)) { tp ->
        val listState = rememberLazyListState()
        // Scroll to a post asked for (the remembered one, the first unread, a link's post).
        LaunchedEffect(tp, target) {
            val t = target
            if (t != null && t.page == page) {
                val i = tp.posts.indexOfFirst { it.id == t.postId }
                if (i >= 0) listState.scrollToItem(i + 1)
                viewModel.consumeTarget()
            }
        }
        // Remember the first post on screen once scrolling rests.
        LaunchedEffect(listState, tp) {
            snapshotFlow { listState.firstVisibleItemIndex }
                .distinctUntilChanged()
                .debounce(700)
                .collect { index ->
                    if (viewModel.currentPage == page) {
                        viewModel.rememberPosition(page, tp.posts.getOrNull((index - 1).coerceAtLeast(0))?.id)
                    }
                }
        }
        MuabetPullToRefresh(refreshing == page, onRefresh = { viewModel.refresh(page) }, modifier = Modifier.fillMaxSize()) {
            val inset = contentInset()
            LazyColumn(
                Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(start = inset, end = inset, top = padding.calculateTopPadding() + Space.xs, bottom = padding.calculateBottomPadding() + Space.xl),
                verticalArrangement = Arrangement.spacedBy(Space.m),
            ) {
                item(key = "title", contentType = "title") {
                    Column {
                        PageTitle(tp.title, subtitle = tp.forumTitle)
                        if (tp.fromSaved) NoteCard(stringResource(R.string.thread_saved_copy))
                    }
                }
                itemsIndexed(tp.posts, key = { _, p -> p.id }, contentType = { _, _ -> "post" }) { _, post ->
                    PostOrMuted(post, rules, actions, highlighted = false)
                }
                item(key = "end", contentType = "end") {
                    PageEnd(page, info.lastPage, canReply = loggedIn && tp.canReply, onReply = onReply, onNext = onNext)
                }
            }
        }
    }
}

@Composable
private fun PostOrMuted(post: Post, rules: MuteRules, actions: PostActions, highlighted: Boolean) {
    var shown by rememberSaveable(post.id) { mutableStateOf(false) }
    val hidden = remember(post, rules) { rules.hidesPost(post) }
    if (hidden && !shown) {
        MutedPostItem(post.author.name, onShow = { shown = true })
    } else {
        PostItem(post, actions, time = relativeTime(post.timestamp), highlighted = highlighted)
    }
}

/** Under the last post: the next page, or Reply on the thread's last page. */
@Composable
private fun PageEnd(page: Int, last: Int, canReply: Boolean, onReply: () -> Unit, onNext: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = Space.s),
        horizontalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        if (page < last) {
            OutlinedPill(stringResource(R.string.page_next), onNext, Modifier.weight(1f))
        } else if (canReply) {
            PrimaryPill(stringResource(R.string.action_reply), onClick = onReply, modifier = Modifier.weight(1f), icon = Icons.AutoMirrored.Outlined.Reply)
        }
    }
}

@Composable
private fun PageBar(current: Int, last: Int, onGo: (Int) -> Unit, onJump: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(barGround).navigationBarsPadding()) {
        Rule()
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = { onGo(1) }, enabled = current > 1) { Icon(Icons.Rounded.FirstPage, stringResource(R.string.page_first)) }
            IconButton(onClick = { onGo(current - 1) }, enabled = current > 1) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, stringResource(R.string.page_previous))
            }
            Text(
                stringResource(R.string.page_of, current, last),
                style = MaterialTheme.typography.labelLarge.numeric,
                modifier = Modifier
                    .clickable(onClick = onJump, role = Role.Button, onClickLabel = stringResource(R.string.page_jump_title))
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            )
            IconButton(onClick = { onGo(current + 1) }, enabled = current < last) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, stringResource(R.string.page_next))
            }
            IconButton(onClick = { onGo(last) }, enabled = current < last) { Icon(Icons.Rounded.LastPage, stringResource(R.string.page_last)) }
        }
    }
}

/** Choose pages to keep for offline reading: this page, the next ten, or a range. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SaveSheet(current: Int, last: Int, saving: Boolean, onSave: (Int, Int) -> Unit, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var from by rememberSaveable { mutableStateOf(current.toString()) }
    var to by rememberSaveable { mutableStateOf(minOf(last, current + 9).toString()) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = floatingGround) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Space.xl).padding(bottom = Space.xxl),
            verticalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            Text(stringResource(R.string.save_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.save_explain), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedPill(stringResource(R.string.save_this_page), { onSave(current, current) }, Modifier.fillMaxWidth(), enabled = !saving)
            if (current < last) {
                OutlinedPill(stringResource(R.string.save_next_ten), { onSave(current, minOf(last, current + 9)) }, Modifier.fillMaxWidth(), enabled = !saving)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                OutlinedTextField(
                    value = from,
                    onValueChange = { v -> from = v.filter { it.isDigit() }.take(6) },
                    label = { Text(stringResource(R.string.save_from)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = to,
                    onValueChange = { v -> to = v.filter { it.isDigit() }.take(6) },
                    label = { Text(stringResource(R.string.save_to)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }
            val a = from.toIntOrNull()
            val b = to.toIntOrNull()
            PrimaryPill(
                stringResource(R.string.save_range),
                onClick = { if (a != null && b != null) onSave(minOf(a, b), maxOf(a, b)) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !saving && a != null && b != null && a in 1..last && b in 1..last && abs(b - a) < MAX_SAVE_PAGES,
            )
        }
    }
}

private const val MAX_SAVE_PAGES = 200

/** Tap an image to see it whole: pinch or double-tap to zoom, drag to pan, tap or Back to close. */
@Composable
private fun ImageViewer(url: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        val transform = rememberTransformableState { zoom, pan, _ ->
            scale = (scale * zoom).coerceIn(1f, 6f)
            offset = if (scale > 1f) offset + pan else Offset.Zero
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.94f))
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (scale > 1f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                scale = 2.5f
                            }
                        },
                        onTap = { onDismiss() },
                    )
                },
        ) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .transformable(transform)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
            )
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(Space.s)) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.action_close), tint = Color.White)
            }
        }
    }
}
