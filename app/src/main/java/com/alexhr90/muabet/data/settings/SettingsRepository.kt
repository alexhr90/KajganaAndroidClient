package com.alexhr90.muabet.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class MotionMode { SYSTEM, FULL, REDUCED }

/** The painted backdrop behind the app (from Izvor); AUTO follows the time of day. */
enum class BackdropStyle { NONE, AUTO, DAWN, DAY, DUSK, NIGHT, AURORA, EMBER }

/** Everything the user can set. Stored by enum name, so never rename a constant. */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val motionMode: MotionMode = MotionMode.SYSTEM,
    // As in Izvor (SPEC §7 open question default).
    val backdrop: BackdropStyle = BackdropStyle.AUTO,
)

private val Context.dataStore by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val context: Context) {

    private object Keys {
        val theme = stringPreferencesKey("theme")
        val motion = stringPreferencesKey("motion")
        val backdrop = stringPreferencesKey("backdrop")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        val d = AppSettings()
        AppSettings(
            themeMode = enumOr(p[Keys.theme], d.themeMode),
            motionMode = enumOr(p[Keys.motion], d.motionMode),
            backdrop = enumOr(p[Keys.backdrop], d.backdrop),
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun update(change: (AppSettings) -> AppSettings) {
        context.dataStore.edit { p ->
            val old = AppSettings(
                themeMode = enumOr(p[Keys.theme], ThemeMode.SYSTEM),
                motionMode = enumOr(p[Keys.motion], MotionMode.SYSTEM),
                backdrop = enumOr(p[Keys.backdrop], BackdropStyle.AUTO),
            )
            val n = change(old)
            p[Keys.theme] = n.themeMode.name
            p[Keys.motion] = n.motionMode.name
            p[Keys.backdrop] = n.backdrop.name
        }
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default
}
