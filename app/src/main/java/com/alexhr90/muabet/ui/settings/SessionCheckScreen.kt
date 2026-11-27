package com.alexhr90.muabet.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alexhr90.muabet.R
import com.alexhr90.muabet.designsystem.InfoRow
import com.alexhr90.muabet.designsystem.LogoLoader
import com.alexhr90.muabet.designsystem.NoteCard
import com.alexhr90.muabet.designsystem.OutlinedPill
import com.alexhr90.muabet.designsystem.PrimaryPill
import com.alexhr90.muabet.designsystem.ScreenScaffold
import com.alexhr90.muabet.designsystem.Space
import com.alexhr90.muabet.designsystem.StatusPill
import com.alexhr90.muabet.designsystem.Tone
import com.alexhr90.muabet.designsystem.pageTitle
import com.alexhr90.muabet.designsystem.screenContent
import com.alexhr90.muabet.domain.net.BlockKind

/**
 * Spike 1 (SPEC §4 M0): shows whether the WebView's login carries over to the app's own requests.
 * Cookie names only, never their values. The owner copies the result into docs/DECISIONS.md.
 */
@Composable
fun SessionCheckScreen(
    onBack: () -> Unit,
    onLogin: () -> Unit,
    reloadKey: Long = 0L,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val check by viewModel.check.collectAsStateWithLifecycle()
    val checking by viewModel.checking.collectAsStateWithLifecycle()
    LaunchedEffect(reloadKey) { viewModel.runCheck() }
    val yes = stringResource(R.string.yes)
    val no = stringResource(R.string.no)

    ScreenScaffold(title = stringResource(R.string.session_check_title), onBack = onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding.screenContent()) {
            pageTitle(stringResource(R.string.session_check_title))
            item { NoteCard(stringResource(R.string.session_check_explain)) }
            val c = check
            if (checking || c == null) {
                item {
                    Box(Modifier.fillMaxWidth().padding(Space.xl), contentAlignment = Alignment.Center) { LogoLoader(Modifier.size(36.dp)) }
                }
            } else {
                item {
                    StatusPill(
                        stringResource(if (c.passed) R.string.session_check_passed else R.string.session_check_failed),
                        tone = if (c.passed) Tone.Positive else Tone.Warning,
                        modifier = Modifier.padding(vertical = Space.m),
                    )
                }
                item {
                    InfoRow(
                        stringResource(R.string.session_check_cookies),
                        c.webViewCookies.joinToString(", ").ifEmpty { stringResource(R.string.none) },
                    )
                }
                item { InfoRow(stringResource(R.string.session_check_remembered), if (c.remembered) yes else no) }
                item {
                    InfoRow(
                        stringResource(R.string.session_check_http),
                        when {
                            c.networkError -> stringResource(R.string.error_offline_title)
                            c.httpCode != null -> c.httpCode.toString()
                            else -> "–"
                        },
                    )
                }
                item {
                    InfoRow(
                        stringResource(R.string.session_check_seen_as),
                        when {
                            c.session?.loggedIn == true -> c.session.username ?: stringResource(R.string.account_logged_in)
                            c.session != null -> stringResource(R.string.account_guest)
                            else -> "–"
                        },
                    )
                }
                item {
                    InfoRow(
                        stringResource(R.string.session_check_block),
                        when (c.blocked) {
                            null, BlockKind.None -> no
                            BlockKind.Challenge -> stringResource(R.string.block_challenge)
                            BlockKind.RateLimited -> stringResource(R.string.block_rate_limited)
                            BlockKind.Forbidden -> stringResource(R.string.block_forbidden)
                        },
                    )
                }
                item { InfoRow(stringResource(R.string.session_check_time), stringResource(R.string.duration_ms, c.durationMs)) }
                if (!c.passed && c.session?.loggedIn != true) {
                    item { NoteCard(stringResource(R.string.session_check_login_first)) }
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(top = Space.xl),
                    horizontalArrangement = Arrangement.spacedBy(Space.s),
                ) {
                    OutlinedPill(stringResource(R.string.action_log_in), onLogin, Modifier.weight(1f))
                    PrimaryPill(stringResource(R.string.action_run_again), { viewModel.runCheck() }, Modifier.weight(1f), enabled = !checking)
                }
            }
        }
    }
}
