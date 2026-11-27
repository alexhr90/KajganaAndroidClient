package com.alexhr90.muabet.domain

import com.alexhr90.muabet.domain.filter.MuteRules
import com.alexhr90.muabet.domain.filter.PostText
import com.alexhr90.muabet.domain.model.Block
import com.alexhr90.muabet.domain.model.Run
import com.alexhr90.muabet.domain.model.ThreadSummary
import com.alexhr90.muabet.domain.parse.ListParser
import com.alexhr90.muabet.domain.parse.ParseException
import com.alexhr90.muabet.domain.parse.ThreadParser
import com.alexhr90.muabet.domain.url.ForumUrls
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ListTest {

    @Test
    fun whatsNewListsThreadsWithForumsAndBadges() {
        val list = ListParser.threads(fixture("whats_new.html"), "https://forum.kajgana.com/whats-new/posts/9001/")
        assertEquals(listOf(300L, 301L), list.threads.map { it.id })
        val first = list.threads[0]
        assertTrue(first.unread)
        assertEquals("https://forum.kajgana.com/threads/prva-tema.300/", first.url)
        assertEquals(2L, first.forumId)
        assertEquals("Општи разговори", first.forumTitle)
        assertEquals(1234, first.replies)
        val second = list.threads[1]
        assertEquals("Инфо", second.prefix)
        assertEquals("Втора тема за ФУДБАЛ", second.title)
        assertEquals(7L, second.forumId)
        assertEquals(41, second.lastPage)
        assertEquals(3, list.page.last)
        assertEquals("https://forum.kajgana.com/whats-new/posts/9001/page-2", list.nextUrl)
        assertEquals(5, list.session.alertsUnread)
        assertEquals(2, list.session.conversationsUnread)
    }

    @Test
    fun alertsKeepTextLinkAndUnreadState() {
        val list = ListParser.alerts(fixture("alerts.html"), "https://forum.kajgana.com/account/alerts")
        assertEquals(listOf(5001L, 5000L), list.alerts.map { it.id })
        val a = list.alerts[0]
        assertTrue(a.unread)
        assertEquals("avtor_eden", a.author)
        assertTrue(a.text.startsWith("avtor_eden одговори на темата Прва тема"))
        assertFalse(a.text.contains("денес"))
        assertEquals("https://forum.kajgana.com/threads/prva-tema.300/post-7001", a.url)
        assertEquals("https://forum.kajgana.com/data/avatars/s/0/20.jpg", a.avatarUrl)
        assertEquals(1764200000L, a.timestamp)
        val b = list.alerts[1]
        assertFalse(b.unread)
        assertEquals("https://forum.kajgana.com/posts/6990/", b.url)
        assertNull(list.nextUrl)
        assertEquals(0, list.session.alertsUnread)
    }

    @Test
    fun searchResultsAndEmptySearch() {
        val page = ListParser.search(fixture("search.html"), "https://forum.kajgana.com/search/77/?q=zbor&o=date")
        assertEquals(2, page.results.size)
        val r = page.results[0]
        assertEquals("Прва тема", r.title)
        assertEquals(7001L, r.postId)
        assertEquals("Пример текст со збор во него.", r.snippet)
        assertEquals("avtor_eden", r.author)
        assertEquals("Општи разговори", r.forumTitle)
        assertEquals(301L, page.results[1].threadId)
        assertNull(page.results[1].postId)
        assertEquals("https://forum.kajgana.com/search/77/?page=2&q=zbor&o=date", page.nextUrl)

        val empty = ListParser.search(fixture("search_empty.html"), "https://forum.kajgana.com/search/search")
        assertTrue(empty.results.isEmpty())
    }

    @Test(expected = ParseException::class)
    fun aPageWithoutAListIsAParseError() {
        ListParser.threads("<html><body><p>Нешто друго</p></body></html>", "https://forum.kajgana.com/watched/threads")
    }

    @Test
    fun postAuthorHasProfileLink() {
        val page = ThreadParser.parse(fixture("thread_page.html"), "https://forum.kajgana.com/threads/primer-tema.100/")
        assertEquals("https://forum.kajgana.com/members/primer-korisnik.10/", page.posts[0].author.profileUrl)
    }

    @Test
    fun muteRulesHideThreadsKeywordsAndForums() {
        fun t(id: Long, title: String, forum: Long? = null) = ThreadSummary(id = id, slug = "", title = title, url = "", forumId = forum)
        val rules = MuteRules(threadIds = setOf(1L), keywords = listOf("  фудбал ", "SPOILER"), hiddenForumIds = setOf(9L))
        assertTrue(rules.hides(t(1, "Било што")))
        assertTrue(rules.hides(t(2, "Втора тема за ФУДБАЛ")))
        assertTrue(rules.hides(t(3, "Spoiler alert")))
        assertTrue(rules.hides(t(4, "Тема", forum = 9)))
        assertFalse(rules.hides(t(5, "Кошарка", forum = 2)))
        assertEquals(listOf(5L), rules.filter(listOf(t(1, "x"), t(5, "Кошарка"))).map { it.id })
        assertTrue(MuteRules.None.isEmpty)
        assertFalse(MuteRules.None.hides(t(1, "фудбал")))
    }

    @Test
    fun postTextSkipsQuotesWhenAsked() {
        val body = listOf(
            Block.Quote("x", null, listOf(Block.Paragraph(listOf(Run("цитат"))))),
            Block.Paragraph(listOf(Run("мој "), Run("текст", bold = true))),
        )
        assertEquals("мој текст", PostText.plain(body, includeQuotes = false))
        assertTrue(PostText.plain(body).contains("цитат"))
    }

    @Test
    fun newUrls() {
        assertEquals(
            "https://forum.kajgana.com/search/search?keywords=%D0%B7%D0%B1%D0%BE%D1%80+two&o=date&c%5Bthread%5D=300",
            ForumUrls.search(" збор two ", threadId = 300),
        )
        assertEquals("https://forum.kajgana.com/threads/prva-tema.300/reply?quote=7001", ForumUrls.quote("prva-tema", 300, 7001))
        assertEquals("https://forum.kajgana.com/forums/sport.7/post-thread", ForumUrls.postThread("sport", 7))
        assertEquals("https://forum.kajgana.com/x.jpg", ForumUrls.preferHttps("http://forum.kajgana.com/x.jpg"))
        assertEquals("http://example.com/x.jpg", ForumUrls.preferHttps("http://example.com/x.jpg"))
    }
}
