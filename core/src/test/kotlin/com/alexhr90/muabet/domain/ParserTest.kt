package com.alexhr90.muabet.domain

import com.alexhr90.muabet.domain.model.Block
import com.alexhr90.muabet.domain.parse.BbContent
import com.alexhr90.muabet.domain.parse.ForumParser
import com.alexhr90.muabet.domain.parse.HomeParser
import com.alexhr90.muabet.domain.parse.ParseException
import com.alexhr90.muabet.domain.parse.SessionParser
import com.alexhr90.muabet.domain.parse.ThreadParser
import com.alexhr90.muabet.domain.parse.parseCount
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParserTest {

    @Test
    fun homeListsCategoriesForumsAndSubforums() {
        val home = HomeParser.parse(fixture("home_guest.html"))
        assertFalse(home.session.loggedIn)
        assertEquals(2, home.categories.size)
        val general = home.categories[0]
        assertEquals("Општо", general.title)
        assertEquals(1L, general.id)
        assertEquals(listOf(2L, 3L), general.forums.map { it.id })

        val talk = general.forums[0]
        assertEquals("Општи разговори", talk.title)
        assertEquals("opshti-razgovori", talk.slug)
        assertTrue(talk.unread)
        assertEquals("https://forum.kajgana.com/forums/opshti-razgovori.2/", talk.url)
        assertEquals(listOf("Вицови", "Игри"), talk.subforums.map { it.title })
        assertTrue(talk.subforums[0].unread)
        assertFalse(talk.subforums[1].unread)

        val latest = requireNotNull(talk.latest)
        assertEquals("Прва тема", latest.title)
        assertEquals(100L, latest.threadId)
        assertEquals("primer_korisnik", latest.author)
        assertEquals(1795770000L, latest.timestamp)

        assertNull(general.forums[1].latest)
        assertFalse(general.forums[1].unread)
    }

    @Test
    fun homeKeepsTheFullLatestTitleAndParsesIsoTimes() {
        val other = HomeParser.parse(fixture("home_guest.html")).categories[1]
        // The link forum has no forum id in its URL, so it is identified by its node class.
        assertEquals(listOf(8L, 9L), other.forums.map { it.id })
        assertTrue(other.forums[0].isLink)
        val latest = requireNotNull(other.forums[1].latest)
        assertEquals("Нов телефон, кој да го купам", latest.title)
        assertEquals(java.time.OffsetDateTime.parse("2026-11-26T21:15:00+01:00").toEpochSecond(), latest.timestamp)
    }

    @Test(expected = ParseException::class)
    fun homeWithoutForumsIsAParseFailure() {
        HomeParser.parse("<html><body><p>Something else</p></body></html>")
    }

    @Test
    fun forumPageSplitsPinnedThreadsAndReadsPages() {
        val url = "https://forum.kajgana.com/forums/opshti-razgovori.2/"
        val page = ForumParser.parse(fixture("forum_page1.html"), url)
        assertEquals(2L, page.forumId)
        assertEquals("Општи разговори", page.title)
        assertTrue(page.session.loggedIn)
        assertEquals("test_korisnik", page.session.username)
        assertEquals(1, page.page.current)
        assertEquals(87, page.page.last)
        assertEquals(listOf(5L), page.subforums.map { it.id })

        assertEquals(1, page.sticky.size)
        val rules = page.sticky[0]
        assertTrue(rules.sticky)
        assertTrue(rules.locked)
        assertEquals(50L, rules.id)

        assertEquals(listOf(100L, 101L), page.threads.map { it.id })
        val first = page.threads[0]
        assertEquals("Прва тема", first.title)
        assertEquals("Анкета", first.prefix)
        assertEquals("https://forum.kajgana.com/threads/prva-tema.100/", first.url)
        assertTrue(first.unread)
        assertEquals(1046, first.lastPage)
        assertEquals(15_700, first.replies)
        assertEquals("primer_korisnik", first.author)
        assertEquals("drug_korisnik", first.lastPoster)
        assertEquals(1795770000L, first.lastPostAt)

        val second = page.threads[1]
        assertFalse(second.unread)
        assertEquals(1, second.lastPage)
        assertEquals(12, second.replies)
    }

    @Test
    fun threadPageReadsPostsAuthorsAndReactions() {
        val url = "https://forum.kajgana.com/threads/prva-tema.100/page-1046"
        val page = ThreadParser.parse(fixture("thread_page.html"), url)
        assertEquals(100L, page.threadId)
        assertEquals("prva-tema", page.slug)
        assertEquals("Прва тема", page.title)
        assertEquals(2L, page.forumId)
        assertEquals("Општи разговори", page.forumTitle)
        assertEquals(1046, page.page.current)
        assertEquals(1046, page.page.last)
        assertFalse(page.canReply)
        assertEquals(2, page.posts.size)

        val post = page.posts[0]
        assertEquals(2000L, post.id)
        assertEquals(15676, post.number)
        assertEquals(1795766400L, post.timestamp)
        assertEquals("Денес во 9:00", post.timeText)
        assertEquals("primer_korisnik", post.author.name)
        assertEquals(10L, post.author.userId)
        assertEquals("https://forum.kajgana.com/data/avatars/m/0/10.jpg?1600000000", post.author.avatarUrl)
        assertEquals("Член", post.author.title)
        assertEquals("Член од" to "1 јан. 2010", post.author.details[0])
        assertEquals(4, post.author.details.size)

        val reactions = requireNotNull(post.reactions)
        assertEquals(listOf(1, 3), reactions.reactionIds)
        assertEquals(listOf("Like", "Haha"), reactions.names)
        assertEquals(5, reactions.total)
        assertEquals("https://forum.kajgana.com/posts/2000/reactions", reactions.listUrl)

        val second = page.posts[1]
        assertNull(second.author.avatarUrl)
        assertNull(second.reactions)
        assertEquals(15677, second.number)
        assertFalse(second.hasUnknown)
    }

    @Test
    fun postBodyBecomesBlocks() {
        val post = ThreadParser.parse(fixture("thread_page.html"), "https://forum.kajgana.com/threads/prva-tema.100/page-1046").posts[0]
        val body = post.body
        val quote = body[0] as Block.Quote
        assertEquals("drug_korisnik", quote.author)
        assertEquals(1999L, quote.postId)
        assertEquals("Ова е цитиран текст.", (quote.blocks.single() as Block.Paragraph).runs.joinToString("") { it.text })

        val text = body[1] as Block.Paragraph
        val plain = text.runs.joinToString("") { it.text }
        assertTrue(plain, plain.startsWith("Здраво сите, ова е пример :)\nВтор ред со надворешна врска и внатрешна."))
        assertTrue(text.runs.any { it.bold && it.text == "сите" })
        assertTrue(text.runs.any { it.italic && it.text == "пример" })
        assertTrue(text.runs.any { it.href == "https://example.com/strana" && it.text == "надворешна врска" })

        val image = body[2] as Block.Image
        assertEquals("https://example.com/slika.jpg", image.url)

        val spoiler = body[3] as Block.Spoiler
        assertEquals("Крајот", spoiler.title)
        assertEquals("Тајниот текст.", (spoiler.blocks.single() as Block.Paragraph).runs.single().text)

        val embed = body[4] as Block.Embed
        assertEquals("youtube", embed.site)
        assertEquals("https://www.youtube.com/watch?v=abcdefghijk", embed.url)

        val list = body[5] as Block.ListBlock
        assertEquals(2, list.items.size)
        assertFalse(list.ordered)

        assertEquals(Block.Unknown("table"), body[6])
        assertTrue(post.hasUnknown)

        val attachment = body[7] as Block.Image
        assertEquals("https://forum.kajgana.com/data/attachments/0/77-thumb.jpg", attachment.url)
        assertEquals("https://forum.kajgana.com/attachments/foto-jpg.77/", attachment.link)
        assertEquals(8, body.size)
    }

    @Test
    fun unfurlsAndCodeBlocks() {
        val post = ThreadParser.parse(fixture("thread_page.html"), "https://forum.kajgana.com/threads/prva-tema.100/").posts[1]
        assertEquals("Кратко.", (post.body[0] as Block.Paragraph).runs.single().text)
        assertEquals(Block.Link("https://example.com/vest", "Наслов на вест"), post.body[1])
        assertEquals(Block.Code("kod();"), post.body[2])
    }

    @Test
    fun linkedImagesKeepTheirLinkAndEmptyMarkupGivesNothing() {
        val html = "<div class=\"bbWrapper\"><a href=\"https://example.com/full.jpg\"><img class=\"bbImage\" src=\"https://example.com/small.jpg\"></a>  <br> </div>"
        val blocks = BbContent.parse(Jsoup.parse(html).selectFirst(".bbWrapper")!!)
        assertEquals(listOf(Block.Image("https://example.com/small.jpg", null, "https://example.com/full.jpg")), blocks)
    }

    @Test
    fun sessionFallsBackToTheAccountLink() {
        val html = "<html><body><a class=\"p-navgroup-link p-navgroup-link--user\" href=\"/account/\"><span class=\"p-navgroup-linkText\">nekoj</span></a></body></html>"
        val s = SessionParser.parse(html, "https://forum.kajgana.com/")
        assertTrue(s.loggedIn)
        assertEquals("nekoj", s.username)
    }

    @Test
    fun countsInTheForumsFormats() {
        assertEquals(1234, parseCount("1,234"))
        assertEquals(12345, parseCount("12.345"))
        assertEquals(1200, parseCount("1.2K"))
        assertEquals(15700, parseCount("15,7K"))
        assertEquals(2_000_000, parseCount("2M"))
        assertEquals(7, parseCount("7"))
        assertNull(parseCount(""))
        assertNull(parseCount("—"))
    }
}
