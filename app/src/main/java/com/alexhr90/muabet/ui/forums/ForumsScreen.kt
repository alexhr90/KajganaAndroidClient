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
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.alexhr90.muabet.designsystem.ForumRow
import com.alexhr90.muabet.designsystem.MuabetPullToRefresh
import com.alexhr90.muabet.designsystem.ScreenScaffold
import com.alexhr90.muabet.designsystem.Space
import com.alexhr90.muabet.designsystem.pageTitle
import com.alexhr90.muabet.designsystem.screenContent
import com.alexhr90.muabet.domain.model.Forum
import com.alexhr90.muabet.data.repo.Loaded
import com.alexhr90.muabet.ui.common.LoadedContent
import com.alexhr90.muabet.ui.common.UiState
import com.alexhr90.muabet.ui.common.relativeTime

/** The Forums tab (SPEC §4 M1): categories with compact forum rows, no icons or counts. */
@Composable
fun ForumsScreen(
    onForum: (Forum) -> Unit,
    onSettings: () -> Unit,
    onLogin: () -> Unit,
    reloadKey: Long = 0L,
    viewModel: ForumsViewModel = hiltViewModel(),
) {
    LaunchedEffect(reloadKey) { if (reloadKey > 0) viewModel.load(pull = true) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val collapsed by viewModel.collapsed.collectAsStateWithLifecycle()
    ScreenScaffold(
        title = stringResource(R.string.tab_forums),
        onOpenSettings = onSettings,
        actions = {
            val home = (state as? UiState.Done)?.result as? Loaded.Ok
            if (home != null && !home.value.session.loggedIn) {
                IconButton(onClick = onLogin) {
                    Icon(Icons.AutoMirrored.Outlined.Login, contentDescription = stringResource(R.string.action_log_in))
                }
            }
        },
    ) { padding ->
        LoadedContent(state, onRetry = { viewModel.load() }, modifier = Modifier.padding(padding)) { home ->
            MuabetPullToRefresh(refreshing, onRefresh = { viewModel.load(pull = true) }, modifier = Modifier.fillMaxSize()) {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = padding.screenContent()) {
                    pageTitle(stringResource(R.string.tab_forums), hero = true)
                    home.categories.forEachIndexed { index, category ->
                        val key = category.id?.toString() ?: "c$index"
                        val folded = key in collapsed
                        if (category.title.isNotEmpty()) {
                            item(key = "cat-$key", contentType = "category") {
                                CategoryHeader(category.title, folded) { viewModel.toggle(key) }
                            }
                        }
                        if (!folded) {
                            items(category.forums, key = { "f-${it.id}" }, contentType = { "forum" }) { forum ->
                                val latest = forum.latest?.let { l ->
                                    listOfNotNull(l.title, relativeTime(l.timestamp)).joinToString("  ·  ")
                                }
                                ForumRow(title = forum.title, latest = latest ?: forum.description, unread = forum.unread, onClick = { onForum(forum) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryHeader(title: String, folded: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle, role = Role.Button)
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
        Icon(
            if (folded) Icons.Rounded.KeyboardArrowDown else Icons.Rounded.KeyboardArrowUp,
            contentDescription = stringResource(if (folded) R.string.cd_expand else R.string.cd_collapse),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
