package com.alexhr90.muabet.ui.forums

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alexhr90.muabet.R
import com.alexhr90.muabet.data.repo.Loaded
import com.alexhr90.muabet.designsystem.ActionSheet
import com.alexhr90.muabet.designsystem.ForumRow
import com.alexhr90.muabet.designsystem.MuabetPullToRefresh
import com.alexhr90.muabet.designsystem.MuabetSnackbarHost
import com.alexhr90.muabet.designsystem.ScreenScaffold
import com.alexhr90.muabet.designsystem.SheetAction
import com.alexhr90.muabet.designsystem.Space
import com.alexhr90.muabet.designsystem.Tone
import com.alexhr90.muabet.designsystem.pageTitle
import com.alexhr90.muabet.designsystem.screenContent
import com.alexhr90.muabet.domain.model.Forum
import com.alexhr90.muabet.domain.model.ForumHome
import com.alexhr90.muabet.domain.url.ForumUrls
import com.alexhr90.muabet.ui.common.LoadedContent
import com.alexhr90.muabet.ui.common.LocalOpenWeb
import com.alexhr90.muabet.ui.common.MessageEffect
import com.alexhr90.muabet.ui.common.UiState
import com.alexhr90.muabet.ui.common.relativeTime

/**
 * The Forums tab (SPEC §4 M1): pinned favourites on top in the user's order, then the forum's
 * categories, foldable and remembered. Long-press a forum to pin, move, hide or open it on the
 * forum. No icons and no counts.
 */
@Composable
fun ForumsScreen(
    onForum: (Forum) -> Unit,
    onSettings: () -> Unit,
    onSearch: () -> Unit,
    onLogin: () -> Unit,
    viewModel: ForumsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val collapsed by viewModel.collapsed.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val hidden by viewModel.hidden.collectAsStateWithLifecycle()
    val openWeb = LocalOpenWeb.current
    val snackbar = remember { SnackbarHostState() }
    MessageEffect(viewModel.messages, snackbar)
    var menuFor by remember { mutableStateOf<Forum?>(null) }
    val heading = stringResource(R.string.tab_forums)

    ScreenScaffold(
        title = heading,
        onOpenSettings = onSettings,
        snackbarHost = { MuabetSnackbarHost(snackbar) },
        actions = {
            IconButton(onClick = onSearch) { Icon(Icons.Outlined.Search, contentDescription = stringResource(R.string.search_title)) }
            val home = (state as? UiState.Done)?.result as? Loaded.Ok
            if (home != null && !home.value.session.loggedIn) {
                IconButton(onClick = onLogin) {
                    Icon(Icons.AutoMirrored.Outlined.Login, contentDescription = stringResource(R.string.action_log_in))
                }
            }
        },
    ) { padding ->
        LoadedContent(state, onRetry = { viewModel.load() }, modifier = Modifier.padding(padding)) { home ->
            val byId = remember(home) { home.allForums() }
            val hiddenIds = remember(hidden) { hidden.mapTo(HashSet()) { it.id } }
            val longPressLabel = stringResource(R.string.cd_forum_actions)
            MuabetPullToRefresh(refreshing, onRefresh = { viewModel.load(pull = true) }, modifier = Modifier.fillMaxSize()) {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = padding.screenContent()) {
                    pageTitle(heading, hero = true)
                    if (favorites.isNotEmpty()) {
                        item(key = "cat-pinned", contentType = "category") {
                            CategoryHeader(stringResource(R.string.forums_pinned), folded = false, onToggle = null)
                        }
                        items(favorites, key = { "fav-${it.id}" }, contentType = { "forum" }) { fav ->
                            val live = byId[fav.id]
                            val f = live ?: Forum(id = fav.id, slug = fav.slug, title = fav.title, url = ForumUrls.forum(fav.slug, fav.id))
                            ForumLine(f, onClick = { onForum(f) }, onLongClick = { menuFor = f }, longPressLabel = longPressLabel)
                        }
                    }
                    home.categories.forEachIndexed { index, category ->
                        val key = category.id?.toString() ?: "c$index"
                        val folded = key in collapsed
                        val forums = category.forums.filterNot { it.id in hiddenIds }
                        if (category.title.isNotEmpty()) {
                            item(key = "cat-$key", contentType = "category") {
                                CategoryHeader(category.title, folded) { viewModel.toggle(key) }
                            }
                        }
                        if (!folded) {
                            items(forums, key = { "f-$key-${it.id}" }, contentType = { "forum" }) { forum ->
                                ForumLine(forum, onClick = { onForum(forum) }, onLongClick = { menuFor = forum }, longPressLabel = longPressLabel)
                            }
                        }
                    }
                }
            }
        }
    }

    menuFor?.let { f ->
        val index = favorites.indexOfFirst { it.id == f.id }
        val pinned = index >= 0
        val actions = buildList {
            if (pinned) {
                add(SheetAction(stringResource(R.string.action_unpin), Icons.Outlined.PushPin) { viewModel.unpin(f.id) })
                if (index > 0) add(SheetAction(stringResource(R.string.action_move_up), Icons.Outlined.ArrowUpward) { viewModel.move(f.id, -1) })
                if (index < favorites.lastIndex) add(SheetAction(stringResource(R.string.action_move_down), Icons.Outlined.ArrowDownward) { viewModel.move(f.id, 1) })
            } else {
                add(SheetAction(stringResource(R.string.action_pin), Icons.Outlined.PushPin) { viewModel.pin(f) })
            }
            add(SheetAction(stringResource(R.string.action_hide_forum), Icons.Outlined.VisibilityOff, Tone.Negative) { viewModel.hide(f) })
            add(SheetAction(stringResource(R.string.action_open_on_forum), Icons.Outlined.OpenInBrowser) { openWeb(f.url) })
        }
        ActionSheet(title = f.title, actions = actions, onDismiss = { menuFor = null })
    }
}

@Composable
private fun ForumLine(forum: Forum, onClick: () -> Unit, onLongClick: () -> Unit, longPressLabel: String) {
    val latest = forum.latest?.let { l -> listOfNotNull(l.title, relativeTime(l.timestamp)).joinToString("  ·  ") }
    ForumRow(
        title = forum.title,
        latest = latest ?: forum.description,
        unread = forum.unread,
        onClick = onClick,
        onLongClick = onLongClick,
        onLongClickLabel = longPressLabel,
    )
}

/** Every forum on the home page by id, subforums included, for the pinned rows' latest thread. */
private fun ForumHome.allForums(): Map<Long, Forum> = buildMap {
    categories.forEach { c -> c.forums.forEach { f -> put(f.id, f) } }
}

@Composable
private fun CategoryHeader(title: String, folded: Boolean, onToggle: (() -> Unit)?) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onToggle != null) Modifier.clickable(onClick = onToggle, role = Role.Button) else Modifier)
            .heightIn(min = 48.dp)
            .padding(top = Space.l, bottom = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (onToggle != null) {
            Icon(
                if (folded) Icons.Rounded.KeyboardArrowDown else Icons.Rounded.KeyboardArrowUp,
                contentDescription = stringResource(if (folded) R.string.cd_expand else R.string.cd_collapse),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
