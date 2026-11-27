package com.alexhr90.muabet.domain

import com.alexhr90.muabet.domain.model.Block
import com.alexhr90.muabet.domain.model.EmbedKind
import com.alexhr90.muabet.domain.model.FormResult
import com.alexhr90.muabet.domain.parse.BbCode
import com.alexhr90.muabet.domain.parse.BbContent
import com.alexhr90.muabet.domain.parse.ConversationParser
import com.alexhr90.muabet.domain.parse.MemberParser
import com.alexhr90.muabet.domain.parse.ThreadParser
import com.alexhr90.muabet.domain.parse.XfJson
import com.alexhr90.muabet.domain.url.ForumUrls
import com.alexhr90.muabet.domain.url.Route
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Embeds, tables, post actions, direct messages, profiles and form answers (v1.1). */
class NativeTest {
    private fun blocks(html: String): List<Block> =
        BbContent.parse(Jsoup.parse("<div class=\"bbWrapper\">$html</div>", ForumUrls.BASE).selectFirst(".bbWrapper")!!)

    private fun embed(html: String): Block.Embed = blocks(html).single() as Block.Embed

    @Test
    fun youtubeFromEveryMarkup() {
        val lazy = embed("<span data-s9e-mediaembed=\"youtube\" style=\"display:inline-block\"><span><iframe allowfullscreen=\"\" loading=\"lazy\" data-s9e-mediaembed-src=\"https://www.youtube.com/embed/abcdefghijk?start=30\" src=\"\"></iframe></span></span>")
        assertEquals(EmbedKind.YouTube, lazy.kind)
        assertEquals("abcdefghijk", lazy.mediaId)
        assertTrue(lazy.frameUrl!!.contains("start=30"))
        assertEquals("https://i.ytimg.com/vi/abcdefghijk/hqdefault.jpg", lazy.thumbnail)

        val keyed = embed("<div class=\"bbMediaWrapper\" data-media-site-id=\"youtube\" data-media-key=\"abcdefghijk:t=12\"><div class=\"bbMediaWrapper-inner\"><iframe src=\"\"></iframe></div></div>")
        assertEquals("https://www.youtube.com/watch?v=abcdefghijk&t=12", keyed.url)

        val thumbOnly = embed("<div class=\"bbMediaWrapper\"><a href=\"https://youtu.be/abcdefghijk\"><img src=\"https://i.ytimg.com/vi/abcdefghijk/hqdefault.jpg\"></a></div>")
        assertEquals("abcdefghijk", thumbOnly.mediaId)
    }

    @Test
    fun otherSites() {
        assertEquals(EmbedKind.Video, embed("<iframe src=\"https://player.vimeo.com/video/12345\"></iframe>").kind)
        val spotify = embed("<div class=\"bbMediaWrapper\" data-media-site-id=\"spotify\" data-media-key=\"track:4uLU6hMCjMI75M1A2tKUQC\"></div>")
        assertEquals(EmbedKind.Audio, spotify.kind)
        assertEquals("https://open.spotify.com/embed/track/4uLU6hMCjMI75M1A2tKUQC", spotify.frameUrl)
        val tweet = embed("<div class=\"bbOembed\" data-media-site-id=\"twitter\" data-media-key=\"1234567890\"><blockquote class=\"twitter-tweet\"><a href=\"https://twitter.com/x/status/1234567890\">x</a></blockquote></div>")
        assertEquals(EmbedKind.Social, tweet.kind)
        assertEquals("https://x.com/i/status/1234567890", tweet.url)
        val insta = embed("<span data-s9e-mediaembed=\"instagram\"><iframe src=\"https://s9e.github.io/iframe/2/instagram.min.html#Cabc123\"></iframe><a href=\"https://www.instagram.com/p/Cabc123/\">post</a></span>")
        assertEquals("instagram", insta.site)
        val mp4 = embed("<video controls src=\"https://example.com/clip.mp4\"></video>")
        assertEquals(EmbedKind.VideoFile, mp4.kind)
        val unknownPlayer = embed("<iframe src=\"https://player.example.org/v/9\"></iframe>")
        assertEquals("https://player.example.org/v/9", unknownPlayer.frameUrl)
    }

    @Test
    fun boxesFilesAndUnknownKeepTheirMarkup() {
        val box = blocks("<div class=\"bbCodeBlock bbCodeBlock--hide\"><div class=\"bbCodeBlock-title\">Скриено</div><div class=\"bbCodeBlock-content\">текст</div></div>").single() as Block.Box
        assertEquals("Скриено", box.title)
        val unfurl = blocks("<div class=\"bbCodeBlock bbCodeBlock--unfurl\" data-url=\"https://example.com/a\"><div class=\"contentRow-figure\"><img src=\"https://example.com/i.jpg\"></div><h3 class=\"contentRow-header\"><a href=\"https://example.com/a\">Наслов</a></h3><div class=\"contentRow-snippet\">Опис</div></div>").single() as Block.Link
        assertEquals("Опис", unfurl.description)
        assertEquals("https://example.com/i.jpg", unfurl.image)
        val ytUnfurl = blocks("<div class=\"bbCodeBlock bbCodeBlock--unfurl\" data-url=\"https://www.youtube.com/watch?v=abcdefghijk\"></div>").single()
        assertTrue(ytUnfurl is Block.Embed)
        val unknown = blocks("<iframe></iframe>").single() as Block.Unknown
        assertNotNull(unknown.html)
    }

    @Test
    fun postActionsAndAttachments() {
        val html = """
            <article class="message message--post" data-content="post-77" data-author="a">
              <div class="message-body"><div class="bbWrapper">x</div></div>
              <div class="message-attribution-opposite"><a class="bookmarkLink is-bookmarked" href="/posts/77/bookmark"></a></div>
              <div class="message-actionBar actionBar">
                <a href="/posts/77/react?reaction_id=2" class="reaction actionBar-action actionBar-action--reaction has-reaction" data-reaction-id="2">Love</a>
                <a href="/threads/t.1/reply?quote=77" class="actionBar-action actionBar-action--reply" data-xf-click="quote">Reply</a>
                <a href="/posts/77/edit" class="actionBar-action actionBar-action--edit">Edit</a>
                <a href="/posts/77/report" class="actionBar-action actionBar-action--report">Report</a>
              </div>
              <div class="message-attachments"><ul class="attachmentList">
                <li><a href="/attachments/doc-pdf.8/"><span class="file-name">doc.pdf</span><span class="file-meta">12 KB</span></a></li>
                <li><a href="/attachments/clip-mp4.9/"><video><source src="/data/video/9.mp4"></video><span class="file-name">clip.mp4</span></a></li>
              </ul></div>
            </article>
        """
        val post = ThreadParser.parsePost(Jsoup.parse(html, ForumUrls.BASE).selectFirst("article")!!)!!
        assertEquals(77L, post.id)
        assertTrue(post.can.react && post.can.quote && post.can.edit && post.can.report && post.can.bookmarked)
        assertEquals(2, post.can.myReactionId)
        assertTrue(!post.can.delete)
        val file = post.body[1] as Block.File
        assertEquals("doc.pdf", file.name)
        assertEquals("12 KB", file.meta)
        assertEquals(EmbedKind.VideoFile, (post.body[2] as Block.Embed).kind)
    }

    @Test
    fun directMessages() {
        val list = ConversationParser.list(fixture("conversations.html"), "https://forum.kajgana.com/direct-messages/")
        assertEquals(2, list.conversations.size)
        val first = list.conversations[0]
        assertEquals(55L, first.id)
        assertEquals(listOf("prv", "vtor"), first.participants)
        assertEquals(3, first.replies)
        assertEquals("vtor", first.lastSender)
        assertTrue(first.unread)
        assertNotNull(list.nextUrl)

        val page = ConversationParser.page(fixture("conversation.html"), "https://forum.kajgana.com/direct-messages/pozdrav.55/")
        assertEquals(55L, page.id)
        assertEquals(listOf(901L, 902L), page.messages.map { it.id })
        assertTrue(page.canReply)
        assertEquals(Route.Conversation(55, "pozdrav"), ForumUrls.route("https://forum.kajgana.com/direct-messages/pozdrav.55/"))
        assertEquals(Route.Conversations, ForumUrls.route("https://forum.kajgana.com/direct-messages/"))
        assertEquals(Route.Bookmarks, ForumUrls.route("https://forum.kajgana.com/account/bookmarks"))
    }

    @Test
    fun memberProfile() {
        val m = MemberParser.parse(fixture("member.html"), "https://forum.kajgana.com/members/primer.10/")
        assertEquals(10L, m.id)
        assertEquals("primer", m.name)
        assertEquals("https://forum.kajgana.com/data/avatars/l/0/10.jpg", m.avatarUrl)
        assertEquals(listOf("Пораки" to "1.234", "Реакции" to "567"), m.stats)
        assertEquals(2, m.blurb.size)
        assertTrue(m.canMessage)
    }

    @Test
    fun formAnswers() {
        val ok = XfJson.parse("""{"status":"ok","redirect":"/threads/t.1/post-5","message":"Done","attachment":{"attachment_id":42}}""") as FormResult.Ok
        assertEquals("https://forum.kajgana.com/threads/t.1/post-5", ok.redirect)
        assertEquals("42", ok.data["attachment.attachment_id"])
        val refused = XfJson.parse("""{"status":"error","errors":["Please enter a valid message."]}""") as FormResult.Refused
        assertEquals("Please enter a valid message.", refused.errors.single())
        assertEquals(FormResult.TokenExpired, XfJson.parse("""{"status":"error","errors":["Security error occurred. Please press back, refresh the page, and try again."]}"""))
        val quote = XfJson.parse("""{"quote":"[QUOTE=\"a, post: 1\"]x[/QUOTE]\n","status":"ok"}""") as FormResult.Ok
        assertEquals("[QUOTE=\"a, post: 1\"]x[/QUOTE]\n", BbCode.quoteFromJson(quote.data))
        assertTrue(XfJson.parse("<html>oops</html>") is FormResult.Refused)
        val reactions = XfJson.reactions("""<div><a href="/posts/1/react?reaction_id=1" data-reaction-id="1"><img title="Like" src="/r/like.png"></a><a data-reaction-id="2" title="Love"></a></div>""")
        assertEquals(listOf(1, 2), reactions.map { it.id })
        assertEquals("Love", reactions[1].name)
    }

    @Test
    fun editFormKeepsItsOtherFields() {
        val html = "<form action=\"/posts/5/edit\"><input name=\"title\" value=\"Наслов\"><select name=\"prefix_id\"><option value=\"0\">-</option><option value=\"3\" selected>П</option></select>" +
            "<input type=\"checkbox\" name=\"watch\" value=\"1\" checked><input type=\"checkbox\" name=\"sticky\" value=\"1\"><input type=\"hidden\" name=\"attachment_hash\" value=\"abc\">" +
            "<input type=\"hidden\" name=\"_xfToken\" value=\"t\"><textarea name=\"message_html\">&lt;p&gt;x&lt;/p&gt;</textarea></form>"
        val fields = com.alexhr90.muabet.domain.parse.FormFields.collect(html, "form[action*=/edit]")
        assertEquals(listOf("title" to "Наслов", "prefix_id" to "3", "watch" to "1", "attachment_hash" to "abc"), fields)
        assertEquals("abc", com.alexhr90.muabet.domain.parse.FormFields.attachmentHash(fields))
        assertEquals("x", BbCode.fromEditForm(html))
    }

    @Test
    fun editorHtmlBackToBbCode() {
        val bb = BbCode.fromEditorHtml("<p>Здраво <strong>сите</strong> <a href=\"https://example.com\">врска</a></p><blockquote data-quote=\"a\" data-source=\"post: 5\"><p>цитат</p></blockquote><p><img data-attachment=\"full:9\" src=\"x\"></p>")
        assertEquals("Здраво [B]сите[/B] [URL=https://example.com]врска[/URL]\n[QUOTE=\"a, post: 5\"]цитат[/QUOTE]\n[ATTACH=full]9[/ATTACH]", bb)
        assertEquals("[B]x[/B]", BbCode.fromEditForm("<form><textarea name=\"message\">[B]x[/B]</textarea></form>"))
        assertNull(BbCode.fromEditForm("<form></form>"))
    }
}
