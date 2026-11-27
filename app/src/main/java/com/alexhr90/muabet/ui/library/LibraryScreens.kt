package com.alexhr90.muabet.ui.library

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alexhr90.muabet.R
import com.alexhr90.muabet.designsystem.EmptyState
import com.alexhr90.muabet.designsystem.NavigationRow
import com.alexhr90.muabet.designsystem.NoteCard
import com.alexhr90.muabet.designsystem.PrimaryPill
import com.alexhr90.muabet.designsystem.Rule
import com.alexhr90.muabet.designsystem.ScreenScaffold
import com.alexhr90.muabet.designsystem.SectionHeader
import com.alexhr90.muabet.designsystem.Space
import com.alexhr90.muabet.designsystem.pageTitle
import com.alexhr90.muabet.designsystem.screenContent

/** Forums hidden from the Forums tab and from New; a tap brings one back. */
@Composable
fun HiddenForumsScreen(onBack: () -> Unit, viewModel: LibraryViewModel = hiltViewModel()) {
    val hidden by viewModel.hidden.collectAsStateWithLifecycle()
    val heading = stringResource(R.string.hidden_forums)
    ScreenScaffold(title = heading, onBack = onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding.screenContent()) {
            pageTitle(heading)
            item { NoteCard(stringResource(R.string.hidden_forums_explain)) }
            val list = hidden.orEmpty()
            items(list, key = { it.id }) { f ->
                RemovableRow(f.title, null, stringResource(R.string.action_unhide), Icons.Outlined.Visibility) { viewModel.unhide(f.id) }
            }
            if (hidden != null && list.isEmpty()) {
                item { EmptyState(stringResource(R.string.hidden_forums_none), stringResource(R.string.hidden_forums_none_hint), icon = Icons.Outlined.VisibilityOff) }
            }
        }
    }
}

/** Muted threads and keywords (SPEC §4 M3): what matches never appears in lists, posts fold away. */
@Composable
fun MutesScreen(onBack: () -> Unit, viewModel: LibraryViewModel = hiltViewModel()) {
    val threads by viewModel.mutedThreads.collectAsStateWithLifecycle()
    val keywords by viewModel.keywords.collectAsStateWithLifecycle()
    var typed by rememberSaveable { mutableStateOf("") }
    val heading = stringResource(R.string.mutes_title)
    val add = {
        if (typed.isNotBlank()) {
            viewModel.addKeyword(typed)
            typed = ""
        }
    }
    ScreenScaffold(title = heading, onBack = onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding.screenContent()) {
            pageTitle(heading)
            item { NoteCard(stringResource(R.string.mutes_explain)) }
            item { SectionHeader(stringResource(R.string.mutes_keywords)) }
            item {
                Row(Modifier.fillMaxWidth().padding(vertical = Space.s), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    OutlinedTextField(
                        value = typed,
                        onValueChange = { typed = it.take(60) },
                        label = { Text(stringResource(R.string.mutes_add_hint)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { add() }),
                        modifier = Modifier.weight(1f),
                    )
                    PrimaryPill(stringResource(R.string.action_add), onClick = add, enabled = typed.isNotBlank())
                }
            }
            items(keywords.orEmpty(), key = { "k-${it.keyword}" }) { k ->
                RemovableRow(k.keyword, null, stringResource(R.string.action_remove), Icons.Rounded.Close) { viewModel.removeKeyword(k.keyword) }
            }
            item { SectionHeader(stringResource(R.string.mutes_threads)) }
            val list = threads.orEmpty()
            items(list, key = { "t-${it.id}" }) { t ->
                RemovableRow(t.title, null, stringResource(R.string.action_unmute_thread), Icons.Rounded.Close) { viewModel.unmute(t.id) }
            }
            if (threads != null && list.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.mutes_threads_none),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = Space.m),
                    )
                }
            }
        }
    }
}

/** Threads saved for offline reading; a tap opens the first saved page, the bin deletes it. */
@Composable
fun SavedScreen(onBack: () -> Unit, onOpen: (Long, String, Int, String) -> Unit, viewModel: LibraryViewModel = hiltViewModel()) {
    val saved by viewModel.savedThreads.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val heading = stringResource(R.string.saved_title)
    ScreenScaffold(title = heading, onBack = onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding.screenContent()) {
            pageTitle(heading)
            item { NoteCard(stringResource(R.string.saved_explain)) }
            val list = saved.orEmpty()
            items(list, key = { it.threadId }) { t ->
                NavigationRow(
                    title = t.title,
                    subtitle = stringResource(R.string.saved_row, t.pages, Formatter.formatShortFileSize(context, t.bytes)),
                    icon = null,
                    onClick = { onOpen(t.threadId, t.slug, t.firstPage, t.title) },
                    trailing = {
                        IconButton(onClick = { viewModel.deleteSaved(t.threadId) }) {
                            Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.action_delete))
                        }
                    },
                )
            }
            if (saved != null && list.isEmpty()) {
                item { EmptyState(stringResource(R.string.saved_none), stringResource(R.string.saved_none_hint), icon = Icons.Outlined.DownloadForOffline) }
            }
        }
    }
}

@Composable
private fun RemovableRow(title: String, subtitle: String?, actionLabel: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onAction: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(vertical = Space.s)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onAction) { Icon(icon, contentDescription = actionLabel, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Rule()
    }
}
