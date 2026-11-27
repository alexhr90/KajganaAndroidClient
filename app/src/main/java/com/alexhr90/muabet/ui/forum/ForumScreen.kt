package com.alexhr90.muabet.ui.forum

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LastPage
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.rounded.FirstPage
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alexhr90.muabet.R
import com.alexhr90.muabet.data.repo.Loaded
import com.alexhr90.muabet.designsystem.ActionSheet
import com.alexhr90.muabet.designsystem.EmptyState
import com.alexhr90.muabet.designsystem.ForumRow
import com.alexhr90.muabet.designsystem.MuabetPullToRefresh
import com.alexhr90.muabet.designsystem.MuabetSnackbarHost
import com.alexhr90.muabet.designsystem.NavigationRow
import com.alexhr90.muabet.designsystem.ScreenScaffold
import com.alexhr90.muabet.designsystem.SheetAction
import com.alexhr90.muabet.designsystem.ThreadRow
import com.alexhr90.muabet.designsystem.Tone
import com.alexhr90.muabet.designsystem.pageTitle
import com.alexhr90.muabet.designsystem.screenContent
import com.alexhr90.muabet.domain.model.Forum
import com.alexhr90.muabet.domain.model.ThreadSummary
import com.alexhr90.muabet.domain.url.ForumUrls
import com.alexhr90.muabet.ui.common.LoadMoreFooter
import com.alexhr90.muabet.ui.common.LoadedContent
import com.alexhr90.muabet.ui.common.LocalOpenWeb
import com.alexhr90.muabet.ui.common.MessageEffect
import com.alexhr90.muabet.ui.common.UiState
import com.alexhr90.muabet.ui.common.copyText
import com.alexhr90.muabet.ui.common.relativeTime
import com.alexhr90.muabet.ui.common.shareLink

/** Opens a thread: [page] 0 resumes where the user stopped (or the first unread post). */
typealias OpenThread = (ThreadSummary, Int) -> Unit

/** A forum: its subforums, pinned threads folded into one row, and the thread list with infinite scroll. */
@Composable
fun ForumScreen(
    onBack: () -> Unit,
    onForum: (Forum) -> Unit,
    onThread: OpenThread,
    onNewThread: (com.alexhr90.muabet.ui.compose.ComposeTarget) -> Unit,
    viewModel: ForumViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    val loggedIn by viewModel.loggedIn.collectAsStateWithLifecycle()
    val openWeb = LocalOpenWeb.current
    val title = ((state as? UiState.Done)?.result as? Loaded.Ok)?.value?.first?.title ?: viewModel.initialTitle
    var pinnedOpen by rememberSaveable { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<ThreadSummary?>(null) }
    val snackbar = remember { SnackbarHostState() }
    MessageEffect(viewModel.messages, snackbar)
    ScreenScaffold(
        title = title,
        onBack = onBack,
        snackbarHost = { MuabetSnackbarHost(snackbar) },
        actions = {
            if (loggedIn) {
                IconButton(onClick = { onNewThread(com.alexhr90.muabet.ui.compose.ComposeTarget(com.alexhr90.muabet.ui.compose.ComposeKind.Thread, viewModel.forumId, viewModel.slug, title)) }) {
                    Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.action_new_thread))
                }
            }
            IconButton(onClick = { openWeb(ForumUrls.forum(viewModel.slug, viewModel.forumId)) }) {
                Icon(Icons.Outlined.OpenInBrowser, contentDescription = stringResource(R.string.action_open_on_forum))
            }
        },
    ) { padding ->
        LoadedContent(state, onRetry = { viewModel.load() }, modifier = Modifier.padding(padding)) { list ->
            val listState = rememberLazyListState()
            val nearEnd by listState.nearEnd()
            LaunchedEffect(nearEnd, list.loadedPage) { if (nearEnd) viewModel.loadMore() }
            val sticky = remember(list.first.sticky, rules) { rules.filter(list.first.sticky) }
            val threads = remember(list.threads, rules) { rules.filter(list.threads) }
            val longPressLabel = stringResource(R.string.cd_thread_actions)
            MuabetPullToRefresh(refreshing, onRefresh = { viewModel.load(pull = true) }, modifier = Modifier.fillMaxSize()) {
                LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = padding.screenContent()) {
                    pageTitle(title)
                    items(list.first.subforums, key = { "sub-${it.id}" }) { sub ->
                        ForumRow(title = sub.title, latest = sub.latest?.title, unread = sub.unread, onClick = { onForum(sub) })
                    }
                    if (sticky.isNotEmpty()) {
                        item(key = "pinned") {
                            NavigationRow(
                                title = stringResource(R.string.pinned_threads),
                                subtitle = pluralStringResource(R.plurals.threads_count, sticky.size, sticky.size),
                                icon = Icons.Outlined.PushPin,
                                onClick = { pinnedOpen = !pinnedOpen },
                            )
                        }
                        if (pinnedOpen) {
                            items(sticky, key = { "s-${it.id}" }) { t -> ThreadLine(t, onThread, { menuFor = t }, longPressLabel) }
                        }
                    }
                    items(threads, key = { "t-${it.id}" }, contentType = { "thread" }) { t -> ThreadLine(t, onThread, { menuFor = t }, longPressLabel) }
                    if (threads.isEmpty() && sticky.isEmpty() && !list.hasMore) {
                        item(key = "empty") { EmptyState(stringResource(R.string.forum_empty_title), stringResource(R.string.forum_empty_message)) }
                    }
                    if (list.loadingMore || list.moreFailed) {
                        item(key = "more") { LoadMoreFooter(list.loadingMore, list.moreFailed) { viewModel.loadMore() } }
                    }
                }
            }
        }
    }
    menuFor?.let { t -> ThreadMenu(t, onThread, onMute = { viewModel.mute(t) }, onDismiss = { menuFor = null }) }
}

/** Long-press on a thread: first or last page, mute, share, open on the forum. */
@Composable
fun ThreadMenu(t: ThreadSummary, onThread: OpenThread, onMute: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val openWeb = LocalOpenWeb.current
    val actions = buildList {
        add(SheetAction(stringResource(R.string.page_first), Icons.Rounded.FirstPage) { onThread(t, 1) })
        if (t.lastPage > 1) add(SheetAction(stringResource(R.string.page_last), Icons.Rounded.LastPage) { onThread(t, t.lastPage) })
        add(SheetAction(stringResource(R.string.action_share), Icons.Outlined.Share) { shareLink(context, t.url, t.title) })
        add(SheetAction(stringResource(R.string.action_copy_link), Icons.Outlined.Link) { copyText(context, t.title, t.url) })
        add(SheetAction(stringResource(R.string.action_open_on_forum), Icons.Outlined.OpenInBrowser) { openWeb(t.url) })
        add(SheetAction(stringResource(R.string.action_mute_thread), Icons.Outlined.NotificationsOff, Tone.Negative, onMute))
    }
    ActionSheet(title = t.title, actions = actions, onDismiss = onDismiss)
}

@Composable
fun ThreadLine(t: ThreadSummary, onThread: OpenThread, onLongClick: () -> Unit, longPressLabel: String, showForum: Boolean = false) {
    val who = t.lastPoster ?: t.author
    val time = relativeTime(t.lastPostAt ?: t.startedAt)
    val meta = listOfNotNull(
        if (showForum) t.forumTitle else null,
        who,
        time,
        t.replies?.let { pluralStringResource(R.plurals.replies_count, it, it) },
    ).joinToString("  ·  ")
    ThreadRow(
        title = t.title,
        meta = meta,
        unread = t.unread,
        prefix = t.prefix,
        sticky = t.sticky,
        locked = t.locked,
        onClick = { onThread(t, 0) },
        pagesLabel = if (t.lastPage > 1) stringResource(R.string.pages_short, t.lastPage) else null,
        onPages = if (t.lastPage > 1) ({ onThread(t, t.lastPage) }) else null,
        onLongClick = onLongClick,
        onLongClickLabel = longPressLabel,
    )
}

/** True when the list is within [threshold] items of its end: time to load the next page. */
@Composable
fun LazyListState.nearEnd(threshold: Int = 5): State<Boolean> = remember(this) {
    derivedStateOf {
        val info = layoutInfo
        val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
        info.totalItemsCount > 0 && lastVisible >= info.totalItemsCount - threshold
    }
}
