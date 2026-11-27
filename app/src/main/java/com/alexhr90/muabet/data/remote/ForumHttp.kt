package com.alexhr90.muabet.data.remote

import android.webkit.CookieManager
import com.alexhr90.muabet.domain.net.Backoff
import com.alexhr90.muabet.domain.net.BlockDetector
import com.alexhr90.muabet.domain.net.BlockKind
import com.alexhr90.muabet.domain.net.Politeness
import com.alexhr90.muabet.domain.net.RequestSpacing
import com.alexhr90.muabet.domain.url.ForumUrls
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.Semaphore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The session bridge (SPEC §3): the user logs in on the forum's own page in the WebView, and
 * OkHttp reads and writes the same cookies through [CookieManager]. The app never sees the
 * password and never logs, stores or exports cookie values.
 */
class WebViewCookieJar : CookieJar {
    private val manager: CookieManager get() = CookieManager.getInstance()

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        if (!ForumUrls.isForumHost(url.host)) return emptyList()
        val header = runCatching { manager.getCookie(url.toString()) }.getOrNull() ?: return emptyList()
        return header.split(';').mapNotNull { Cookie.parse(url, it.trim()) }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (!ForumUrls.isForumHost(url.host)) return
        runCatching {
            cookies.forEach { manager.setCookie(url.toString(), it.toString()) }
            manager.flush()
        }
    }

    /** The names (never the values) of the forum cookies the WebView holds. */
    fun cookieNames(): List<String> {
        val header = runCatching { manager.getCookie(ForumUrls.home) }.getOrNull() ?: return emptyList()
        return header.split(';').map { it.substringBefore('=').trim() }.filter { it.isNotEmpty() }.sorted()
    }
}

/**
 * Politeness on every forum request: at most two at once, one per second, back-off after errors
 * and rate limits, and the honest User-Agent. Requests to other hosts (images) only get the
 * User-Agent.
 */
class PolitenessInterceptor(private val userAgent: String) : Interceptor {
    private val slots = Semaphore(Politeness.MAX_CONCURRENT, true)
    private val spacing = RequestSpacing()
    private val backoff = Backoff()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder().header("User-Agent", userAgent).build()
        if (!ForumUrls.isForumHost(request.url.host)) return chain.proceed(request)
        slots.acquire()
        try {
            val now = System.currentTimeMillis()
            val wait = maxOf(backoff.waitMs(now), spacing.reserve(now))
            if (wait > 0) Thread.sleep(wait)
            val response = try {
                chain.proceed(request)
            } catch (e: IOException) {
                backoff.onFailure(System.currentTimeMillis())
                throw e
            }
            if (backoff.isBackoffStatus(response.code)) {
                backoff.onFailure(System.currentTimeMillis(), BlockDetector.retryAfterMs(response.header("Retry-After")))
            } else {
                backoff.onSuccess()
            }
            return response
        } finally {
            slots.release()
        }
    }
}

/** A fetched forum page, or why there is none. */
sealed interface FetchResult {
    data class Page(val url: String, val code: Int, val html: String) : FetchResult
    /** A bot check or block: show the page in the WebView and tell the user (never work around it). */
    data class Blocked(val url: String, val kind: BlockKind, val code: Int) : FetchResult
    data class HttpError(val url: String, val code: Int) : FetchResult
    data class NetworkError(val url: String, val message: String?) : FetchResult
}

@Singleton
class ForumFetcher @Inject constructor(private val client: OkHttpClient) {

    suspend fun get(url: String): FetchResult = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "text/html,application/xhtml+xml")
            .build()
        try {
            client.newCall(request).execute().use { response ->
                val finalUrl = response.request.url.toString()
                // Forum pages are small; cap the read so a wrong link cannot fill memory.
                val body = response.body?.let { b ->
                    val source = b.source()
                    source.request(MAX_BYTES)
                    source.buffer.clone().readUtf8()
                }.orEmpty()
                val kind = BlockDetector.classify(response.code, body)
                when {
                    kind != BlockKind.None -> FetchResult.Blocked(finalUrl, kind, response.code)
                    response.isSuccessful -> FetchResult.Page(finalUrl, response.code, body)
                    // XenForo answers "no permission" and "not found" with a normal forum page.
                    (response.code == 403 || response.code == 404) && body.contains("data-xf-init") -> FetchResult.Page(finalUrl, response.code, body)
                    else -> FetchResult.HttpError(finalUrl, response.code)
                }
            }
        } catch (e: IOException) {
            FetchResult.NetworkError(url, e.message)
        }
    }

    private companion object {
        const val MAX_BYTES = 6L * 1024 * 1024
    }
}
