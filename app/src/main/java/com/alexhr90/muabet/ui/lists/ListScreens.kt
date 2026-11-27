package com.alexhr90.muabet.ui.lists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alexhr90.muabet.R
import com.alexhr90.muabet.designsystem.Avatar
import com.alexhr90.muabet.designsystem.EmptyState
import com.alexhr90.muabet.designsystem.MuabetPullToRefresh
import com.alexhr90.muabet.designsystem.MuabetSnackbarHost
import com.alexhr90.muabet.designsystem.Rule
import com.alexhr90.muabet.designsystem.ScreenScaffold
import com.alexhr90.muabet.designsystem.Space
import com.alexhr90.muabet.designsystem.UnreadDot
import com.alexhr90.muabet.designsystem.pageTitle
import com.alexhr90.muabet.designsystem.screenContent
import com.alexhr90.muabet.domain.model.Alert
import com.alexhr90.muabet.domain.model.ThreadSummary
import com.alexhr90.muabet.domain.url.ForumUrls
import com.alexhr90.muabet.ui.common.LoadMoreFooter
import com.alexhr90.muabet.ui.common.LoadedContent
import com.alexhr90.muabet.ui.common.LocalOpenLink
import com.alexhr90.muabet.ui.common.LocalOpenWeb
import com.alexhr90.muabet.ui.common.LoginNeeded
import com.alexhr90.muabet.ui.common.MessageEffect
import com.alexhr90.muabet.ui.common.relativeTime
import com.alexhr90.muabet.ui.forum.OpenThread
import com.alexhr90.muabet.ui.forum.ThreadLine
import com.alexhr90.muabet.ui.forum.ThreadMenu
import com.alexhr90.muabet.ui.forum.nearEnd

/** The New tab (SPEC §4 M2): new posts across the forum, opening at the first unread post. */
@Composable
fun NewScreen(onThread: OpenThread, onSettings: () -> Unit, onSearch: () -> Unit, viewModel: NewViewModel = hiltViewModel()) {
    ThreadListScreen(
        title = stringResource(R.string.tab_new),
        icon = Icons.Outlined.NewReleases,
        emptyMessage = stringResource(R.string.new_empty),
        webUrl = ForumUrls.whatsNewPosts,
        viewModel = viewModel,
        onThread = onThread,
        onSettings = onSettings,
        onSearch = onSearch,
        onLogin = {},
        hideForums = true,
    )
}

/** The Watched tab: watched threads, opening at the first unread post. */
@Composable
fun WatchedScreen(onThread: OpenThread, onSettings: () -> Unit, onSearch: () -> Unit, onLogin: () -> Unit, viewModel: WatchedViewModel = hiltViewModel()) {
    ThreadListScreen(
        title = stringResource(R.string.tab_watched),
        icon = Icons.Outlined.Bookmarks,
        emptyMessage = stringResource(R.string.watched_empty),
        webUrl = ForumUrls.watchedThreads,
        viewModel = viewModel,
        onThread = onThread,
        onSettings = onSettings,
        onSearch = onSearch,
        onLogin = onLogin,
        hideForums = false,
    )
}

@Composable
private fun ThreadListScreen(
    title: String,
    icon: ImageVector,
    emptyMessage: String,
    webUrl: String,
    viewModel: ThreadListViewModel,
    onThread: OpenThread,
    onSettings: () -> Unit,
    onSearch: () -> Unit,
    onLogin: () -> Unit,
    hideForums: Boolean,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val loginNeeded by viewModel.loginNeeded.collectAsStateWithLifecycle()
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    val openWeb = LocalOpenWeb.current
    val snackbar = remember { SnackbarHostState() }
    MessageEffect(viewModel.messages, snackbar)
    var menuFor by remember { mutableStateOf<ThreadSummary?>(null) }
    ScreenScaffold(
        title = title,
        onOpenSettings = onSettings,
        snackbarHost = { MuabetSnackbarHost(snackbar) },
        actions = {
            IconButton(onClick = onSearch) { Icon(Icons.Outlined.Search, contentDescription = stringResource(R.string.search_title)) }
            IconButton(onClick = { openWeb(webUrl) }) { Icon(Icons.Outlined.OpenInBrowser, contentDescription = stringResource(R.string.action_open_on_forum)) }
        },
    ) { padding ->
        if (loginNeeded) {
            LoginNeeded(icon, onLogin, Modifier.padding(padding))
            return@ScreenScaffold
        }
        LoadedContent(state, onRetry = { viewModel.load() }, modifier = Modifier.padding(padding)) { list ->
            val listState = rememberLazyListState()
            val nearEnd by listState.nearEnd()
            LaunchedEffect(nearEnd, list.items.size) { if (nearEnd) viewModel.loadMore() }
            // Muted threads never appear; in New, neither do hidden forums (MuteRules covers both).
            val threads = remember(list.items, rules, hideForums) {
                if (hideForums) rules.filter(list.items) else list.items.filterNot { it.id in rules.threadIds || rules.matchesKeyword(it.title) }
            }
            val longPressLabel = stringResource(R.string.cd_thread_actions)
            MuabetPullToRefresh(refreshing, onRefresh = { viewModel.load(pull = true) }, modifier = Modifier.fillMaxSize()) {
                LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = padding.screenContent()) {
                    pageTitle(title, hero = true)
                    items(threads, key = { "t-${it.id}" }, contentType = { "thread" }) { t ->
                        ThreadLine(t, onThread, { menuFor = t }, longPressLabel, showForum = true)
                    }
                    if (threads.isEmpty() && list.nextUrl == null) {
                        item(key = "empty") { EmptyState(title, emptyMessage, icon = icon) }
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

/** The Alerts tab: the forum's alerts, newest first; a tap opens what the alert is about. */
@Composable
fun AlertsScreen(onSettings: () -> Unit, onLogin: () -> Unit, onMessages: () -> Unit, viewModel: AlertsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val loginNeeded by viewModel.loginNeeded.collectAsStateWithLifecycle()
    val openLink = LocalOpenLink.current
    val openWeb = LocalOpenWeb.current
    val title = stringResource(R.string.tab_alerts)
    ScreenScaffold(
        title = title,
        onOpenSettings = onSettings,
        actions = {
            if (!loginNeeded) {
                val unread by viewModel.messagesUnread.collectAsStateWithLifecycle()
                IconButton(onClick = onMessages) {
                    if (unread > 0) {
                        androidx.compose.material3.BadgedBox(badge = { androidx.compose.material3.Badge { Text(if (unread > 99) "99+" else "$unread") } }) {
                            Icon(Icons.Outlined.Mail, contentDescription = stringResource(R.string.cd_unread_messages, unread))
                        }
                    } else {
                        Icon(Icons.Outlined.Mail, contentDescription = stringResource(R.string.direct_messages))
                    }
                }
            }
        },
    ) { padding ->
        if (loginNeeded) {
            LoginNeeded(Icons.Outlined.Notifications, onLogin, Modifier.padding(padding))
            return@ScreenScaffold
        }
        LoadedContent(state, onRetry = { viewModel.load() }, modifier = Modifier.padding(padding)) { list ->
            val listState = rememberLazyListState()
            val nearEnd by listState.nearEnd()
            LaunchedEffect(nearEnd, list.items.size) { if (nearEnd) viewModel.loadMore() }
            MuabetPullToRefresh(refreshing, onRefresh = { viewModel.load(pull = true) }, modifier = Modifier.fillMaxSize()) {
                LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = padding.screenContent()) {
                    pageTitle(title, hero = true)
                    items(list.items, key = { "a-${it.id}" }) { a -> AlertRow(a) { a.url?.let(openLink) } }
                    if (list.items.isEmpty() && list.nextUrl == null) {
                        item(key = "empty") { EmptyState(title, stringResource(R.string.alerts_empty), icon = Icons.Outlined.Notifications) }
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
private fun AlertRow(alert: Alert, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(enabled = alert.url != null, onClick = onClick, role = Role.Button)
                .heightIn(min = 64.dp)
                .padding(vertical = Space.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(alert.avatarUrl, alert.author ?: "?")
            Spacer(Modifier.width(Space.m))
            Column(Modifier.weight(1f)) {
                Text(
                    alert.text,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = if (alert.unread) FontWeight.SemiBold else FontWeight.Normal),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
                relativeTime(alert.timestamp)?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (alert.unread) {
                Spacer(Modifier.width(Space.m))
                UnreadDot()
            }
        }
        Rule()
    }
}
