package com.alexhr90.muabet.data.settings

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class MotionMode { SYSTEM, FULL, REDUCED }

/** How much room list rows take. */
enum class RowDensity { COMFORTABLE, COMPACT }

/** The painted backdrop behind the app (from Izvor); AUTO follows the time of day. */
enum class BackdropStyle { NONE, AUTO, DAWN, DAY, DUSK, NIGHT, AURORA, EMBER }

/** Everything the user can set. Enums are stored by name, so never rename a constant. */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val motionMode: MotionMode = MotionMode.SYSTEM,
    // As in Izvor (SPEC §7 open question default).
    val backdrop: BackdropStyle = BackdropStyle.AUTO,
    /** Text size on top of the system's, 0.85 to 1.5. */
    val textScale: Float = 1f,
    val density: RowDensity = RowDensity.COMFORTABLE,
    /** Post images show a placeholder until tapped (saves data). */
    val imagesOnTap: Boolean = false,
    /** Category keys the user folded on the Forums tab. */
    val collapsedCategories: Set<String> = emptySet(),
    /** Alert notifications (SPEC §4 M4): off by default. */
    val notifications: Boolean = false,
    /** Minutes between alert checks; never below 15. */
    val notifyIntervalMin: Int = 30,
    /** Quiet hours, 0..23 each; equal start and end mean none. */
    val quietStart: Int = 23,
    val quietEnd: Int = 7,
    /** Offline copies stop at this size. */
    val savedCapMb: Int = 100,
) {
    val hasQuietHours: Boolean get() = quietStart != quietEnd

    /** True when [hour] (0..23) falls in the quiet hours. */
    fun isQuiet(hour: Int): Boolean = when {
        !hasQuietHours -> false
        quietStart < quietEnd -> hour in quietStart until quietEnd
        else -> hour >= quietStart || hour < quietEnd
    }

    companion object {
        val TEXT_SCALES = listOf(0.85f, 1f, 1.15f, 1.3f, 1.5f)
        val INTERVALS = listOf(15, 30, 60, 180)
        val SAVED_CAPS = listOf(50, 100, 250, 500)
    }
}

/** Counters the alert worker keeps between runs. Not user settings, never backed up. */
data class AlertState(val lastAlerts: Int = 0, val lastConversations: Int = 0)

private val Context.dataStore by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val context: Context) {

    private object Keys {
        val theme = stringPreferencesKey("theme")
        val motion = stringPreferencesKey("motion")
        val backdrop = stringPreferencesKey("backdrop")
        val textScale = floatPreferencesKey("text_scale")
        val density = stringPreferencesKey("density")
        val imagesOnTap = booleanPreferencesKey("images_on_tap")
        val collapsed = stringSetPreferencesKey("collapsed_categories")
        val notifications = booleanPreferencesKey("notifications")
        val interval = intPreferencesKey("notify_interval")
        val quietStart = intPreferencesKey("quiet_start")
        val quietEnd = intPreferencesKey("quiet_end")
        val savedCap = intPreferencesKey("saved_cap_mb")
        val lastAlerts = intPreferencesKey("last_alerts")
        val lastConversations = intPreferencesKey("last_conversations")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { read(it) }.distinctUntilChanged()

    suspend fun current(): AppSettings = settings.first()

    suspend fun update(change: (AppSettings) -> AppSettings) {
        context.dataStore.edit { p -> write(p, change(read(p))) }
    }

    suspend fun alertState(): AlertState = context.dataStore.data.first().let { p ->
        AlertState(p[Keys.lastAlerts] ?: 0, p[Keys.lastConversations] ?: 0)
    }

    suspend fun setAlertState(state: AlertState) {
        context.dataStore.edit { p ->
            p[Keys.lastAlerts] = state.lastAlerts
            p[Keys.lastConversations] = state.lastConversations
        }
    }

    private fun read(p: Preferences): AppSettings {
        val d = AppSettings()
        return AppSettings(
            themeMode = enumOr(p[Keys.theme], d.themeMode),
            motionMode = enumOr(p[Keys.motion], d.motionMode),
            backdrop = enumOr(p[Keys.backdrop], d.backdrop),
            textScale = (p[Keys.textScale] ?: d.textScale).coerceIn(0.85f, 1.5f),
            density = enumOr(p[Keys.density], d.density),
            imagesOnTap = p[Keys.imagesOnTap] ?: d.imagesOnTap,
            collapsedCategories = p[Keys.collapsed] ?: d.collapsedCategories,
            notifications = p[Keys.notifications] ?: d.notifications,
            notifyIntervalMin = (p[Keys.interval] ?: d.notifyIntervalMin).coerceAtLeast(15),
            quietStart = (p[Keys.quietStart] ?: d.quietStart).coerceIn(0, 23),
            quietEnd = (p[Keys.quietEnd] ?: d.quietEnd).coerceIn(0, 23),
            savedCapMb = (p[Keys.savedCap] ?: d.savedCapMb).coerceIn(10, 2_000),
        )
    }

    private fun write(p: MutablePreferences, s: AppSettings) {
        p[Keys.theme] = s.themeMode.name
        p[Keys.motion] = s.motionMode.name
        p[Keys.backdrop] = s.backdrop.name
        p[Keys.textScale] = s.textScale
        p[Keys.density] = s.density.name
        p[Keys.imagesOnTap] = s.imagesOnTap
        p[Keys.collapsed] = s.collapsedCategories
        p[Keys.notifications] = s.notifications
        p[Keys.interval] = s.notifyIntervalMin.coerceAtLeast(15)
        p[Keys.quietStart] = s.quietStart
        p[Keys.quietEnd] = s.quietEnd
        p[Keys.savedCap] = s.savedCapMb
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default
}
