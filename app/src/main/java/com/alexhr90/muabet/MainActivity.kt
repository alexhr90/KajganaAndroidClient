package com.alexhr90.muabet

import android.content.Context
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alexhr90.muabet.data.settings.AppSettings
import com.alexhr90.muabet.data.settings.BackdropStyle
import com.alexhr90.muabet.data.settings.MotionMode
import com.alexhr90.muabet.data.settings.SettingsRepository
import com.alexhr90.muabet.designsystem.LaunchIntro
import com.alexhr90.muabet.designsystem.LocalBackdrop
import com.alexhr90.muabet.designsystem.LocalReducedMotion
import com.alexhr90.muabet.designsystem.MuabetBackdrop
import com.alexhr90.muabet.designsystem.MuabetTheme
import com.alexhr90.muabet.designsystem.isAppInDarkTheme
import com.alexhr90.muabet.ui.navigation.MuabetNavHost
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settings: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // No system scrim behind three-button navigation: the app's own background runs to the edge.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
        val firstLaunch = savedInstanceState == null
        setContent {
            val loaded by settings.settings.map<AppSettings, AppSettings?> { it }.collectAsStateWithLifecycle(initialValue = null)
            val s = loaded ?: AppSettings()
            val context = LocalContext.current
            val reduced = when (s.motionMode) {
                MotionMode.FULL -> false
                MotionMode.REDUCED -> true
                MotionMode.SYSTEM -> systemAnimationsOff(context)
            }
            MuabetTheme(darkTheme = isAppInDarkTheme(s.themeMode), reducedMotion = reduced) {
                // Saved, so rotation never replays it.
                var showIntro by rememberSaveable { mutableStateOf(firstLaunch) }
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    if (loaded != null) {
                        val backdrop = s.backdrop != BackdropStyle.NONE
                        if (backdrop) MuabetBackdrop(s.backdrop)
                        CompositionLocalProvider(LocalBackdrop provides backdrop) {
                            MuabetNavHost()
                        }
                    }
                    if (showIntro && !LocalReducedMotion.current) {
                        LaunchIntro(onFinished = { showIntro = false })
                    }
                }
            }
        }
    }

    private fun systemAnimationsOff(context: Context): Boolean = runCatching {
        android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }.getOrDefault(false)
}
