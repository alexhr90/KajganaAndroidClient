package com.alexhr90.muabet.domain.parse

import com.alexhr90.muabet.domain.model.Block
import com.alexhr90.muabet.domain.model.Post
import com.alexhr90.muabet.domain.model.PostAuthor
import com.alexhr90.muabet.domain.model.ReactionSummary
import com.alexhr90.muabet.domain.model.ThreadPage
import com.alexhr90.muabet.domain.url.ForumUrls
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * One page of a thread. XenForo 2 renders each post as `article.message--post` with the author
 * in `.message-user`, the time and number in `.message-attribution`, the body in `.bbWrapper`
 * and reactions in `.reactionsBar` (SPEC §3).
 */
object ThreadParser {
    fun parse(html: String, url: String): ThreadPage = parse(parseDocument(html, url), url)

    fun parse(doc: Document, url: String): ThreadPage {
        val (slug, threadId) = ForumUrls.slugAndId(doc.selectFirst("link[rel=canonical]")?.attr("href"), "threads")
            ?: ForumUrls.slugAndId(url, "threads")
            ?: throw ParseException("Not a thread URL: $url")
        val articles = doc.select("article.message--post")
        if (articles.isEmpty()) throw ParseException("No posts on the thread page")
        val crumbs = doc.select(".p-breadcrumbs a[href*=/forums/]")
        val forumLink = crumbs.lastOrNull()
        return ThreadPage(
            threadId = threadId,
            slug = slug,
            title = pageTitle(doc),
            forumId = ForumUrls.slugAndId(forumLink?.attr("href"), "forums")?.second,
            forumTitle = forumLink?.text()?.squash()?.takeIf { it.isNotEmpty() },
            posts = articles.mapNotNull { parsePost(it, doc.location()) },
            page = parsePageInfo(doc),
            session = SessionParser.parse(doc),
            canReply = doc.selectFirst("form.js-quickReply, form[action*=add-reply]") != null,
        )
    }

    fun parsePost(el: Element, base: String = ForumUrls.BASE): Post? {
        val id = el.attr("data-content").removePrefix("post-").toLongOrNull()
            ?: el.id().removePrefix("js-post-").toLongOrNull()
            ?: el.selectFirst(".u-anchorTarget[id^=post-]")?.id()?.removePrefix("post-")?.toLongOrNull()
            ?: return null
        val attribution = el.selectFirst(".message-attribution")
        val time = attribution?.selectFirst(".message-attribution-main time") ?: attribution?.selectFirst("time")
        val number = attribution?.select(".message-attribution-opposite a")
            ?.firstNotNullOfOrNull { a -> Regex("^#\\s*([0-9.,]+)$").find(a.text().trim())?.groupValues?.get(1) }
            ?.filter { it.isDigit() }?.toIntOrNull()
        val wrapper = el.selectFirst(".message-body .bbWrapper") ?: el.selectFirst(".bbWrapper")
        val body: MutableList<Block> = if (wrapper != null) BbContent.parse(wrapper, base).toMutableList() else mutableListOf(Block.Unknown("body"))
        body += attachments(el, base)
        return Post(
            id = id,
            number = number,
            author = parseAuthor(el, base),
            timestamp = time?.epochSeconds(),
            timeText = time?.text()?.squash()?.takeIf { it.isNotEmpty() },
            body = body,
            reactions = parseReactions(el, base),
        )
    }

    private fun parseAuthor(el: Element, base: String): PostAuthor {
        val user = el.selectFirst(".message-user")
        val nameLink = user?.selectFirst(".message-name .username") ?: user?.selectFirst(".message-name a")
        val name = el.attr("data-author").takeIf { it.isNotBlank() }
            ?: nameLink?.text()?.squash()?.takeIf { it.isNotEmpty() }
            ?: user?.selectFirst(".message-name")?.text()?.squash()
            ?: ""
        val userId = (nameLink?.attr("data-user-id") ?: user?.selectFirst("[data-user-id]")?.attr("data-user-id"))?.toLongOrNull()
        val avatar = user?.selectFirst(".message-avatar img")?.let { img ->
            ForumUrls.absolute(img.attr("src").ifBlank { img.attr("data-src") }, base)
        }
        val title = user?.selectFirst(".message-userTitle, .userTitle")?.text()?.squash()?.takeIf { it.isNotEmpty() }
        val details = user?.select(".message-userExtras dl")?.mapNotNull { dl ->
            val label = dl.selectFirst("dt")?.text()?.squash().orEmpty()
            val value = dl.selectFirst("dd")?.text()?.squash().orEmpty()
            if (label.isEmpty() && value.isEmpty()) null else label.trimEnd(':') to value
        }.orEmpty()
        return PostAuthor(name = name, userId = userId, avatarUrl = avatar, title = title, details = details)
    }

    private fun attachments(el: Element, base: String): List<Block> =
        el.select(".message-attachments .attachmentList li").mapNotNull { li ->
            val link = li.selectFirst("a.file-preview") ?: li.selectFirst("a[href]")
            val img = li.selectFirst("img")
            val full = ForumUrls.absolute(link?.attr("href"), base)
            val thumb = ForumUrls.absolute(img?.attr("src"), base)
            val url = thumb ?: full ?: return@mapNotNull null
            Block.Image(url = url, alt = li.selectFirst(".file-name")?.text()?.squash() ?: img?.attr("alt"), link = full)
        }

    private fun parseReactions(el: Element, base: String): ReactionSummary? {
        val bar = el.selectFirst(".reactionsBar") ?: return null
        val reactions = bar.select(".reactionSummary .reaction")
        val ids = reactions.mapNotNull { it.attr("data-reaction-id").toIntOrNull() }
        val names = reactions.mapNotNull { r ->
            r.selectFirst("img")?.let { it.attr("title").ifBlank { it.attr("alt") } }?.takeIf { it.isNotBlank() }
        }
        val link = bar.selectFirst("a.reactionsBar-link")
        if (ids.isEmpty() && link == null) return null
        val named = link?.select("bdi")?.size ?: 0
        val rest = link?.clone()?.apply { select("bdi").remove() }?.text()
        val others = rest?.let { Regex("([0-9][0-9.,]*)").find(it)?.groupValues?.get(1)?.filter(Char::isDigit)?.toIntOrNull() } ?: 0
        return ReactionSummary(
            reactionIds = ids,
            names = names,
            total = (named + others).coerceAtLeast(if (ids.isNotEmpty()) 1 else 0),
            listUrl = ForumUrls.absolute(link?.attr("href"), base),
        )
    }
}
