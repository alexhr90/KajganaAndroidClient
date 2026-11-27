package com.alexhr90.muabet.domain.net

/**
 * The politeness rules (SPEC §3): at most [MAX_CONCURRENT] requests at once and one request per
 * [MIN_INTERVAL_MS], with back-off after errors. Pure logic on a caller-supplied clock, so it is
 * unit tested on the JVM; the app's OkHttp interceptor applies it.
 */
object Politeness {
    const val MAX_CONCURRENT = 2
    const val MIN_INTERVAL_MS = 1_000L
    const val BACKOFF_START_MS = 2_000L
    const val BACKOFF_MAX_MS = 5 * 60_000L

    fun userAgent(version: String): String = "Muabet/$version (+https://github.com/alexhr90/KajganaAndroidClient)"
}

/** Hands out start times at least [intervalMs] apart. Thread safe. */
class RequestSpacing(private val intervalMs: Long = Politeness.MIN_INTERVAL_MS) {
    private var next = 0L

    /** Reserves the next slot; returns how long to wait from [now] before sending. */
    @Synchronized
    fun reserve(now: Long): Long {
        val start = maxOf(now, next)
        next = start + intervalMs
        return start - now
    }
}

/**
 * Exponential back-off after server errors, rate limits and network failures: 2 s, 4 s, 8 s…
 * up to 5 minutes, or the server's `Retry-After` when it is longer. Reset by a success.
 */
class Backoff(
    private val startMs: Long = Politeness.BACKOFF_START_MS,
    private val maxMs: Long = Politeness.BACKOFF_MAX_MS,
) {
    private var failures = 0
    private var until = 0L

    @Synchronized
    fun onFailure(now: Long, retryAfterMs: Long? = null) {
        failures++
        val exp = (startMs shl (failures - 1).coerceAtMost(20)).coerceAtMost(maxMs)
        val wait = maxOf(exp, (retryAfterMs ?: 0L).coerceAtMost(maxMs))
        until = now + wait
    }

    @Synchronized
    fun onSuccess() {
        failures = 0
        until = 0L
    }

    /** How long to wait from [now] before the next request may go out. */
    @Synchronized
    fun waitMs(now: Long): Long = (until - now).coerceAtLeast(0L)

    /** True for responses that should slow the app down. */
    fun isBackoffStatus(code: Int): Boolean = code == 429 || code in 500..599
}

/** What a response looks like when the forum refuses an automated client. */
enum class BlockKind { None, Challenge, RateLimited, Forbidden }

/**
 * Recognises a bot check or a block (SPEC §7). The app never works around one: it shows the page
 * in the WebView and tells the user.
 */
object BlockDetector {
    private val challengeMarkers = listOf(
        "cf-chl", "challenge-platform", "cf_chl_opt", "Just a moment...", "cf-turnstile",
        "g-recaptcha", "hcaptcha", "ddos-guard", "Checking your browser",
    )

    fun classify(code: Int, body: String?): BlockKind {
        val text = body.orEmpty()
        val head = if (text.length > 20_000) text.substring(0, 20_000) else text
        if (challengeMarkers.any { head.contains(it, ignoreCase = true) } && !head.contains("data-xf-init", ignoreCase = false)) {
            return BlockKind.Challenge
        }
        return when (code) {
            429 -> BlockKind.RateLimited
            403 -> if (head.contains("data-xf-init")) BlockKind.None else BlockKind.Forbidden
            else -> BlockKind.None
        }
    }

    /** `Retry-After` in milliseconds (seconds form only), or null. */
    fun retryAfterMs(header: String?): Long? = header?.trim()?.toLongOrNull()?.takeIf { it >= 0 }?.times(1000)
}
