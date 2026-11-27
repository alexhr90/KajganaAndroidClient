package com.alexhr90.muabet.domain.parse

import com.alexhr90.muabet.domain.model.ForumPage
import com.alexhr90.muabet.domain.model.ThreadSummary
import com.alexhr90.muabet.domain.url.ForumUrls
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * A forum's thread list. XenForo 2 renders threads as `.structItem--thread` inside
 * `.structItemContainer`; pinned ones sit in `.structItemContainer-group--sticky`.
 */
object ForumParser {
    fun parse(html: String, url: String): ForumPage = parse(parseDocument(html, url), url)

    fun parse(doc: Document, url: String): ForumPage {
        val forumId = ForumUrls.slugAndId(url, "forums")?.second
            ?: ForumUrls.slugAndId(doc.selectFirst("link[rel=canonical]")?.attr("href"), "forums")?.second
            ?: throw ParseException("Not a forum URL: $url")
        val title = pageTitle(doc)
        val subforums = doc.select(".block--nodes .node, .node-list .node").mapNotNull { NodeParser.parse(it) }.distinctBy { it.id }
        val items = doc.select(".structItem--thread")
        if (items.isEmpty() && doc.selectFirst(".structItemContainer, .block-body--emptyMessage, .blockMessage") == null) {
            throw ParseException("No thread list on the forum page")
        }
        val sticky = mutableListOf<ThreadSummary>()
        val normal = mutableListOf<ThreadSummary>()
        items.forEach { el ->
            val t = ThreadRowParser.parse(el) ?: return@forEach
            if (t.sticky) sticky += t else normal += t
        }
        return ForumPage(
            forumId = forumId,
            title = title,
            subforums = subforums,
            sticky = sticky,
            threads = normal,
            page = parsePageInfo(doc),
            session = SessionParser.parse(doc),
        )
    }
}

/** The page's own heading without its prefix labels. */
internal fun pageTitle(doc: Document): String {
    val h1 = doc.selectFirst("h1.p-title-value") ?: return doc.title().substringBefore(" | ").squash()
    val copy = h1.clone()
    copy.select(".label, .labelLink, .label-append").remove()
    return copy.text().squash().ifEmpty { h1.text().squash() }
}

/** One `.structItem--thread` row. Also used for "New posts" and "Watched" lists (milestone 2). */
object ThreadRowParser {
    fun parse(el: Element): ThreadSummary? {
        val titleLink = el.select(".structItem-title a[href*=/threads/]").lastOrNull()
            ?: return null
        val href = titleLink.attr("href")
        val (slug, id) = ForumUrls.slugAndId(href, "threads")
            ?: Regex("js-threadListItem-(\\d+)").find(el.className())?.groupValues?.get(1)?.toLongOrNull()?.let { "" to it }
            ?: return null
        // The title link may point at /unread; the summary keeps the thread's own address.
        val url = ForumUrls.thread(slug, id)
        val prefix = el.selectFirst(".structItem-title .label")?.text()?.squash()?.takeIf { it.isNotEmpty() }
        val inStickyGroup = el.parents().any { it.hasClass("structItemContainer-group--sticky") }
        val statuses = el.select(".structItem-status").flatMap { it.classNames() }.toSet()
        val author = el.attr("data-author").takeIf { it.isNotBlank() }
            ?: el.selectFirst(".structItem-minor .username")?.text()?.squash()
        val started = el.selectFirst(".structItem-startDate time")?.epochSeconds()
        val meta = el.selectFirst(".structItem-cell--meta")
        val replies = meta?.selectFirst("dl dd")?.text()?.let(::parseCount)
        val latestCell = el.selectFirst(".structItem-cell--latest")
        val lastAt = latestCell?.selectFirst("time")?.epochSeconds()
        val lastPoster = latestCell?.selectFirst(".username")?.text()?.squash()
        val lastPage = el.select(".structItem-pageJump a").mapNotNull { a ->
            ForumUrls.pageOf(a.attr("href")) ?: a.text().trim().toIntOrNull()
        }.maxOrNull() ?: 1
        return ThreadSummary(
            id = id,
            slug = slug,
            title = titleLink.text().squash(),
            url = url,
            prefix = prefix,
            author = author,
            startedAt = started,
            replies = replies,
            lastPostAt = lastAt,
            lastPoster = lastPoster,
            unread = el.hasClass("is-unread"),
            sticky = inStickyGroup || "structItem-status--sticky" in statuses,
            locked = "structItem-status--locked" in statuses,
            lastPage = lastPage,
        )
    }
}
