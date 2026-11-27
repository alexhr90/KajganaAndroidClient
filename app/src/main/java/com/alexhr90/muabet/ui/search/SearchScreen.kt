package com.alexhr90.muabet.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alexhr90.muabet.R
import com.alexhr90.muabet.designsystem.EmptyState
import com.alexhr90.muabet.designsystem.Rule
import com.alexhr90.muabet.designsystem.ScreenScaffold
import com.alexhr90.muabet.designsystem.Space
import com.alexhr90.muabet.designsystem.SwitchRow
import com.alexhr90.muabet.designsystem.pageTitle
import com.alexhr90.muabet.designsystem.screenContent
import com.alexhr90.muabet.domain.model.SearchResult
import com.alexhr90.muabet.ui.common.LoadMoreFooter
import com.alexhr90.muabet.ui.common.LoadedContent
import com.alexhr90.muabet.ui.common.LocalOpenLink
import com.alexhr90.muabet.ui.common.UiState
import com.alexhr90.muabet.ui.common.relativeTime
import com.alexhr90.muabet.ui.forum.nearEnd

@Composable
fun SearchScreen(onBack: () -> Unit, viewModel: SearchViewModel = hiltViewModel()) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val titlesOnly by viewModel.titlesOnly.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val openLink = LocalOpenLink.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    val heading = if (viewModel.threadId != null) stringResource(R.string.search_in_thread) else stringResource(R.string.search_title)
    LaunchedEffect(Unit) { if (!viewModel.isPreset && state == null) runCatching { focus.requestFocus() } }

    ScreenScaffold(title = heading, onBack = onBack) { padding ->
        val listState = rememberLazyListState()
        val nearEnd by listState.nearEnd()
        val loaded = ((state as? UiState.Done)?.result as? com.alexhr90.muabet.data.repo.Loaded.Ok)?.value
        LaunchedEffect(nearEnd, loaded?.items?.size) { if (nearEnd && loaded != null) viewModel.loadMore() }
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = padding.screenContent()) {
            pageTitle(heading, subtitle = viewModel.threadTitle.takeIf { it.isNotBlank() })
            if (!viewModel.isPreset) {
                item(key = "field") {
                    OutlinedTextField(
                        value = query,
                        onValueChange = viewModel::setQuery,
                        label = { Text(stringResource(R.string.search_hint)) },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setQuery("") }) { Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.action_clear)) }
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide(); viewModel.search() }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
                if (viewModel.threadId == null) {
                    item(key = "titles") { SwitchRow(stringResource(R.string.search_titles_only), titlesOnly, viewModel::setTitlesOnly) }
                }
            }
            val s = state
            if (s != null && loaded == null) {
                item(key = "state") {
                    Box(Modifier.fillMaxWidth().heightIn(min = 320.dp)) {
                        LoadedContent(s, onRetry = { viewModel.search() }) { }
                    }
                }
            }
            if (loaded != null) {
                items(loaded.items, key = { it.url }) { r -> ResultRow(r) { openLink(r.url) } }
                if (loaded.items.isEmpty()) {
                    item(key = "none") { EmptyState(stringResource(R.string.search_none_title), stringResource(R.string.search_none_message), icon = Icons.Outlined.SearchOff) }
                }
                if (loaded.loadingMore || loaded.moreFailed) {
                    item(key = "more") { LoadMoreFooter(loaded.loadingMore, loaded.moreFailed) { viewModel.loadMore() } }
                }
            }
        }
    }
}

@Composable
private fun ResultRow(r: SearchResult, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick, role = Role.Button)
                .heightIn(min = 64.dp)
                .padding(vertical = Space.m),
        ) {
            Text(r.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            r.snippet?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            val meta = listOfNotNull(r.author, relativeTime(r.timestamp), r.forumTitle).joinToString("  ·  ")
            if (meta.isNotEmpty()) {
                Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            }
        }
        Rule()
    }
}
