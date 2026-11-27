package com.alexhr90.muabet

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alexhr90.muabet.data.settings.AppSettings
import com.alexhr90.muabet.data.settings.BackdropStyle
import com.alexhr90.muabet.data.settings.MotionMode
import com.alexhr90.muabet.data.settings.RowDensity
import com.alexhr90.muabet.data.settings.SettingsRepository
import com.alexhr90.muabet.designsystem.LaunchIntro
import com.alexhr90.muabet.designsystem.LocalBackdrop
import com.alexhr90.muabet.designsystem.LocalCompactRows
import com.alexhr90.muabet.designsystem.LocalImagesOnTap
import com.alexhr90.muabet.designsystem.LocalReducedMotion
import com.alexhr90.muabet.designsystem.MuabetBackdrop
import com.alexhr90.muabet.designsystem.MuabetTheme
import com.alexhr90.muabet.designsystem.isAppInDarkTheme
import com.alexhr90.muabet.domain.url.ForumUrls
import com.alexhr90.muabet.ui.navigation.MuabetNavHost
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settings: SettingsRepository

    /** A forum link that arrived from outside (Open with, share, widget, notification). */
    private val incoming = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // No system scrim behind three-button navigation: the app's own background runs to the edge.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
        val firstLaunch = savedInstanceState == null
        if (firstLaunch) incoming.value = linkOf(intent)
        setContent {
            val loaded by settings.settings.map<AppSettings, AppSettings?> { it }.collectAsStateWithLifecycle(initialValue = null)
            val link by incoming.collectAsStateWithLifecycle()
            val s = loaded ?: AppSettings()
            val context = LocalContext.current
            val reduced = when (s.motionMode) {
                MotionMode.FULL -> false
                MotionMode.REDUCED -> true
                MotionMode.SYSTEM -> systemAnimationsOff(context)
            }
            val density = LocalDensity.current
            MuabetTheme(darkTheme = isAppInDarkTheme(s.themeMode), reducedMotion = reduced) {
                // Saved, so rotation never replays it.
                var showIntro by rememberSaveable { mutableStateOf(firstLaunch && link == null) }
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    if (loaded != null) {
                        val backdrop = s.backdrop != BackdropStyle.NONE
                        if (backdrop) MuabetBackdrop(s.backdrop)
                        CompositionLocalProvider(
                            LocalBackdrop provides backdrop,
                            LocalCompactRows provides (s.density == RowDensity.COMPACT),
                            LocalImagesOnTap provides s.imagesOnTap,
                            // Text size on top of the system's (SPEC §4 M3).
                            LocalDensity provides Density(density.density, density.fontScale * s.textScale),
                        ) {
                            MuabetNavHost(incomingLink = link, onLinkHandled = { incoming.value = null })
                        }
                    }
                    if (showIntro && !LocalReducedMotion.current) {
                        LaunchIntro(onFinished = { showIntro = false })
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        linkOf(intent)?.let { incoming.value = it }
    }

    /** The forum address in a VIEW or SEND intent, or null. Links to other sites are ignored. */
    private fun linkOf(intent: Intent?): String? {
        intent ?: return null
        val raw = when (intent.action) {
            Intent.ACTION_VIEW -> intent.dataString
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)?.let { text ->
                Regex("https?://[^\\s<>\"]+").findAll(text).map { it.value }.firstOrNull { isForum(it) }
            }
            else -> null
        } ?: return null
        return if (isForum(raw)) ForumUrls.preferHttps(raw) else null
    }

    private fun isForum(url: String): Boolean = runCatching { ForumUrls.isForumHost(java.net.URI(url).host) }.getOrDefault(false)

    private fun systemAnimationsOff(context: Context): Boolean = runCatching {
        android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }.getOrDefault(false)
}
