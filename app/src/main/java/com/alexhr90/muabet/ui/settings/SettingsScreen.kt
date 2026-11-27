package com.alexhr90.muabet.ui.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alexhr90.muabet.BuildConfig
import com.alexhr90.muabet.R
import com.alexhr90.muabet.data.settings.BackdropStyle
import com.alexhr90.muabet.data.settings.MotionMode
import com.alexhr90.muabet.data.settings.ThemeMode
import com.alexhr90.muabet.designsystem.ChoiceDialog
import com.alexhr90.muabet.designsystem.NavigationRow
import com.alexhr90.muabet.designsystem.NoteCard
import com.alexhr90.muabet.designsystem.ScreenScaffold
import com.alexhr90.muabet.designsystem.SectionHeader
import com.alexhr90.muabet.designsystem.Tone
import com.alexhr90.muabet.designsystem.ValueRow
import com.alexhr90.muabet.designsystem.pageTitle
import com.alexhr90.muabet.designsystem.screenContent
import com.alexhr90.muabet.ui.common.rememberCustomTabOpener

private enum class Picker { THEME, MOTION, BACKDROP }

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLogin: () -> Unit,
    onSessionCheck: () -> Unit,
    reloadKey: Long = 0L,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    // After a login from here, ask the forum who we are now.
    LaunchedEffect(reloadKey) { if (reloadKey > 0) viewModel.runCheck() }
    val s by viewModel.state.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    var picker by rememberSaveable { mutableStateOf<Picker?>(null) }
    val openTab = rememberCustomTabOpener(LocalContext.current)

    ScreenScaffold(title = stringResource(R.string.settings_title), onBack = onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding.screenContent()) {
            pageTitle(stringResource(R.string.settings_title))

            item { SectionHeader(stringResource(R.string.settings_account)) }
            item {
                val name = session?.username
                if (session?.loggedIn == true) {
                    NavigationRow(
                        title = stringResource(R.string.action_log_out),
                        subtitle = name?.let { stringResource(R.string.account_logged_in_as, it) } ?: stringResource(R.string.account_logged_in),
                        icon = Icons.AutoMirrored.Outlined.Logout,
                        tone = Tone.Negative,
                        onClick = viewModel::logout,
                    )
                } else {
                    NavigationRow(
                        title = stringResource(R.string.action_log_in),
                        subtitle = stringResource(R.string.account_login_hint),
                        icon = Icons.AutoMirrored.Outlined.Login,
                        onClick = onLogin,
                    )
                }
            }

            item { SectionHeader(stringResource(R.string.settings_appearance)) }
            item { ValueRow(stringResource(R.string.settings_theme), themeLabel(s.themeMode), { picker = Picker.THEME }) }
            item { ValueRow(stringResource(R.string.settings_motion), motionLabel(s.motionMode), { picker = Picker.MOTION }) }
            item { ValueRow(stringResource(R.string.settings_backdrop), backdropLabel(s.backdrop), { picker = Picker.BACKDROP }) }

            item { SectionHeader(stringResource(R.string.settings_diagnostics)) }
            item {
                NavigationRow(
                    title = stringResource(R.string.session_check_title),
                    subtitle = stringResource(R.string.session_check_subtitle),
                    icon = Icons.Outlined.NetworkCheck,
                    onClick = onSessionCheck,
                )
            }

            item { SectionHeader(stringResource(R.string.settings_about)) }
            item {
                NavigationRow(
                    title = stringResource(R.string.about_source),
                    subtitle = stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                    icon = Icons.Outlined.Code,
                    onClick = { openTab("https://github.com/alexhr90/KajganaAndroidClient") },
                )
            }
            item { NoteCard(stringResource(R.string.about_unofficial)) }
        }
    }

    // ChoiceDialog's label is a plain function, so every label is resolved here first.
    val themeLabels = ThemeMode.entries.associateWith { themeLabel(it) }
    val motionLabels = MotionMode.entries.associateWith { motionLabel(it) }
    val backdropLabels = BackdropStyle.entries.associateWith { stringResource(backdropRes(it)) }
    when (picker) {
        Picker.THEME -> ChoiceDialog(
            title = stringResource(R.string.settings_theme),
            options = ThemeMode.entries,
            selected = s.themeMode,
            label = { themeLabels.getValue(it) },
            onSelect = { v -> viewModel.update { it.copy(themeMode = v) } },
            onDismiss = { picker = null },
        )
        Picker.MOTION -> ChoiceDialog(
            title = stringResource(R.string.settings_motion),
            options = MotionMode.entries,
            selected = s.motionMode,
            label = { motionLabels.getValue(it) },
            onSelect = { v -> viewModel.update { it.copy(motionMode = v) } },
            onDismiss = { picker = null },
        )
        Picker.BACKDROP -> ChoiceDialog(
            title = stringResource(R.string.settings_backdrop),
            options = BackdropStyle.entries,
            selected = s.backdrop,
            label = { backdropLabels.getValue(it) },
            onSelect = { v -> viewModel.update { it.copy(backdrop = v) } },
            onDismiss = { picker = null },
        )
        null -> Unit
    }
}

@Composable
private fun themeLabel(m: ThemeMode): String = stringResource(
    when (m) {
        ThemeMode.SYSTEM -> R.string.option_system
        ThemeMode.LIGHT -> R.string.theme_light
        ThemeMode.DARK -> R.string.theme_dark
    },
)

@Composable
private fun motionLabel(m: MotionMode): String = stringResource(
    when (m) {
        MotionMode.SYSTEM -> R.string.option_system
        MotionMode.FULL -> R.string.motion_full
        MotionMode.REDUCED -> R.string.motion_reduced
    },
)

@Composable
private fun backdropLabel(b: BackdropStyle): String = stringResource(backdropRes(b))

private fun backdropRes(b: BackdropStyle): Int = when (b) {
    BackdropStyle.NONE -> R.string.backdrop_none
    BackdropStyle.AUTO -> R.string.backdrop_auto
    BackdropStyle.DAWN -> R.string.backdrop_dawn
    BackdropStyle.DAY -> R.string.backdrop_day
    BackdropStyle.DUSK -> R.string.backdrop_dusk
    BackdropStyle.NIGHT -> R.string.backdrop_night
    BackdropStyle.AURORA -> R.string.backdrop_aurora
    BackdropStyle.EMBER -> R.string.backdrop_ember
}
