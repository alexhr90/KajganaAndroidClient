package com.alexhr90.muabet.ui.member

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alexhr90.muabet.R
import com.alexhr90.muabet.designsystem.Avatar
import com.alexhr90.muabet.designsystem.BlockList
import com.alexhr90.muabet.designsystem.InfoRow
import com.alexhr90.muabet.designsystem.OutlinedPill
import com.alexhr90.muabet.designsystem.PostActions
import com.alexhr90.muabet.designsystem.PrimaryPill
import com.alexhr90.muabet.designsystem.ScreenScaffold
import com.alexhr90.muabet.designsystem.SectionCard
import com.alexhr90.muabet.designsystem.Space
import com.alexhr90.muabet.designsystem.contentInset
import com.alexhr90.muabet.domain.url.ForumUrls
import com.alexhr90.muabet.ui.common.LoadedContent
import com.alexhr90.muabet.ui.common.LocalOpenLink
import com.alexhr90.muabet.ui.common.LocalOpenWeb

/** A member's profile (v1.1: native): picture, name, title, the forum's figures, about, and actions. */
@Composable
fun MemberScreen(
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
    onPosts: (Long) -> Unit,
    viewModel: MemberViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loggedIn by viewModel.loggedIn.collectAsStateWithLifecycle()
    val openWeb = LocalOpenWeb.current
    val openLink = LocalOpenLink.current
    ScreenScaffold(
        title = stringResource(R.string.member_profile),
        onBack = onBack,
        actions = {
            IconButton(onClick = { openWeb(ForumUrls.member("", viewModel.id)) }) {
                Icon(Icons.Outlined.OpenInBrowser, contentDescription = stringResource(R.string.action_open_on_forum))
            }
        },
    ) { padding ->
        LoadedContent(state, onRetry = { viewModel.load() }, modifier = Modifier.padding(padding)) { m ->
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(padding)
                    .padding(horizontal = contentInset())
                    .padding(bottom = Space.xxl),
                verticalArrangement = Arrangement.spacedBy(Space.l),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Space.m)) {
                    Avatar(m.avatarUrl, m.name, size = 88.dp)
                    Spacer(Modifier.width(Space.l))
                    Column(Modifier.weight(1f)) {
                        Text(m.name, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        m.title?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
                        m.blurb.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s), modifier = Modifier.fillMaxWidth()) {
                    if (loggedIn && m.canMessage) PrimaryPill(stringResource(R.string.member_message), onClick = { onMessage(m.name) }, modifier = Modifier.weight(1f))
                    OutlinedPill(stringResource(R.string.member_posts), { onPosts(m.id) }, Modifier.weight(1f))
                }
                if (m.stats.isNotEmpty()) {
                    SectionCard {
                        m.stats.forEach { (label, value) -> InfoRow(label, value) }
                    }
                }
                if (m.about.isNotEmpty()) {
                    Text(stringResource(R.string.member_about), style = MaterialTheme.typography.titleMedium)
                    BlockList(m.about, PostActions(onLink = openLink, onImage = openLink, onOpenOnForum = { openWeb(ForumUrls.member(m.slug, m.id)) }))
                }
            }
        }
    }
}
