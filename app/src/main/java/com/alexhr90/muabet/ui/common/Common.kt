package com.alexhr90.muabet.ui.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.alexhr90.muabet.designsystem.LogoLoader
import com.alexhr90.muabet.designsystem.OutlinedPill
import com.alexhr90.muabet.designsystem.Space
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.GppMaybe
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.Flow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import com.alexhr90.muabet.R
import com.alexhr90.muabet.data.repo.Loaded
import com.alexhr90.muabet.designsystem.EmptyState
import com.alexhr90.muabet.designsystem.LoadingState
import com.alexhr90.muabet.designsystem.Tone

/** A screen's state: still loading, or what the load gave. */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Done<T>(val result: Loaded<T>) : UiState<T>
}

/** Opens a link: forum pages natively, other sites in a Custom Tab (SPEC §3 "Links"). */
val LocalOpenLink = staticCompositionLocalOf<(String) -> Unit> { {} }

/** Opens a forum page in the in-app WebView (login, posting, anything not native yet). */
val LocalOpenWeb = staticCompositionLocalOf<(String) -> Unit> { {} }

/**
 * Shows [state]: the logo loader while loading, [content] when it loaded, and otherwise an
 * empty state that explains what happened with "Open on the forum" (never a blank screen).
 */
@Composable
fun <T> LoadedContent(state: UiState<T>, onRetry: () -> Unit, modifier: Modifier = Modifier, content: @Composable (T) -> Unit) {
    val openWeb = LocalOpenWeb.current
    when (state) {
        UiState.Loading -> LoadingState(stringResource(R.string.loading), modifier)
        is UiState.Done -> when (val r = state.result) {
            is Loaded.Ok -> content(r.value)
            is Loaded.Offline -> Centered(modifier) {
                EmptyState(
                    title = stringResource(R.string.error_offline_title),
                    message = stringResource(R.string.error_offline_message),
                    icon = Icons.Outlined.CloudOff,
                    actionLabel = stringResource(R.string.action_retry),
                    onAction = onRetry,
                )
            }
            is Loaded.Blocked -> Centered(modifier) {
                EmptyState(
                    title = stringResource(R.string.error_blocked_title),
                    message = stringResource(R.string.error_blocked_message),
                    icon = Icons.Outlined.GppMaybe,
                    tone = Tone.Warning,
                    actionLabel = stringResource(R.string.action_open_on_forum),
                    onAction = { openWeb(r.url) },
                )
            }
            is Loaded.Failed -> Centered(modifier) {
                EmptyState(
                    title = stringResource(R.string.error_parse_title),
                    message = stringResource(R.string.error_parse_message),
                    icon = Icons.Outlined.ErrorOutline,
                    actionLabel = stringResource(R.string.action_open_on_forum),
                    onAction = { openWeb(r.url) },
                )
            }
        }
    }
}

@Composable
private fun Centered(modifier: Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

/** Opens [url] in a Custom Tab in the app's colours, or any browser when there is no Custom Tab support. */
fun openCustomTab(context: Context, url: String, toolbarColor: Int? = null) {
    val uri = Uri.parse(url)
    try {
        val builder = CustomTabsIntent.Builder().setShowTitle(true)
        if (toolbarColor != null) {
            builder.setDefaultColorSchemeParams(CustomTabColorSchemeParams.Builder().setToolbarColor(toolbarColor).build())
        }
        builder.build().launchUrl(context, uri)
    } catch (e: ActivityNotFoundException) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

/** A Custom Tab opener that takes the theme's background as its toolbar colour. */
@Composable
fun rememberCustomTabOpener(context: Context): (String) -> Unit {
    val color = MaterialTheme.colorScheme.background.toArgb()
    return { url -> openCustomTab(context, url, color) }
}

/**
 * Opens a link to another site in that site's own app when one is installed (YouTube, Instagram,
 * X, Spotify…), otherwise in a Custom Tab. Android 11+ can ask for "an app, not a browser".
 */
@Composable
fun rememberExternalOpener(context: Context): (String) -> Unit {
    val tab = rememberCustomTabOpener(context)
    return { url ->
        val opened = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R && runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addCategory(Intent.CATEGORY_BROWSABLE)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER),
            )
        }.isSuccess
        if (!opened) tab(url)
    }
}

/**
 * Downloads a forum attachment with the session to Downloads (Android 10+) or the app's download
 * folder, with the system's notification to open it. No permission is needed.
 */
fun downloadFile(context: Context, url: String, name: String): Boolean = runCatching {
    val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
    val request = android.app.DownloadManager.Request(Uri.parse(url))
        .setTitle(name)
        .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
    val host = Uri.parse(url).host
    if (com.alexhr90.muabet.domain.url.ForumUrls.isForumHost(host)) {
        android.webkit.CookieManager.getInstance().getCookie(url)?.let { request.addRequestHeader("Cookie", it) }
    }
    val safe = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "download" }
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
        request.setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, safe)
    } else {
        request.setDestinationInExternalFilesDir(context, android.os.Environment.DIRECTORY_DOWNLOADS, safe)
    }
    dm.enqueue(request)
}.isSuccess

/** A short message for the screen's message bar: a string resource with an optional number. */
data class UiMessage(val text: Int, val arg: Int? = null, val undo: (() -> Unit)? = null)

/** Shares [url] (with [title]) through the system share sheet. */
fun shareLink(context: Context, url: String, title: String?) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, if (title.isNullOrBlank()) url else "$title\n$url")
        .apply { if (!title.isNullOrBlank()) putExtra(Intent.EXTRA_SUBJECT, title) }
    runCatching { context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** Copies [text] to the clipboard. Android 13+ shows its own confirmation. */
fun copyText(context: Context, label: String, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager ?: return
    cm.setPrimaryClip(android.content.ClipData.newPlainText(label, text))
}

/** "5 minutes ago", "Yesterday", "12 Mar" in the phone's language, from epoch seconds. */
fun relativeTime(epochSeconds: Long?, now: Long = System.currentTimeMillis()): String? {
    epochSeconds ?: return null
    val ms = epochSeconds * 1000
    return DateUtils.getRelativeTimeSpanString(ms, now, DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString()
}

/** Shows each [UiMessage] in the screen's message bar, with Undo when the message has one. */
@Composable
fun MessageEffect(messages: Flow<UiMessage>, host: SnackbarHostState) {
    val context = LocalContext.current
    val undoLabel = stringResource(R.string.action_undo)
    LaunchedEffect(messages, host) {
        messages.collect { m ->
            val text = if (m.arg != null) context.getString(m.text, m.arg) else context.getString(m.text)
            val result = host.showSnackbar(text, actionLabel = if (m.undo != null) undoLabel else null, duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) m.undo?.invoke()
        }
    }
}

/** For guests on a tab that needs the forum account: why, and the login button. */
@Composable
fun LoginNeeded(icon: ImageVector, onLogin: () -> Unit, modifier: Modifier = Modifier) {
    Centered(modifier) {
        EmptyState(
            title = stringResource(R.string.login_needed_title),
            message = stringResource(R.string.login_needed_message),
            icon = icon,
            actionLabel = stringResource(R.string.action_log_in),
            onAction = onLogin,
        )
    }
}

/** The end of an endless list: the loader while the next page loads, or Try again after a failure. */
@Composable
fun LoadMoreFooter(loading: Boolean, failed: Boolean, onRetry: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(Space.l), contentAlignment = Alignment.Center) {
        when {
            loading -> LogoLoader(Modifier.size(28.dp))
            failed -> OutlinedPill(stringResource(R.string.action_retry), onRetry)
        }
    }
}
