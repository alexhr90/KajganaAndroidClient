package com.alexhr90.muabet.domain.parse

import com.alexhr90.muabet.domain.model.ConversationList
import com.alexhr90.muabet.domain.model.ConversationPage
import com.alexhr90.muabet.domain.model.ConversationSummary
import com.alexhr90.muabet.domain.url.ForumUrls
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Direct messages (XenForo 2.3 renamed conversations): the list at `/direct-messages/` renders
 * each one as a `.structItem` like a thread row, and a conversation's messages are
 * `article.message` like posts, so [ThreadParser.parsePost] reads them.
 */
object ConversationParser {

    fun list(html: String, url: String): ConversationList = list(parseDocument(html, url))

    fun list(doc: Document): ConversationList {
        val base = doc.location().ifBlank { ForumUrls.BASE }
        val rows = doc.select(".structItem").filter { it.selectFirst(".structItem-title a[href*=/direct-messages/], .structItem-title a[href*=/conversations/]") != null }
        if (rows.isEmpty() && doc.selectFirst(".blockMessage, .block-body--emptyMessage, .block-row--empty") == null) {
            throw ParseException("No direct message list on the page")
        }
        return ConversationList(
            conversations = rows.mapNotNull { row(it, base) }.distinctBy { it.id },
            page = parsePageInfo(doc),
            session = SessionParser.parse(doc),
            nextUrl = parseNextUrl(doc),
        )
    }

    private fun row(el: Element, base: String): ConversationSummary? {
        val a = el.selectFirst(".structItem-title a[href*=/direct-messages/], .structItem-title a[href*=/conversations/]") ?: return null
        val url = ForumUrls.absolute(a.attr("href"), base) ?: return null
        val (slug, id) = slugAndId(url) ?: return null
        val parts = el.select(".structItem-parts .username, .structItem-parts a[href*=/members/]").map { it.text().squash() }
            .filter { it.isNotEmpty() }.distinct()
        val started = el.selectFirst(".structItem-minor .username, .structItem-parts .username")?.text()?.squash()?.takeIf { it.isNotEmpty() }
        val replies = el.select(".structItem-cell--meta dl.pairs dd, .structItem-cell--meta dd").firstOrNull()?.text()?.let(::parseCount)
        val latest = el.selectFirst(".structItem-cell--latest")
        return ConversationSummary(
            id = id,
            slug = slug,
            title = a.text().squash(),
            url = url,
            participants = parts.ifEmpty { listOfNotNull(started) },
            startedBy = started,
            replies = replies,
            lastMessageAt = (latest?.selectFirst("time") ?: el.selectFirst("time"))?.epochSeconds(),
            lastSender = latest?.selectFirst(".username")?.text()?.squash()?.takeIf { it.isNotEmpty() },
            unread = el.hasClass("is-unread"),
        )
    }

    fun page(html: String, url: String): ConversationPage = page(parseDocument(html, url), url)

    fun page(doc: Document, url: String): ConversationPage {
        val base = doc.location().ifBlank { ForumUrls.BASE }
        val (slug, id) = slugAndId(doc.selectFirst("link[rel=canonical]")?.attr("href"))
            ?: slugAndId(url)
            ?: throw ParseException("Not a direct message URL: $url")
        val articles = doc.select("article.message").filter { it.selectFirst(".bbWrapper") != null }
        if (articles.isEmpty()) throw ParseException("No messages on the page")
        val participants = doc.select(".p-body-sidebar .username, .block--conversationParticipants .username, .conversationParticipants .username")
            .map { it.text().squash() }.filter { it.isNotEmpty() }.distinct()
        return ConversationPage(
            id = id,
            slug = slug,
            title = pageTitle(doc),
            participants = participants,
            messages = articles.mapNotNull { ThreadParser.parsePost(it, base) },
            page = parsePageInfo(doc),
            session = SessionParser.parse(doc),
            canReply = doc.selectFirst("form[action*=add-reply], form.js-quickReply") != null,
        )
    }

    /** The `<slug>.<id>` of a direct message link (either URL scheme). */
    fun slugAndId(href: String?): Pair<String, Long>? =
        ForumUrls.slugAndId(href, "direct-messages") ?: ForumUrls.slugAndId(href, "conversations")
}
