package com.alexhr90.muabet.ui.compose

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.FormatStrikethrough
import androidx.compose.material.icons.outlined.FormatUnderlined
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.InsertLink
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.alexhr90.muabet.R
import com.alexhr90.muabet.designsystem.LogoLoader
import com.alexhr90.muabet.designsystem.Rule
import com.alexhr90.muabet.designsystem.ScreenScaffold
import com.alexhr90.muabet.designsystem.Space
import com.alexhr90.muabet.designsystem.Tone
import com.alexhr90.muabet.designsystem.barGround
import com.alexhr90.muabet.designsystem.color
import com.alexhr90.muabet.designsystem.contentInset
import com.alexhr90.muabet.designsystem.glass
import com.alexhr90.muabet.ui.common.LocalOpenWeb

/**
 * Writing on the forum without leaving the app (v1.1): replies and quotes, new threads, edits and
 * direct messages. A row of BB code tools sits above the keyboard; photos upload from the system
 * photo picker (no permission). What is typed is kept as a draft until it is sent.
 */
@Composable
fun ComposeScreen(
    onBack: () -> Unit,
    /** Sent: the address of the new post or page. */
    onDone: (String?) -> Unit,
    onLogin: () -> Unit,
    viewModel: ComposeViewModel = hiltViewModel(),
) {
    val openWeb = LocalOpenWeb.current
    LaunchedEffect(viewModel) { viewModel.done.collect { onDone(it) } }
    val leave = {
        viewModel.saveNow()
        onBack()
    }
    BackHandler(onBack = leave)

    var linkDialog by remember { mutableStateOf<LinkKind?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(6)) { uris ->
        uris.forEach { viewModel.attach(it) }
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(viewModel.preparing) {
        if (!viewModel.preparing && !viewModel.needsTitle && !viewModel.needsRecipients) runCatching { focus.requestFocus() }
    }

    val screenTitle = stringResource(
        when (viewModel.kind) {
            ComposeKind.Reply -> R.string.compose_reply
            ComposeKind.Thread -> R.string.compose_new_thread
            ComposeKind.Edit -> R.string.compose_edit
            ComposeKind.Message -> R.string.compose_message_reply
            ComposeKind.NewMessage -> R.string.compose_new_message
        },
    )
    ScreenScaffold(
        title = screenTitle,
        onBack = leave,
        titleInBar = true,
        actions = {
            if (viewModel.sending) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { LogoLoader(Modifier.size(24.dp)) }
            } else {
                IconButton(onClick = viewModel::send, enabled = !viewModel.preparing && viewModel.uploading == 0) {
                    Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = stringResource(R.string.action_send), tint = MaterialTheme.colorScheme.primary)
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .imePadding(),
        ) {
            val inset = contentInset()
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = inset),
                verticalArrangement = Arrangement.spacedBy(Space.s),
            ) {
                if (viewModel.contextTitle.isNotBlank()) {
                    Text(
                        viewModel.contextTitle,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
                viewModel.problem?.let { p -> ProblemCard(p, onLogin = onLogin, onOpen = openWeb, onDismiss = viewModel::dismissProblem) }
                if (viewModel.needsRecipients) {
                    OutlinedTextField(
                        value = viewModel.recipients,
                        onValueChange = viewModel::onRecipients,
                        label = { Text(stringResource(R.string.compose_recipients)) },
                        supportingText = { Text(stringResource(R.string.compose_recipients_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (viewModel.needsTitle) {
                    OutlinedTextField(
                        value = viewModel.title,
                        onValueChange = viewModel::onTitle,
                        label = { Text(stringResource(R.string.compose_title)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    OutlinedTextField(
                        value = viewModel.text,
                        onValueChange = viewModel::onText,
                        placeholder = { Text(stringResource(R.string.compose_placeholder)) },
                        enabled = !viewModel.preparing,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, keyboardType = KeyboardType.Text),
                        modifier = Modifier.fillMaxSize().focusRequester(focus),
                    )
                    if (viewModel.preparing) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LogoLoader(Modifier.size(32.dp)) }
                    }
                }
                if (viewModel.attachments.isNotEmpty() || viewModel.uploading > 0) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(Space.s),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        viewModel.attachments.forEach { a ->
                            val thumb = a.thumbnail
                            InputChip(
                                selected = false,
                                onClick = { viewModel.detach(a) },
                                label = { Text(a.name, maxLines = 1) },
                                avatar = if (thumb != null) {
                                    { AsyncImage(model = thumb, contentDescription = null, modifier = Modifier.size(24.dp)) }
                                } else {
                                    null
                                },
                                trailingIcon = { Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cd_remove_photo), modifier = Modifier.size(18.dp)) },
                            )
                        }
                        if (viewModel.uploading > 0) {
                            LogoLoader(Modifier.size(24.dp))
                            Text(stringResource(R.string.compose_uploading), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                Spacer(Modifier.size(Space.xs))
            }
            Toolbar(
                onWrap = viewModel::wrap,
                onInsert = viewModel::insert,
                onLink = { linkDialog = LinkKind.Link },
                onImage = { linkDialog = LinkKind.Image },
                onPhoto = if (viewModel.canAttach) {
                    { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                } else {
                    null
                },
            )
        }
    }

    linkDialog?.let { kind ->
        UrlDialog(
            kind = kind,
            initialText = viewModel.selected(),
            onDismiss = { linkDialog = null },
            onDone = { url, label ->
                linkDialog = null
                when (kind) {
                    LinkKind.Link -> viewModel.insert(if (label.isBlank()) "[URL]$url[/URL]" else "[URL=$url]$label[/URL]")
                    LinkKind.Image -> viewModel.insert("[IMG]$url[/IMG]")
                }
            },
        )
    }
}

private enum class LinkKind { Link, Image }

/** The BB code tools, above the keyboard: they wrap the selection or start a tag at the cursor. */
@Composable
private fun Toolbar(
    onWrap: (String, String) -> Unit,
    onInsert: (String) -> Unit,
    onLink: () -> Unit,
    onImage: () -> Unit,
    onPhoto: (() -> Unit)?,
) {
    Column(Modifier.fillMaxWidth().background(barGround).navigationBarsPadding()) {
        Rule()
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Space.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onPhoto != null) Tool(Icons.Outlined.AddPhotoAlternate, R.string.tool_photo, onPhoto)
            Tool(Icons.Outlined.FormatBold, R.string.tool_bold) { onWrap("[B]", "[/B]") }
            Tool(Icons.Outlined.FormatItalic, R.string.tool_italic) { onWrap("[I]", "[/I]") }
            Tool(Icons.Outlined.FormatUnderlined, R.string.tool_underline) { onWrap("[U]", "[/U]") }
            Tool(Icons.Outlined.FormatStrikethrough, R.string.tool_strike) { onWrap("[S]", "[/S]") }
            Tool(Icons.Outlined.InsertLink, R.string.tool_link, onLink)
            Tool(Icons.Outlined.Image, R.string.tool_image, onImage)
            Tool(Icons.Outlined.FormatQuote, R.string.tool_quote) { onWrap("[QUOTE]", "[/QUOTE]") }
            Tool(Icons.Outlined.VisibilityOff, R.string.tool_spoiler) { onWrap("[SPOILER]", "[/SPOILER]") }
            Tool(Icons.Outlined.Code, R.string.tool_code) { onWrap("[CODE]", "[/CODE]") }
            Tool(Icons.Outlined.FormatListBulleted, R.string.tool_list) { onInsert("[LIST]\n[*]\n[/LIST]") }
        }
    }
}

@Composable
private fun Tool(icon: ImageVector, label: Int, onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(icon, contentDescription = stringResource(label)) }
}

@Composable
private fun UrlDialog(kind: LinkKind, initialText: String, onDismiss: () -> Unit, onDone: (String, String) -> Unit) {
    var url by remember { mutableStateOf(if (initialText.startsWith("http")) initialText else "") }
    var label by remember { mutableStateOf(if (initialText.startsWith("http")) "" else initialText) }
    val valid = url.trim().let { it.startsWith("http://") || it.startsWith("https://") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (kind == LinkKind.Link) R.string.tool_link else R.string.tool_image)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.compose_address)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (kind == LinkKind.Link) {
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        label = { Text(stringResource(R.string.compose_link_text)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDone(url.trim(), label.trim()) }, enabled = valid) { Text(stringResource(R.string.action_insert)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Why the message did not go: the forum's own words, no connection, a login, a bot check. */
@Composable
private fun ProblemCard(problem: ComposeProblem, onLogin: () -> Unit, onOpen: (String) -> Unit, onDismiss: () -> Unit) {
    val text = when (problem) {
        is ComposeProblem.Refused -> problem.errors.joinToString("\n").ifBlank { stringResource(R.string.compose_refused) }
        ComposeProblem.Offline -> stringResource(R.string.compose_offline)
        is ComposeProblem.Blocked -> stringResource(R.string.error_blocked_message)
        ComposeProblem.NeedsLogin -> stringResource(R.string.compose_needs_login)
        ComposeProblem.Empty -> stringResource(R.string.compose_empty)
        is ComposeProblem.Upload -> problem.message?.takeIf { it.isNotBlank() } ?: stringResource(R.string.compose_upload_failed)
        ComposeProblem.SourceFailed -> stringResource(R.string.compose_source_failed)
    }
    Row(
        Modifier.fillMaxWidth().glass(MaterialTheme.shapes.small).heightIn(min = 48.dp).padding(start = Space.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = Tone.Negative.color())
        Spacer(Modifier.width(Space.m))
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(vertical = Space.s))
        when (problem) {
            ComposeProblem.NeedsLogin -> TextButton(onClick = onLogin) { Text(stringResource(R.string.action_log_in)) }
            is ComposeProblem.Blocked -> TextButton(onClick = { onOpen(problem.url) }) { Text(stringResource(R.string.action_open_on_forum)) }
            else -> IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.action_close)) }
        }
    }
}
