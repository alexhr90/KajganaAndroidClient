package com.alexhr90.muabet.data.repo

import com.alexhr90.muabet.data.remote.FetchResult
import com.alexhr90.muabet.data.remote.ForumFetcher
import com.alexhr90.muabet.domain.model.ForumHome
import com.alexhr90.muabet.domain.model.ForumPage
import com.alexhr90.muabet.domain.model.SessionInfo
import com.alexhr90.muabet.domain.model.ThreadPage
import com.alexhr90.muabet.domain.net.BlockKind
import com.alexhr90.muabet.domain.parse.ForumParser
import com.alexhr90.muabet.domain.parse.HomeParser
import com.alexhr90.muabet.domain.parse.ParseException
import com.alexhr90.muabet.domain.parse.ThreadParser
import com.alexhr90.muabet.domain.url.ForumUrls
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
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

/**
 * Reads forum pages and parses them in `:core`. Every page also updates [session], so the app
 * notices a login, a logout or an expired session on whatever page it loads next.
 */
@Singleton
class ForumRepository @Inject constructor(private val fetcher: ForumFetcher) {

    private val _session = MutableStateFlow<SessionInfo?>(null)
    /** The session as the last loaded page showed it; null before the first page. */
    val session: StateFlow<SessionInfo?> = _session.asStateFlow()

    suspend fun home(): Loaded<ForumHome> = load(ForumUrls.home) { html, url -> HomeParser.parse(html, url).also { _session.value = it.session } }

    suspend fun forum(slug: String, id: Long, page: Int): Loaded<ForumPage> =
        load(ForumUrls.forum(slug, id, page)) { html, url -> ForumParser.parse(html, url).also { _session.value = it.session } }

    suspend fun thread(slug: String, id: Long, page: Int): Loaded<ThreadPage> =
        load(ForumUrls.thread(slug, id, page)) { html, url -> ThreadParser.parse(html, url).also { _session.value = it.session } }

    /** Any thread link (unread, latest, a post): the forum redirects to the right page. */
    suspend fun threadAt(url: String): Loaded<ThreadPage> =
        load(url) { html, finalUrl -> ThreadParser.parse(html, finalUrl).also { _session.value = it.session } }

    /** A session seen outside the page loads above (the session check). */
    fun noteSession(info: SessionInfo) {
        _session.value = info
    }

    fun forget() {
        _session.value = SessionInfo.Guest
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
