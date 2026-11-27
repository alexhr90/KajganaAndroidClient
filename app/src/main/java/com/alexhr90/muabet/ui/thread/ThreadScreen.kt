package com.alexhr90.muabet.ui.thread

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.rounded.FirstPage
import androidx.compose.material.icons.rounded.LastPage
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.alexhr90.muabet.R
import com.alexhr90.muabet.data.repo.Loaded
import com.alexhr90.muabet.designsystem.PageJumpSheet
import com.alexhr90.muabet.designsystem.PostActions
import com.alexhr90.muabet.designsystem.PostItem
import com.alexhr90.muabet.designsystem.Rule
import com.alexhr90.muabet.designsystem.ScreenScaffold
import com.alexhr90.muabet.designsystem.barGround
import com.alexhr90.muabet.designsystem.numeric
import com.alexhr90.muabet.designsystem.pageTitle
import com.alexhr90.muabet.designsystem.screenContent
import com.alexhr90.muabet.domain.url.ForumUrls
import com.alexhr90.muabet.ui.common.LoadedContent
import com.alexhr90.muabet.ui.common.LocalOpenLink
import com.alexhr90.muabet.ui.common.LocalOpenWeb
import com.alexhr90.muabet.ui.common.UiState
import com.alexhr90.muabet.ui.common.relativeTime
import androidx.compose.foundation.background

/** A thread page by page, with first, previous, the page jump sheet, next and last in the bottom bar. */
@Composable
fun ThreadScreen(
    onBack: () -> Unit,
    viewModel: ThreadViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val openLink = LocalOpenLink.current
    val openWeb = LocalOpenWeb.current
    val page = ((state as? UiState.Done)?.result as? Loaded.Ok)?.value
    val title = page?.title ?: viewModel.initialTitle
    val current = page?.page?.current ?: viewModel.page
    val last = page?.page?.last ?: current
    var jumpOpen by rememberSaveable { mutableStateOf(false) }
    var image by rememberSaveable { mutableStateOf<String?>(null) }
    val pageUrl = ForumUrls.thread(viewModel.slug, viewModel.threadId, current)

    ScreenScaffold(
        title = title,
        onBack = onBack,
        actions = {
            if (page?.canReply == true) {
                IconButton(onClick = { openWeb(ForumUrls.reply(viewModel.slug, viewModel.threadId)) }) {
                    Icon(Icons.AutoMirrored.Outlined.Reply, contentDescription = stringResource(R.string.action_reply))
                }
            }
            IconButton(onClick = { openWeb(pageUrl) }) {
                Icon(Icons.Outlined.OpenInBrowser, contentDescription = stringResource(R.string.action_open_on_forum))
            }
        },
        bottomBar = {
            PageBar(current, last, onGo = viewModel::goTo, onJump = { jumpOpen = true })
        },
    ) { padding ->
        LoadedContent(state, onRetry = { viewModel.load() }, modifier = Modifier.padding(padding)) { tp ->
            val listState = rememberLazyListState()
            // A new page starts at its top.
            LaunchedEffect(tp.page.current) { if (listState.firstVisibleItemIndex > 0) listState.scrollToItem(0) }
            val actions = PostActions(
                onLink = openLink,
                onImage = { image = it },
                onOpenOnForum = { openWeb(pageUrl) },
            )
            LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = padding.screenContent()) {
                pageTitle(tp.title, subtitle = tp.forumTitle)
                items(tp.posts, key = { it.id }, contentType = { "post" }) { post ->
                    PostItem(post, actions, time = relativeTime(post.timestamp))
                }
            }
        }
    }
    if (jumpOpen) {
        PageJumpSheet(current, last, onJump = { jumpOpen = false; viewModel.goTo(it) }, onDismiss = { jumpOpen = false })
    }
    image?.let { url -> ImageViewer(url, onDismiss = { image = null }) }
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

/** Tap an image to see it whole; tap again or Back to close. */
@Composable
private fun ImageViewer(url: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        AsyncImage(
            model = url,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.92f))
                .clickable(onClick = onDismiss),
        )
    }
}
