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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.GppMaybe
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
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

/** "5 minutes ago", "Yesterday", "12 Mar" in the phone's language, from epoch seconds. */
fun relativeTime(epochSeconds: Long?, now: Long = System.currentTimeMillis()): String? {
    epochSeconds ?: return null
    val ms = epochSeconds * 1000
    return DateUtils.getRelativeTimeSpanString(ms, now, DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString()
}
