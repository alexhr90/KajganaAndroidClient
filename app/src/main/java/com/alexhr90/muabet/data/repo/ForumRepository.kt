package com.alexhr90.muabet.data.repo

import com.alexhr90.muabet.data.local.SavedDao
import com.alexhr90.muabet.data.remote.FetchResult
import com.alexhr90.muabet.data.remote.ForumFetcher
import com.alexhr90.muabet.data.remote.WebViewCookieJar
import com.alexhr90.muabet.domain.model.AlertList
import com.alexhr90.muabet.domain.model.ForumHome
import com.alexhr90.muabet.domain.model.ForumPage
import com.alexhr90.muabet.domain.model.SearchPage
import com.alexhr90.muabet.domain.model.SessionInfo
import com.alexhr90.muabet.domain.model.ThreadList
import com.alexhr90.muabet.domain.model.ThreadPage
import com.alexhr90.muabet.domain.net.BlockKind
import com.alexhr90.muabet.domain.parse.ForumParser
import com.alexhr90.muabet.domain.parse.HomeParser
import com.alexhr90.muabet.domain.parse.ListParser
import com.alexhr90.muabet.domain.parse.ParseException
import com.alexhr90.muabet.domain.parse.ThreadParser
import com.alexhr90.muabet.domain.url.ForumUrls
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Singleton

/** A screen's data, or why it cannot be shown. Every failure carries the page to open on the forum. */
sealed interface Loaded<out T> {
    data class Ok<T>(val value: T) : Loaded<T>
    /** A bot check or block: never worked around; the user sees the page in the WebView. */
    data class Blocked(val url: String, val kind: BlockKind) : Loaded<Nothing>
    data class Offline(val url: String) : Loaded<Nothing>
    data class Failed(val url: String, val reason: String) : Loaded<Nothing>
}

/** [Loaded] with its value changed, failures passed through. */
inline fun <T, R> Loaded<T>.map(f: (T) -> R): Loaded<R> = when (this) {
    is Loaded.Ok -> Loaded.Ok(f(value))
    is Loaded.Blocked -> this
    is Loaded.Offline -> this
    is Loaded.Failed -> this
}

/**
 * Reads forum pages and parses them in `:core`. Every page also updates [session], so the app
 * notices a login, a logout or an expired session on whatever page it loads next.
 */
@Singleton
class ForumRepository @Inject constructor(
    private val fetcher: ForumFetcher,
    private val cookies: WebViewCookieJar,
    private val saved: SavedDao,
) {

    private val _session = MutableStateFlow<SessionInfo?>(null)
    /** The session as the last loaded page showed it; null before the first page. */
    val session: StateFlow<SessionInfo?> = _session.asStateFlow()

    private val _expired = MutableStateFlow(false)
    /**
     * True when a page came back as a guest although the WebView still holds the long-lived login
     * cookie: the forum ended the session (SPEC §4 M2). Cleared by the next logged-in page.
     */
    val sessionExpired: StateFlow<Boolean> = _expired.asStateFlow()

    suspend fun home(): Loaded<ForumHome> = load(ForumUrls.home) { html, url -> HomeParser.parse(html, url).also { note(it.session) } }

    suspend fun forum(slug: String, id: Long, page: Int): Loaded<ForumPage> =
        load(ForumUrls.forum(slug, id, page)) { html, url -> ForumParser.parse(html, url).also { note(it.session) } }

    suspend fun thread(slug: String, id: Long, page: Int): Loaded<ThreadPage> {
        val url = ForumUrls.thread(slug, id, page)
        val live = load(url) { html, finalUrl -> ThreadParser.parse(html, finalUrl).also { note(it.session) } }
        // No connection: a page saved for offline reading stands in (SPEC §4 M3).
        if (live is Loaded.Offline) savedPage(id, page, url)?.let { return Loaded.Ok(it) }
        return live
    }

    /** Any thread link (unread, latest, a post): the forum redirects to the right page. */
    suspend fun threadAt(url: String): Loaded<ThreadPage> =
        load(url) { html, finalUrl -> ThreadParser.parse(html, finalUrl).also { note(it.session) } }

    /** A thread list page: New posts, Watched threads or a next page of either. */
    suspend fun threadList(url: String): Loaded<ThreadList> =
        load(url) { html, finalUrl -> ListParser.threads(html, finalUrl).also { note(it.session) } }

    suspend fun alerts(url: String = ForumUrls.alerts): Loaded<AlertList> =
        load(url) { html, finalUrl -> ListParser.alerts(html, finalUrl).also { note(it.session) } }

    suspend fun search(url: String): Loaded<SearchPage> =
        load(url) { html, finalUrl -> ListParser.search(html, finalUrl).also { note(it.session) } }

    /** The raw page, for saving a thread page offline. */
    suspend fun raw(url: String): FetchResult = fetcher.get(url)

    /** A session seen outside the page loads above (the session check, the alert worker). */
    fun noteSession(info: SessionInfo) = note(info)

    fun forget() {
        _session.value = SessionInfo.Guest
        _expired.value = false
    }

    private fun note(info: SessionInfo) {
        _session.value = info
        _expired.value = !info.loggedIn && cookies.hasLoginCookie()
    }

    private suspend fun savedPage(threadId: Long, page: Int, url: String): ThreadPage? = withContext(Dispatchers.IO) {
        val row = saved.page(threadId, page) ?: return@withContext null
        val html = runCatching { GZIPInputStream(row.html.inputStream()).use { it.readBytes().toString(Charsets.UTF_8) } }.getOrNull()
            ?: return@withContext null
        runCatching { ThreadParser.parse(html, url).copy(fromSaved = true) }.getOrNull()
    }

    private suspend fun <T> load(url: String, parse: (String, String) -> T): Loaded<T> =
        when (val r = fetcher.get(url)) {
            is FetchResult.Page -> withContext(Dispatchers.Default) {
                try {
                    Loaded.Ok(parse(r.html, r.url))
                } catch (e: ParseException) {
                    Loaded.Failed(r.url, e.message ?: "parse")
                } catch (e: RuntimeException) {
                    Loaded.Failed(r.url, e.javaClass.simpleName)
                }
            }
            is FetchResult.Blocked -> Loaded.Blocked(r.url, r.kind)
            is FetchResult.HttpError -> Loaded.Failed(r.url, "HTTP ${r.code}")
            is FetchResult.NetworkError -> Loaded.Offline(r.url)
        }
}
