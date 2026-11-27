package com.alexhr90.muabet.ui.messages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alexhr90.muabet.R
import com.alexhr90.muabet.data.repo.Loaded
import com.alexhr90.muabet.designsystem.Avatar
import com.alexhr90.muabet.designsystem.EmptyState
import com.alexhr90.muabet.designsystem.LogoLoader
import com.alexhr90.muabet.designsystem.MuabetPullToRefresh
import com.alexhr90.muabet.designsystem.OutlinedPill
import com.alexhr90.muabet.designsystem.PageTitle
import com.alexhr90.muabet.designsystem.PostActions
import com.alexhr90.muabet.designsystem.PostItem
import com.alexhr90.muabet.designsystem.PrimaryPill
import com.alexhr90.muabet.designsystem.Rule
import com.alexhr90.muabet.designsystem.ScreenScaffold
import com.alexhr90.muabet.designsystem.Space
import com.alexhr90.muabet.designsystem.UnreadDot
import com.alexhr90.muabet.designsystem.barGround
import com.alexhr90.muabet.designsystem.contentInset
import com.alexhr90.muabet.designsystem.pageTitle
import com.alexhr90.muabet.designsystem.screenContent
import com.alexhr90.muabet.domain.model.ConversationSummary
import com.alexhr90.muabet.domain.model.PostAuthor
import com.alexhr90.muabet.domain.url.ForumUrls
import com.alexhr90.muabet.domain.url.Route
import com.alexhr90.muabet.ui.common.LoadMoreFooter
import com.alexhr90.muabet.ui.common.LoadedContent
import com.alexhr90.muabet.ui.common.LocalOpenLink
import com.alexhr90.muabet.ui.common.LocalOpenWeb
import com.alexhr90.muabet.ui.common.LoginNeeded
import com.alexhr90.muabet.ui.common.UiState
import com.alexhr90.muabet.ui.common.downloadFile
import com.alexhr90.muabet.ui.common.relativeTime
import com.alexhr90.muabet.ui.forum.nearEnd
import com.alexhr90.muabet.ui.search.ResultRow

/** Direct messages (v1.1): the conversations, newest first, and New message. */
@Composable
fun ConversationsScreen(
    onBack: () -> Unit,
    onOpen: (ConversationSummary) -> Unit,
    onNew: () -> Unit,
    onLogin: () -> Unit,
    viewModel: ConversationsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val loginNeeded by viewModel.loginNeeded.collectAsStateWithLifecycle()
    val title = stringResource(R.string.direct_messages)
    ScreenScaffold(
        title = title,
        onBack = onBack,
        actions = {
            if (!loginNeeded) IconButton(onClick = onNew) { Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.compose_new_message)) }
        },
    ) { padding ->
        if (loginNeeded) {
            LoginNeeded(Icons.Outlined.Mail, onLogin, Modifier.padding(padding))
            return@ScreenScaffold
        }
        LoadedContent(state, onRetry = { viewModel.load() }, modifier = Modifier.padding(padding)) { list ->
            val listState = rememberLazyListState()
            val nearEnd by listState.nearEnd()
            LaunchedEffect(nearEnd, list.items.size) { if (nearEnd) viewModel.loadMore() }
            MuabetPullToRefresh(refreshing, onRefresh = { viewModel.load(pull = true) }, modifier = Modifier.fillMaxSize()) {
                LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = padding.screenContent()) {
                    pageTitle(title)
                    items(list.items, key = { "c-${it.id}" }) { c -> ConversationRow(c) { onOpen(c) } }
                    if (list.items.isEmpty() && list.nextUrl == null) {
                        item(key = "empty") {
                            EmptyState(title, stringResource(R.string.messages_empty), icon = Icons.Outlined.Mail, actionLabel = stringResource(R.string.compose_new_message), onAction = onNew)
                        }
                    }
                    if (list.loadingMore || list.moreFailed) {
                        item(key = "more") { LoadMoreFooter(list.loadingMore, list.moreFailed) { viewModel.loadMore() } }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationRow(c: ConversationSummary, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick, role = Role.Button).heightIn(min = 64.dp).padding(vertical = Space.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    c.title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = if (c.unread) FontWeight.SemiBold else FontWeight.Normal),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val meta = listOfNotNull(
                    c.participants.joinToString(", ").takeIf { it.isNotEmpty() },
                    relativeTime(c.lastMessageAt),
                ).joinToString("  ·  ")
                if (meta.isNotEmpty()) {
                    Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (c.unread) {
                Spacer(Modifier.width(Space.m))
                UnreadDot()
            }
        }
        Rule()
    }
}

/** One conversation: messages as post cards, earlier pages on demand, Reply at the bottom. */
@Composable
fun ConversationScreen(
    onBack: () -> Unit,
    onReply: (id: Long, slug: String, title: String) -> Unit,
    onProfile: (Long, String) -> Unit,
    refreshRequest: Boolean,
    onRefreshHandled: () -> Unit,
    viewModel: ConversationViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val target by viewModel.target.collectAsStateWithLifecycle()
    val loggedIn by viewModel.loggedIn.collectAsStateWithLifecycle()
    val openLink = LocalOpenLink.current
    val openWeb = LocalOpenWeb.current
    val context = LocalContext.current
    var image by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(refreshRequest) {
        if (refreshRequest) {
            viewModel.refresh()
            onRefreshHandled()
        }
    }
    val view = ((state as? UiState.Done)?.result as? Loaded.Ok)?.value
    val title = view?.title ?: viewModel.initialTitle
    ScreenScaffold(
        title = title,
        onBack = onBack,
        bottomBar = {
            if (view != null && view.canReply && loggedIn) {
                Column(Modifier.fillMaxWidth().padding(bottom = 0.dp).navigationBarsPadding()) {
                    Rule()
                    Box(Modifier.fillMaxWidth().padding(horizontal = contentInset(), vertical = Space.s)) {
                        PrimaryPill(stringResource(R.string.action_reply), onClick = { onReply(view.id, view.slug, view.title) }, modifier = Modifier.fillMaxWidth(), icon = Icons.AutoMirrored.Outlined.Reply)
                    }
                }
            }
        },
    ) { padding ->
        LoadedContent(state, onRetry = { viewModel.retry() }, modifier = Modifier.padding(padding)) { v ->
            val listState = rememberLazyListState()
            LaunchedEffect(v.messages, target) {
                val t = target ?: return@LaunchedEffect
                val i = v.messages.indexOfFirst { it.id == t }
                if (i >= 0) listState.scrollToItem(i + 2)
                viewModel.consumeTarget()
            }
            val actions = PostActions(
                onLink = openLink,
                onImage = { image = it },
                onOpenOnForum = { openWeb(ForumUrls.conversation(v.slug, v.id)) },
                onAuthor = { a -> memberOf(a)?.let { (id, slug) -> onProfile(id, slug) } },
                onDownload = { url, name -> if (!downloadFile(context, url, name)) openWeb(url) },
            )
            val inset = contentInset()
            MuabetPullToRefresh(refreshing, onRefresh = { viewModel.refresh() }, modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(start = inset, end = inset, top = padding.calculateTopPadding() + Space.xs, bottom = padding.calculateBottomPadding() + Space.xl),
                    verticalArrangement = Arrangement.spacedBy(Space.m),
                ) {
                    item(key = "title") { PageTitle(v.title, subtitle = v.participants.joinToString(", ").ifEmpty { null }) }
                    item(key = "earlier") {
                        if (v.firstPage > 1) {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                if (v.loadingEarlier) LogoLoader(Modifier.size(28.dp)) else OutlinedPill(stringResource(R.string.messages_earlier), { viewModel.loadEarlier() })
                            }
                        }
                    }
                    items(v.messages, key = { it.id }) { m -> PostItem(m, actions, time = relativeTime(m.timestamp)) }
                }
            }
        }
    }
    image?.let { url -> com.alexhr90.muabet.ui.thread.ImageViewer(url, onDismiss = { image = null }) }
}

/** The member id and slug of an author, from the user id or the profile link. */
internal fun memberOf(a: PostAuthor): Pair<Long, String>? {
    val route = a.profileUrl?.let(ForumUrls::route) as? Route.Member
    val id = a.userId ?: route?.id ?: return null
    return id to route?.slug.orEmpty()
}

/** The visitor's bookmarks (v1.1: native). */
@Composable
fun BookmarksScreen(onBack: () -> Unit, onLogin: () -> Unit, viewModel: BookmarksViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val loginNeeded by viewModel.loginNeeded.collectAsStateWithLifecycle()
    val openLink = LocalOpenLink.current
    val title = stringResource(R.string.bookmarks)
    ScreenScaffold(title = title, onBack = onBack) { padding ->
        if (loginNeeded) {
            LoginNeeded(Icons.Outlined.Bookmarks, onLogin, Modifier.padding(padding))
            return@ScreenScaffold
        }
        LoadedContent(state, onRetry = { viewModel.load() }, modifier = Modifier.padding(padding)) { list ->
            val listState = rememberLazyListState()
            val nearEnd by listState.nearEnd()
            LaunchedEffect(nearEnd, list.items.size) { if (nearEnd) viewModel.loadMore() }
            MuabetPullToRefresh(refreshing, onRefresh = { viewModel.load(pull = true) }, modifier = Modifier.fillMaxSize()) {
                LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = padding.screenContent()) {
                    pageTitle(title)
                    items(list.items, key = { "b-${it.url}" }) { r -> ResultRow(r) { openLink(r.url) } }
                    if (list.items.isEmpty() && list.nextUrl == null) {
                        item(key = "empty") { EmptyState(title, stringResource(R.string.bookmarks_empty), icon = Icons.Outlined.Bookmarks) }
                    }
                    if (list.loadingMore || list.moreFailed) {
                        item(key = "more") { LoadMoreFooter(list.loadingMore, list.moreFailed) { viewModel.loadMore() } }
                    }
                }
            }
        }
    }
}
