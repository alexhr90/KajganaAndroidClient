package com.alexhr90.muabet.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alexhr90.muabet.BuildConfig
import com.alexhr90.muabet.R
import com.alexhr90.muabet.data.settings.AppSettings
import com.alexhr90.muabet.data.settings.BackdropStyle
import com.alexhr90.muabet.data.settings.MotionMode
import com.alexhr90.muabet.data.settings.RowDensity
import com.alexhr90.muabet.data.settings.ThemeMode
import com.alexhr90.muabet.designsystem.ChoiceDialog
import com.alexhr90.muabet.designsystem.MuabetSnackbarHost
import com.alexhr90.muabet.designsystem.NavigationRow
import com.alexhr90.muabet.designsystem.NoteCard
import com.alexhr90.muabet.designsystem.ScreenScaffold
import com.alexhr90.muabet.designsystem.SectionHeader
import com.alexhr90.muabet.designsystem.SwitchRow
import com.alexhr90.muabet.designsystem.Tone
import com.alexhr90.muabet.designsystem.ValueRow
import com.alexhr90.muabet.designsystem.pageTitle
import com.alexhr90.muabet.designsystem.screenContent
import com.alexhr90.muabet.domain.url.ForumUrls
import com.alexhr90.muabet.ui.common.LocalOpenWeb
import com.alexhr90.muabet.ui.common.MessageEffect
import com.alexhr90.muabet.ui.common.rememberCustomTabOpener
import java.time.LocalDate

private enum class Picker { THEME, MOTION, BACKDROP, TEXT, DENSITY, INTERVAL, QUIET, SAVED_CAP }

/** Quiet hours the user can pick: off, or a night window. */
private val QUIET_PRESETS = listOf(0 to 0, 22 to 7, 23 to 7, 0 to 8, 22 to 9)

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLogin: () -> Unit,
    onMessages: () -> Unit,
    onBookmarks: () -> Unit,
    onSessionCheck: () -> Unit,
    onHidden: () -> Unit,
    onMutes: () -> Unit,
    onSaved: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val counts by viewModel.counts.collectAsStateWithLifecycle()
    var picker by rememberSaveable { mutableStateOf<Picker?>(null) }
    val context = LocalContext.current
    val openTab = rememberCustomTabOpener(context)
    val openWeb = LocalOpenWeb.current
    val snackbar = remember { SnackbarHostState() }
    MessageEffect(viewModel.messages, snackbar)

    // Notifications need the permission on Android 13+; turning them on asks for it.
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.update { it.copy(notifications = granted) }
    }
    val setNotifications: (Boolean) -> Unit = { on ->
        val needsAsk = on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsAsk) askPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else viewModel.update { it.copy(notifications = on) }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(viewModel::export)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::restore)
    }

    val heading = stringResource(R.string.settings_title)
    ScreenScaffold(title = heading, onBack = onBack, snackbarHost = { MuabetSnackbarHost(snackbar) }) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding.screenContent()) {
            pageTitle(heading)

            item { SectionHeader(stringResource(R.string.settings_account)) }
            if (session?.loggedIn == true) {
                item {
                    NavigationRow(
                        title = stringResource(R.string.direct_messages),
                        subtitle = stringResource(R.string.direct_messages_hint),
                        icon = Icons.Outlined.Mail,
                        onClick = onMessages,
                    )
                }
                item {
                    NavigationRow(
                        title = stringResource(R.string.bookmarks),
                        subtitle = stringResource(R.string.bookmarks_hint),
                        icon = Icons.Outlined.Bookmarks,
                        onClick = onBookmarks,
                    )
                }
                item {
                    NavigationRow(
                        title = stringResource(R.string.account_page),
                        subtitle = stringResource(R.string.account_page_hint),
                        icon = Icons.Outlined.Person,
                        onClick = { openWeb(ForumUrls.account) },
                    )
                }
            }
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

            item { SectionHeader(stringResource(R.string.settings_reading)) }
            item { ValueRow(stringResource(R.string.settings_text_size), percent(s.textScale), { picker = Picker.TEXT }) }
            item { ValueRow(stringResource(R.string.settings_density), densityLabel(s.density), { picker = Picker.DENSITY }) }
            item {
                SwitchRow(
                    stringResource(R.string.settings_images_on_tap),
                    s.imagesOnTap,
                    { on -> viewModel.update { it.copy(imagesOnTap = on) } },
                    subtitle = stringResource(R.string.settings_images_on_tap_hint),
                )
            }

            item { SectionHeader(stringResource(R.string.settings_appearance)) }
            item { ValueRow(stringResource(R.string.settings_theme), themeLabel(s.themeMode), { picker = Picker.THEME }) }
            item { ValueRow(stringResource(R.string.settings_motion), motionLabel(s.motionMode), { picker = Picker.MOTION }) }
            item { ValueRow(stringResource(R.string.settings_backdrop), stringResource(backdropRes(s.backdrop)), { picker = Picker.BACKDROP }) }

            item { SectionHeader(stringResource(R.string.settings_notifications)) }
            item {
                SwitchRow(
                    stringResource(R.string.settings_notify_alerts),
                    s.notifications,
                    setNotifications,
                    subtitle = stringResource(if (session?.loggedIn == true) R.string.settings_notify_alerts_hint else R.string.settings_notify_needs_login),
                )
            }
            if (s.notifications) {
                item { ValueRow(stringResource(R.string.settings_notify_interval), intervalLabel(s.notifyIntervalMin), { picker = Picker.INTERVAL }) }
                item { ValueRow(stringResource(R.string.settings_quiet_hours), quietLabel(s), { picker = Picker.QUIET }) }
                item { NoteCard(stringResource(R.string.settings_notify_late, s.notifyIntervalMin)) }
            }

            item { SectionHeader(stringResource(R.string.settings_lists)) }
            item {
                NavigationRow(
                    title = stringResource(R.string.hidden_forums),
                    subtitle = stringResource(R.string.count_items, counts.hidden),
                    icon = Icons.Outlined.VisibilityOff,
                    onClick = onHidden,
                )
            }
            item {
                NavigationRow(
                    title = stringResource(R.string.mutes_title),
                    subtitle = stringResource(R.string.mutes_counts, counts.mutedThreads, counts.keywords),
                    icon = Icons.Outlined.NotificationsOff,
                    onClick = onMutes,
                )
            }
            item {
                NavigationRow(
                    title = stringResource(R.string.saved_title),
                    subtitle = stringResource(R.string.saved_counts, counts.saved, Formatter.formatShortFileSize(context, counts.savedBytes)),
                    icon = Icons.Outlined.DownloadForOffline,
                    onClick = onSaved,
                )
            }
            item { ValueRow(stringResource(R.string.settings_saved_cap), stringResource(R.string.size_mb, s.savedCapMb), { picker = Picker.SAVED_CAP }) }

            item { SectionHeader(stringResource(R.string.settings_backup)) }
            item {
                NavigationRow(
                    title = stringResource(R.string.backup_export),
                    subtitle = stringResource(R.string.backup_export_hint),
                    icon = Icons.Outlined.FileUpload,
                    onClick = { exportLauncher.launch("muabet-${LocalDate.now()}.json") },
                )
            }
            item {
                NavigationRow(
                    title = stringResource(R.string.backup_import),
                    subtitle = stringResource(R.string.backup_import_hint),
                    icon = Icons.Outlined.FileDownload,
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                )
            }

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
    val densityLabels = RowDensity.entries.associateWith { densityLabel(it) }
    val textLabels = AppSettings.TEXT_SCALES.associateWith { percent(it) }
    val intervalLabels = AppSettings.INTERVALS.associateWith { intervalLabel(it) }
    val quietLabels = QUIET_PRESETS.associateWith { (a, b) -> quietLabel(AppSettings(quietStart = a, quietEnd = b)) }
    val capLabels = AppSettings.SAVED_CAPS.associateWith { stringResource(R.string.size_mb, it) }
    val dismiss = { picker = null }
    when (picker) {
        Picker.THEME -> ChoiceDialog(stringResource(R.string.settings_theme), ThemeMode.entries, s.themeMode, { themeLabels.getValue(it) }, { v -> viewModel.update { it.copy(themeMode = v) } }, dismiss)
        Picker.MOTION -> ChoiceDialog(stringResource(R.string.settings_motion), MotionMode.entries, s.motionMode, { motionLabels.getValue(it) }, { v -> viewModel.update { it.copy(motionMode = v) } }, dismiss)
        Picker.BACKDROP -> ChoiceDialog(stringResource(R.string.settings_backdrop), BackdropStyle.entries, s.backdrop, { backdropLabels.getValue(it) }, { v -> viewModel.update { it.copy(backdrop = v) } }, dismiss)
        Picker.DENSITY -> ChoiceDialog(stringResource(R.string.settings_density), RowDensity.entries, s.density, { densityLabels.getValue(it) }, { v -> viewModel.update { it.copy(density = v) } }, dismiss)
        Picker.TEXT -> ChoiceDialog(
            stringResource(R.string.settings_text_size),
            AppSettings.TEXT_SCALES,
            AppSettings.TEXT_SCALES.minBy { kotlin.math.abs(it - s.textScale) },
            { textLabels.getValue(it) },
            { v -> viewModel.update { it.copy(textScale = v) } },
            dismiss,
        )
        Picker.INTERVAL -> ChoiceDialog(
            stringResource(R.string.settings_notify_interval),
            AppSettings.INTERVALS,
            AppSettings.INTERVALS.minBy { kotlin.math.abs(it - s.notifyIntervalMin) },
            { intervalLabels.getValue(it) },
            { v -> viewModel.update { it.copy(notifyIntervalMin = v) } },
            dismiss,
        )
        Picker.QUIET -> ChoiceDialog(
            stringResource(R.string.settings_quiet_hours),
            QUIET_PRESETS,
            QUIET_PRESETS.firstOrNull { it.first == s.quietStart && it.second == s.quietEnd } ?: QUIET_PRESETS[0],
            { quietLabels.getValue(it) },
            { (a, b) -> viewModel.update { it.copy(quietStart = a, quietEnd = b) } },
            dismiss,
        )
        Picker.SAVED_CAP -> ChoiceDialog(
            stringResource(R.string.settings_saved_cap),
            AppSettings.SAVED_CAPS,
            AppSettings.SAVED_CAPS.minBy { kotlin.math.abs(it - s.savedCapMb) },
            { capLabels.getValue(it) },
            { v -> viewModel.update { it.copy(savedCapMb = v) } },
            dismiss,
        )
        null -> Unit
    }
}

private fun percent(scale: Float): String = "${(scale * 100).toInt()} %"

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
private fun densityLabel(d: RowDensity): String = stringResource(
    when (d) {
        RowDensity.COMFORTABLE -> R.string.density_comfortable
        RowDensity.COMPACT -> R.string.density_compact
    },
)

@Composable
private fun intervalLabel(minutes: Int): String =
    if (minutes < 60) stringResource(R.string.every_minutes, minutes) else stringResource(R.string.every_hours, minutes / 60)

@Composable
private fun quietLabel(s: AppSettings): String =
    if (!s.hasQuietHours) stringResource(R.string.quiet_off) else stringResource(R.string.quiet_range, s.quietStart, s.quietEnd)

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
