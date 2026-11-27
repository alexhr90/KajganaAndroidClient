package com.alexhr90.muabet.data.repo

import android.webkit.CookieManager
import android.webkit.WebStorage
import com.alexhr90.muabet.data.remote.FetchResult
import com.alexhr90.muabet.data.remote.ForumFetcher
import com.alexhr90.muabet.data.remote.WebViewCookieJar
import com.alexhr90.muabet.domain.model.SessionInfo
import com.alexhr90.muabet.domain.net.BlockKind
import com.alexhr90.muabet.domain.parse.SessionParser
import com.alexhr90.muabet.domain.url.ForumUrls
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** What spike 1 found (SPEC §4 M0): does the WebView login carry over to OkHttp? */
data class SessionCheck(
    /** Names only, never values: which forum cookies the WebView holds. */
    val webViewCookies: List<String>,
    val httpCode: Int?,
    val blocked: BlockKind?,
    val networkError: Boolean,
    /** The session as OkHttp's request saw it. */
    val session: SessionInfo?,
    val durationMs: Long,
) {
    /** True when OkHttp, with the WebView's cookies, came back logged in: the spike passed. */
    val passed: Boolean get() = session?.loggedIn == true

    /** False when the login was made without "Stay logged in": the session ends with the app. */
    val remembered: Boolean get() = "xf_user" in webViewCookies
}

@Singleton
class SessionRepository @Inject constructor(
    private val fetcher: ForumFetcher,
    private val cookies: WebViewCookieJar,
    private val forum: ForumRepository,
) {
    fun cookieNames(): List<String> = cookies.cookieNames()

    /** True when the WebView holds the long-lived login cookie. */
    fun hasLoginCookie(): Boolean = "xf_user" in cookies.cookieNames()

    /** Spike 1: one authenticated OkHttp request with the WebView's cookies. */
    suspend fun check(): SessionCheck {
        val names = cookies.cookieNames()
        val start = System.currentTimeMillis()
        val r = fetcher.get(ForumUrls.account)
        val took = System.currentTimeMillis() - start
        return when (r) {
            is FetchResult.Page -> {
                val info = SessionParser.parse(r.html, r.url)
                forum.noteSession(info)
                SessionCheck(names, r.code, null, false, info, took)
            }
            is FetchResult.Blocked -> SessionCheck(names, r.code, r.kind, false, null, took)
            is FetchResult.HttpError -> SessionCheck(names, r.code, null, false, null, took)
            is FetchResult.NetworkError -> SessionCheck(names, null, null, true, null, took)
        }
    }

    /** Logs out locally: the forum's cookies and the WebView's storage are cleared. */
    suspend fun logout() {
        suspendCancellableCoroutine { cont ->
            CookieManager.getInstance().removeAllCookies { cont.resume(Unit) }
        }
        CookieManager.getInstance().flush()
        WebStorage.getInstance().deleteAllData()
        forum.forget()
    }
}
