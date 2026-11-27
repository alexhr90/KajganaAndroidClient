package com.alexhr90.muabet.domain

import com.alexhr90.muabet.domain.net.Backoff
import com.alexhr90.muabet.domain.net.BlockDetector
import com.alexhr90.muabet.domain.net.BlockKind
import com.alexhr90.muabet.domain.net.Politeness
import com.alexhr90.muabet.domain.net.RequestSpacing
import org.junit.Assert.assertEquals
import org.junit.Test

class PolitenessTest {
    @Test
    fun requestsAreAtLeastOneSecondApart() {
        val spacing = RequestSpacing(1_000)
        assertEquals(0L, spacing.reserve(10_000))
        assertEquals(1_000L, spacing.reserve(10_000))
        assertEquals(1_500L, spacing.reserve(10_500))
        assertEquals(0L, spacing.reserve(20_000))
    }

    @Test
    fun backoffDoublesUpToTheCapAndResets() {
        val b = Backoff(startMs = 2_000, maxMs = 10_000)
        b.onFailure(0)
        assertEquals(2_000L, b.waitMs(0))
        b.onFailure(0)
        assertEquals(4_000L, b.waitMs(0))
        b.onFailure(0)
        b.onFailure(0)
        assertEquals(10_000L, b.waitMs(0))
        b.onSuccess()
        assertEquals(0L, b.waitMs(0))
        b.onFailure(0, retryAfterMs = 7_000)
        assertEquals(7_000L, b.waitMs(0))
    }

    @Test
    fun recognisesBotChecks() {
        assertEquals(BlockKind.Challenge, BlockDetector.classify(503, "<html><title>Just a moment...</title><script src=\"/cdn-cgi/challenge-platform/x.js\"></script></html>"))
        assertEquals(BlockKind.RateLimited, BlockDetector.classify(429, ""))
        assertEquals(BlockKind.Forbidden, BlockDetector.classify(403, "<html>Access denied</html>"))
        // XenForo's own "no permission" page is an ordinary forum page.
        assertEquals(BlockKind.None, BlockDetector.classify(403, "<html><body data-xf-init=\"x\">Немате дозвола</body></html>"))
        assertEquals(BlockKind.None, BlockDetector.classify(200, fixture("home_guest.html")))
        assertEquals(5_000L, BlockDetector.retryAfterMs("5"))
    }

    @Test
    fun userAgentNamesTheAppAndItsHome() {
        assertEquals("Muabet/0.1.0 (+https://github.com/alexhr90/KajganaAndroidClient)", Politeness.userAgent("0.1.0"))
    }
}
