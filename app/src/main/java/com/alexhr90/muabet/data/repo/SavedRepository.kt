package com.alexhr90.muabet.data.repo

import com.alexhr90.muabet.data.local.SavedDao
import com.alexhr90.muabet.data.local.SavedPageEntity
import com.alexhr90.muabet.data.local.SavedThreadEntity
import com.alexhr90.muabet.data.local.SavedThreadSummary
import com.alexhr90.muabet.data.remote.FetchResult
import com.alexhr90.muabet.data.settings.SettingsRepository
import com.alexhr90.muabet.domain.parse.ThreadParser
import com.alexhr90.muabet.domain.url.ForumUrls
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/** How a save went. */
sealed interface SaveResult {
    data class Saved(val pages: Int) : SaveResult
    /** The size cap was reached after [pages] pages. */
    data class Full(val pages: Int) : SaveResult
    /** The forum could not be reached or did not send a thread page, after [pages] pages. */
    data class Failed(val pages: Int) : SaveResult
}

/**
 * Offline saved threads (SPEC §4 M3): the pages the user chose, kept as compressed forum HTML and
 * parsed again when read. Capped in size; never exported.
 */
@Singleton
class SavedRepository @Inject constructor(
    private val dao: SavedDao,
    private val forum: ForumRepository,
    private val settings: SettingsRepository,
) {
    val threads: Flow<List<SavedThreadSummary>> = dao.threads()

    fun pages(threadId: Long): Flow<List<Int>> = dao.pages(threadId)

    /** Saves pages [from]..[to] of a thread, one polite request at a time. */
    suspend fun save(threadId: Long, slug: String, title: String, forumTitle: String?, from: Int, to: Int, onProgress: (Int) -> Unit = {}): SaveResult {
        val cap = settings.current().savedCapMb * 1024L * 1024L
        dao.upsertThread(SavedThreadEntity(threadId, slug, title, forumTitle, System.currentTimeMillis()))
        var done = 0
        for (page in from..to) {
            if (dao.totalBytes() >= cap) return SaveResult.Full(done)
            val r = forum.raw(ForumUrls.thread(slug, threadId, page))
            if (r !is FetchResult.Page) return SaveResult.Failed(done)
            // Only real thread pages are kept.
            val ok = withContext(Dispatchers.Default) { runCatching { ThreadParser.parse(r.html, r.url) }.isSuccess }
            if (!ok) return SaveResult.Failed(done)
            val bytes = withContext(Dispatchers.Default) { gzip(r.html) }
            dao.upsertPage(SavedPageEntity(threadId, page, bytes, bytes.size, System.currentTimeMillis()))
            done++
            onProgress(done)
        }
        return SaveResult.Saved(done)
    }

    suspend fun delete(threadId: Long) = dao.deleteThread(threadId)

    private fun gzip(text: String): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(text.toByteArray(Charsets.UTF_8)) }
        return out.toByteArray()
    }
}
