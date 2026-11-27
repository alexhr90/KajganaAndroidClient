package com.alexhr90.muabet.data.backup

import android.content.Context
import android.net.Uri
import com.alexhr90.muabet.data.local.FavoriteForumEntity
import com.alexhr90.muabet.data.local.HiddenForumEntity
import com.alexhr90.muabet.data.local.LibraryDao
import com.alexhr90.muabet.data.local.MutedKeywordEntity
import com.alexhr90.muabet.data.local.MutedThreadEntity
import com.alexhr90.muabet.data.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Backup and restore (SPEC §4 M4): favourites, hidden forums, mutes and settings as one JSON
 * file the user picks. Never cookies, never post content, never saved pages.
 */
@Serializable
data class BackupFile(
    val app: String = "muabet",
    val version: Int = 1,
    val favorites: List<BackupForum> = emptyList(),
    val hidden: List<BackupForum> = emptyList(),
    val mutedThreads: List<BackupThread> = emptyList(),
    val keywords: List<String> = emptyList(),
    val settings: BackupSettings = BackupSettings(),
)

@Serializable
data class BackupForum(val id: Long, val slug: String = "", val title: String)

@Serializable
data class BackupThread(val id: Long, val title: String)

/** Settings by name, so a newer or older app reads what it knows and keeps defaults otherwise. */
@Serializable
data class BackupSettings(
    val theme: String? = null,
    val motion: String? = null,
    val backdrop: String? = null,
    val textScale: Float? = null,
    val density: String? = null,
    val imagesOnTap: Boolean? = null,
    val collapsedCategories: List<String>? = null,
    val notifyIntervalMin: Int? = null,
    val quietStart: Int? = null,
    val quietEnd: Int? = null,
    val savedCapMb: Int? = null,
)

/** What a restore brought back. */
data class RestoreSummary(val favorites: Int, val hidden: Int, val mutedThreads: Int, val keywords: Int)

@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val library: LibraryDao,
    private val settings: SettingsRepository,
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }

    suspend fun export(uri: Uri) = withContext(Dispatchers.IO) {
        val s = settings.current()
        val file = BackupFile(
            favorites = library.favoritesNow().map { BackupForum(it.id, it.slug, it.title) },
            hidden = library.hiddenNow().map { BackupForum(it.id, title = it.title) },
            mutedThreads = library.mutedThreadsNow().map { BackupThread(it.id, it.title) },
            keywords = library.keywordsNow().map { it.keyword },
            settings = BackupSettings(
                theme = s.themeMode.name,
                motion = s.motionMode.name,
                backdrop = s.backdrop.name,
                textScale = s.textScale,
                density = s.density.name,
                imagesOnTap = s.imagesOnTap,
                collapsedCategories = s.collapsedCategories.sorted(),
                notifyIntervalMin = s.notifyIntervalMin,
                quietStart = s.quietStart,
                quietEnd = s.quietEnd,
                savedCapMb = s.savedCapMb,
            ),
        )
        val out = context.contentResolver.openOutputStream(uri, "wt") ?: error("Cannot write the file")
        out.use { it.write(json.encodeToString(BackupFile.serializer(), file).toByteArray(Charsets.UTF_8)) }
    }

    /** Merges a backup into what is there. Notifications stay as they are (they need a permission). */
    suspend fun restore(uri: Uri): RestoreSummary = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: error("Cannot read the file")
        val file = json.decodeFromString(BackupFile.serializer(), text)
        require(file.app == "muabet") { "Not a Muabet backup" }
        val start = library.favoritesNow().size
        library.upsertFavorites(file.favorites.mapIndexed { i, f -> FavoriteForumEntity(f.id, f.slug, f.title, start + i) })
        library.upsertHidden(file.hidden.map { HiddenForumEntity(it.id, it.title) })
        val now = System.currentTimeMillis()
        library.upsertMutedThreads(file.mutedThreads.map { MutedThreadEntity(it.id, it.title, now) })
        library.insertKeywords(file.keywords.filter { it.isNotBlank() }.map { MutedKeywordEntity(it) })
        val b = file.settings
        settings.update { s ->
            s.copy(
                themeMode = enumOr(b.theme, s.themeMode),
                motionMode = enumOr(b.motion, s.motionMode),
                backdrop = enumOr(b.backdrop, s.backdrop),
                textScale = b.textScale?.coerceIn(0.85f, 1.5f) ?: s.textScale,
                density = enumOr(b.density, s.density),
                imagesOnTap = b.imagesOnTap ?: s.imagesOnTap,
                collapsedCategories = b.collapsedCategories?.toSet() ?: s.collapsedCategories,
                notifyIntervalMin = b.notifyIntervalMin?.coerceAtLeast(15) ?: s.notifyIntervalMin,
                quietStart = b.quietStart?.coerceIn(0, 23) ?: s.quietStart,
                quietEnd = b.quietEnd?.coerceIn(0, 23) ?: s.quietEnd,
                savedCapMb = b.savedCapMb ?: s.savedCapMb,
            )
        }
        RestoreSummary(file.favorites.size, file.hidden.size, file.mutedThreads.size, file.keywords.size)
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default

}
