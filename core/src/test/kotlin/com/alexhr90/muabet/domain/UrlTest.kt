package com.alexhr90.muabet.domain

import com.alexhr90.muabet.domain.url.ForumUrls
import com.alexhr90.muabet.domain.url.Route
import com.alexhr90.muabet.domain.url.ThreadTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlTest {
    @Test
    fun buildsForumAndThreadUrls() {
        assertEquals("https://forum.kajgana.com/forums/sport.3/", ForumUrls.forum("sport", 3))
        assertEquals("https://forum.kajgana.com/forums/sport.3/page-4", ForumUrls.forum("sport", 3, 4))
        assertEquals("https://forum.kajgana.com/threads/tema.100/page-1046", ForumUrls.thread("tema", 100, 1046))
        assertEquals("https://forum.kajgana.com/threads/100/", ForumUrls.thread("", 100))
        assertEquals("https://forum.kajgana.com/posts/55/", ForumUrls.post(55))
    }

    @Test
    fun routesForumLinksNatively() {
        assertEquals(Route.Home, ForumUrls.route("https://forum.kajgana.com/"))
        assertEquals(Route.Forum(3, "sport", 1), ForumUrls.route("https://forum.kajgana.com/forums/sport.3/"))
        assertEquals(Route.Forum(3, "sport", 7), ForumUrls.route("https://www.forum.kajgana.com/forums/sport.3/page-7"))
        assertEquals(Route.Thread(100, "tema", 12), ForumUrls.route("https://forum.kajgana.com/threads/tema.100/page-12"))
        assertEquals(Route.Thread(100, "tema.so.tocki", 1), ForumUrls.route("/threads/tema.so.tocki.100/"))
        assertEquals(Route.Thread(100, "tema", 1, ThreadTarget.Unread), ForumUrls.route("https://forum.kajgana.com/threads/tema.100/unread"))
        assertEquals(Route.Thread(100, "tema", 1, ThreadTarget.Latest), ForumUrls.route("https://forum.kajgana.com/threads/tema.100/latest"))
        assertEquals(Route.Post(2000), ForumUrls.route("https://forum.kajgana.com/threads/tema.100/post-2000"))
        assertEquals(Route.Post(2000), ForumUrls.route("https://forum.kajgana.com/threads/tema.100/page-3#post-2000"))
        assertEquals(Route.Post(77), ForumUrls.route("https://forum.kajgana.com/posts/77/"))
        assertEquals(Route.Post(1999), ForumUrls.route("https://forum.kajgana.com/goto/post?id=1999"))
        assertEquals(Route.Member(10, "primer-korisnik"), ForumUrls.route("https://forum.kajgana.com/members/primer-korisnik.10/"))
        assertEquals(Route.Category(1), ForumUrls.route("https://forum.kajgana.com/categories/opshto.1/"))
    }

    @Test
    fun otherLinksGoToTheWeb() {
        assertEquals(Route.ForumWeb("https://forum.kajgana.com/login/"), ForumUrls.route("https://forum.kajgana.com/login/"))
        assertEquals(Route.External("https://example.com/x"), ForumUrls.route("https://example.com/x"))
        assertEquals(Route.External("mailto:a@b.c"), ForumUrls.route("mailto:a@b.c"))
        assertTrue(ForumUrls.isForumHost("FORUM.kajgana.com"))
    }

    @Test
    fun makesLinksAbsolute() {
        assertEquals("https://forum.kajgana.com/data/a.jpg", ForumUrls.absolute("/data/a.jpg"))
        assertEquals("https://cdn.example.com/a.jpg", ForumUrls.absolute("//cdn.example.com/a.jpg"))
        assertNull(ForumUrls.absolute("#top"))
        assertNull(ForumUrls.absolute("data:image/gif;base64,AAAA"))
        assertEquals(1046, ForumUrls.pageOf("/threads/tema.100/page-1046"))
    }
}
