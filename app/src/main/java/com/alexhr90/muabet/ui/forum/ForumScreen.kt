package com.alexhr90.muabet.ui.forum

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alexhr90.muabet.R
import com.alexhr90.muabet.designsystem.EmptyState
import com.alexhr90.muabet.designsystem.ForumRow
import com.alexhr90.muabet.designsystem.LogoLoader
import com.alexhr90.muabet.designsystem.MuabetPullToRefresh
import com.alexhr90.muabet.designsystem.NavigationRow
import com.alexhr90.muabet.designsystem.OutlinedPill
import com.alexhr90.muabet.designsystem.ScreenScaffold
import com.alexhr90.muabet.designsystem.Space
import com.alexhr90.muabet.designsystem.ThreadRow
import com.alexhr90.muabet.designsystem.pageTitle
import com.alexhr90.muabet.designsystem.screenContent
import com.alexhr90.muabet.domain.model.Forum
import com.alexhr90.muabet.domain.model.ThreadSummary
import com.alexhr90.muabet.domain.url.ForumUrls
import com.alexhr90.muabet.ui.common.LoadedContent
import com.alexhr90.muabet.ui.common.LocalOpenWeb
import com.alexhr90.muabet.ui.common.UiState
import com.alexhr90.muabet.data.repo.Loaded
import com.alexhr90.muabet.ui.common.relativeTime

/** A forum: its subforums, pinned threads folded into one row, and the thread list with infinite scroll. */
@Composable
fun ForumScreen(
    onBack: () -> Unit,
    onForum: (Forum) -> Unit,
    onThread: (ThreadSummary, Int) -> Unit,
    viewModel: ForumViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val openWeb = LocalOpenWeb.current
    val title = ((state as? UiState.Done)?.result as? Loaded.Ok)?.value?.first?.title ?: viewModel.initialTitle
    var pinnedOpen by rememberSaveable { mutableStateOf(false) }
    ScreenScaffold(
        title = title,
        onBack = onBack,
        actions = {
            IconButton(onClick = { openWeb(ForumUrls.forum(viewModel.slug, viewModel.forumId)) }) {
                Icon(Icons.Outlined.OpenInBrowser, contentDescription = stringResource(R.string.action_open_on_forum))
            }
        },
    ) { padding ->
        LoadedContent(state, onRetry = { viewModel.load() }, modifier = Modifier.padding(padding)) { list ->
            val listState = rememberLazyListState()
            val nearEnd by remember {
                derivedStateOf {
                    val info = listState.layoutInfo
                    val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                    info.totalItemsCount > 0 && lastVisible >= info.totalItemsCount - 5
                }
            }
            LaunchedEffect(nearEnd, list.loadedPage) { if (nearEnd) viewModel.loadMore() }
            MuabetPullToRefresh(refreshing, onRefresh = { viewModel.load(pull = true) }, modifier = Modifier.fillMaxSize()) {
                LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = padding.screenContent()) {
                    pageTitle(title)
                    items(list.first.subforums, key = { "sub-${it.id}" }) { sub ->
                        ForumRow(title = sub.title, latest = sub.latest?.title, unread = sub.unread, onClick = { onForum(sub) })
                    }
                    if (list.first.sticky.isNotEmpty()) {
                        item(key = "pinned") {
                            NavigationRow(
                                title = stringResource(R.string.pinned_threads),
                                subtitle = pluralStringResource(R.plurals.threads_count, list.first.sticky.size, list.first.sticky.size),
                                icon = Icons.Outlined.PushPin,
                                onClick = { pinnedOpen = !pinnedOpen },
                            )
                        }
                        if (pinnedOpen) {
                            items(list.first.sticky, key = { "s-${it.id}" }) { t -> Thread(t, onThread) }
                        }
                    }
                    items(list.threads, key = { "t-${it.id}" }, contentType = { "thread" }) { t -> Thread(t, onThread) }
                    if (list.threads.isEmpty() && list.first.sticky.isEmpty()) {
                        item(key = "empty") { EmptyState(stringResource(R.string.forum_empty_title), stringResource(R.string.forum_empty_message)) }
                    }
                    if (list.loadingMore) {
                        item(key = "more") {
                            Box(Modifier.fillMaxWidth().padding(Space.l), contentAlignment = Alignment.Center) { LogoLoader(Modifier.size(28.dp)) }
                        }
                    } else if (list.moreFailed) {
                        item(key = "retry") {
                            Box(Modifier.fillMaxWidth().padding(Space.l), contentAlignment = Alignment.Center) {
                                OutlinedPill(stringResource(R.string.action_retry), { viewModel.loadMore() })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Thread(t: ThreadSummary, onThread: (ThreadSummary, Int) -> Unit) {
    val who = t.lastPoster ?: t.author
    val time = relativeTime(t.lastPostAt ?: t.startedAt)
    val meta = listOfNotNull(who, time, t.replies?.let { pluralStringResource(R.plurals.replies_count, it, it) }).joinToString("  ·  ")
    ThreadRow(
        title = t.title,
        meta = meta,
        unread = t.unread,
        prefix = t.prefix,
        sticky = t.sticky,
        locked = t.locked,
        onClick = { onThread(t, 1) },
        pagesLabel = if (t.lastPage > 1) stringResource(R.string.pages_short, t.lastPage) else null,
        onPages = if (t.lastPage > 1) ({ onThread(t, t.lastPage) }) else null,
    )
}
