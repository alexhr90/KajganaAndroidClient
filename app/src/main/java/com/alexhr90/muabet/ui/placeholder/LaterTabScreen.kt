package com.alexhr90.muabet.ui.placeholder

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.alexhr90.muabet.R
import com.alexhr90.muabet.designsystem.EmptyState
import com.alexhr90.muabet.designsystem.ScreenScaffold
import com.alexhr90.muabet.ui.common.LocalOpenWeb

/**
 * New, Watched and Alerts until their native lists arrive in M2 (SPEC §4): the forum's own page
 * in the WebView. Watched and Alerts ask guests to log in first.
 */
@Composable
fun LaterTabScreen(
    title: String,
    icon: ImageVector,
    webUrl: String,
    needsLogin: Boolean,
    loggedIn: Boolean,
    onSettings: () -> Unit,
    onLogin: () -> Unit,
) {
    val openWeb = LocalOpenWeb.current
    ScreenScaffold(title = title, onOpenSettings = onSettings) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            if (needsLogin && !loggedIn) {
                EmptyState(
                    title = stringResource(R.string.login_needed_title),
                    message = stringResource(R.string.login_needed_message),
                    icon = icon,
                    actionLabel = stringResource(R.string.action_log_in),
                    onAction = onLogin,
                )
            } else {
                EmptyState(
                    title = title,
                    message = stringResource(R.string.later_tab_message),
                    icon = icon,
                    actionLabel = stringResource(R.string.action_open_on_forum),
                    onAction = { openWeb(webUrl) },
                )
            }
        }
    }
}
