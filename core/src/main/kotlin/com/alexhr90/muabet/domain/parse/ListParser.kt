package com.alexhr90.muabet.domain.parse

import com.alexhr90.muabet.domain.model.Alert
import com.alexhr90.muabet.domain.model.AlertList
import com.alexhr90.muabet.domain.model.SearchPage
import com.alexhr90.muabet.domain.model.SearchResult
import com.alexhr90.muabet.domain.model.ThreadList
import com.alexhr90.muabet.domain.url.ForumUrls
import com.alexhr90.muabet.domain.url.Route
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * The account lists (SPEC §3 `ListParser`): New posts (`/whats-new/posts/`), Watched threads
 * (`/watched/threads`), alerts (`/account/alerts`) and search results. XenForo 2 renders the
 * thread lists as `.structItem--thread` like a forum, alerts and search hits as `.block-row`
 * with a `.contentRow`.
 */
object ListParser {

    fun threads(html: String, url: String): ThreadList = threads(parseDocument(html, url))

    fun threads(doc: Document): ThreadList {
        val items = doc.select(".structItem--thread")
        if (items.isEmpty() && !hasEmptyMessage(doc)) throw ParseException("No thread list on the page")
        return ThreadList(
            threads = items.mapNotNull { ThreadRowParser.parse(it) }.distinctBy { it.id },
            page = parsePageInfo(doc),
            session = SessionParser.parse(doc),
            nextUrl = parseNextUrl(doc),
        )
    }

    fun alerts(html: String, url: String): AlertList = alerts(parseDocument(html, url))

    fun alerts(doc: Document): AlertList {
        val rows = doc.select("[data-alert-id]")
        if (rows.isEmpty() && !hasEmptyMessage(doc)) throw ParseException("No alert list on the page")
        val base = doc.location().ifBlank { ForumUrls.BASE }
        return AlertList(
            alerts = rows.mapNotNull { parseAlert(it, base) }.distinctBy { it.id },
            page = parsePageInfo(doc),
            session = SessionParser.parse(doc),
            nextUrl = parseNextUrl(doc),
        )
    }

    fun search(html: String, url: String): SearchPage = search(parseDocument(html, url))

    fun search(doc: Document): SearchPage {
        val rows = doc.select(".block-body > li.block-row, ol.block-body > li, .block-row:has(.contentRow-title)")
            .filter { it.selectFirst(".contentRow-title a[href]") != null }
            .distinct()
        if (rows.isEmpty() && !hasEmptyMessage(doc)) throw ParseException("No search results on the page")
        val base = doc.location().ifBlank { ForumUrls.BASE }
        return SearchPage(
            results = rows.mapNotNull { parseResult(it, base) }.distinctBy { it.url },
            page = parsePageInfo(doc),
            session = SessionParser.parse(doc),
            nextUrl = parseNextUrl(doc),
        )
    }

    private fun hasEmptyMessage(doc: Document): Boolean =
        doc.selectFirst(".blockMessage, .block-body--emptyMessage, .block-row--empty") != null

    private fun parseAlert(row: Element, base: String): Alert? {
        val id = row.attr("data-alert-id").toLongOrNull() ?: return null
        val main = row.selectFirst(".contentRow-main") ?: row
        val textEl = main.clone().apply { select(".contentRow-minor, .contentRow-extra, script").remove() }
        val text = textEl.text().squash()
        if (text.isEmpty()) return null
        // The link to the content; member links are only the actor.
        val link = main.selectFirst("a.fauxBlockLink-blockLink")
            ?: main.select("a[href]").firstOrNull { a ->
                val r = ForumUrls.absolute(a.attr("href"), base)?.let(ForumUrls::route)
                r != null && r !is Route.Member && !a.hasClass("username")
            }
        val author = main.selectFirst(".username")?.text()?.squash()?.takeIf { it.isNotEmpty() }
        val avatar = row.selectFirst(".contentRow-figure img")?.let { img ->
            ForumUrls.absolute(img.attr("src").ifBlank { img.attr("data-src") }, base)
        }
        return Alert(
            id = id,
            text = text,
            author = author,
            avatarUrl = avatar,
            url = ForumUrls.absolute(link?.attr("href"), base),
            unread = row.hasClass("is-unread") || row.hasClass("is-recent"),
            timestamp = row.selectFirst("time")?.epochSeconds(),
        )
    }

    private fun parseResult(row: Element, base: String): SearchResult? {
        val a = row.selectFirst(".contentRow-title a[href]") ?: return null
        val url = ForumUrls.absolute(a.attr("href"), base) ?: return null
        val title = a.clone().apply { select(".label, .labelLink").remove() }.text().squash().ifEmpty { a.text().squash() }
        val minor = row.selectFirst(".contentRow-minor")
        val forumLink = minor?.select("a[href*=/forums/]")?.lastOrNull()
        val route = ForumUrls.route(url)
        return SearchResult(
            title = title,
            url = url,
            snippet = row.selectFirst(".contentRow-snippet")?.text()?.squash()?.takeIf { it.isNotEmpty() },
            author = row.attr("data-author").takeIf { it.isNotBlank() } ?: minor?.selectFirst(".username")?.text()?.squash(),
            timestamp = row.selectFirst("time")?.epochSeconds(),
            forumTitle = forumLink?.text()?.squash()?.takeIf { it.isNotEmpty() },
            threadId = (route as? Route.Thread)?.id ?: ForumUrls.slugAndId(url, "threads")?.second,
            postId = (route as? Route.Post)?.id,
        )
    }
}
