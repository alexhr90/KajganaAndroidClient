package com.alexhr90.muabet.domain.parse

import com.alexhr90.muabet.domain.model.Category
import com.alexhr90.muabet.domain.model.Forum
import com.alexhr90.muabet.domain.model.ForumHome
import com.alexhr90.muabet.domain.model.LatestThread
import com.alexhr90.muabet.domain.model.SubForum
import com.alexhr90.muabet.domain.url.ForumUrls
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * The forum list on the home page (and the subforum list on a forum page). XenForo 2 marks each
 * category as `.block--category` and each forum as `.node`, with `.node-title`, `.node-extra`
 * (the latest thread) and `.node-meta` (verified on the live forum, SPEC §3).
 */
object HomeParser {
    fun parse(html: String, url: String = ForumUrls.home): ForumHome = parse(parseDocument(html, url))

    fun parse(doc: Document): ForumHome {
        val session = SessionParser.parse(doc)
        val blocks = doc.select(".block--category")
        val categories = if (blocks.isNotEmpty()) {
            blocks.mapNotNull { block -> parseCategory(block) }
        } else {
            // A style without category blocks: one unnamed group of every top-level forum.
            val forums = doc.select(".node--depth2, .node--depth1").mapNotNull { NodeParser.parse(it) }
            if (forums.isEmpty()) emptyList() else listOf(Category(id = null, title = "", forums = forums))
        }
        if (categories.isEmpty() || categories.all { it.forums.isEmpty() }) {
            throw ParseException("No forums found on the home page")
        }
        return ForumHome(categories = categories, session = session)
    }

    private fun parseCategory(block: Element): Category? {
        val header = block.selectFirst(".block-header")
        val link = header?.selectFirst("a")
        val title = (link?.text() ?: header?.ownText() ?: "").squash()
        val id = ForumUrls.slugAndId(link?.attr("href"), "categories")?.second
            ?: Regex("block--category(\\d+)").find(block.className())?.groupValues?.get(1)?.toLongOrNull()
        // Only the category's direct forums; their subforums come with each node.
        val nodes = block.select(".block-body > .node, .block-body > .node-list > .node, .block-body .node.node--depth2")
            .distinct()
        val forums = nodes.mapNotNull { NodeParser.parse(it) }
        if (forums.isEmpty() && title.isEmpty()) return null
        return Category(id = id, title = title, forums = forums)
    }
}

/** One `.node` row of a forum list. */
internal object NodeParser {
    fun parse(node: Element): Forum? {
        val titleLink = node.selectFirst(".node-title a") ?: return null
        val href = titleLink.attr("href")
        val abs = ForumUrls.absolute(href) ?: return null
        val classes = node.classNames()
        val isLink = "node--link" in classes
        val (slug, id) = ForumUrls.slugAndId(href, "forums")
            ?: Regex("node--id(\\d+)").find(node.className())?.groupValues?.get(1)?.toLongOrNull()?.let { "" to it }
            ?: return null
        val description = node.selectFirst(".node-description")?.text()?.squash()?.takeIf { it.isNotEmpty() }
        return Forum(
            id = id,
            slug = slug,
            title = titleLink.text().squash(),
            description = description,
            unread = "node--unread" in classes,
            isLink = isLink,
            url = abs,
            latest = parseLatest(node),
            subforums = parseSubforums(node),
        )
    }

    private fun parseLatest(node: Element): LatestThread? {
        val extra = node.selectFirst(".node-extra") ?: return null
        val link = extra.selectFirst("a.node-extra-title") ?: extra.selectFirst(".node-extra-row a[href*=/threads/]") ?: return null
        val url = ForumUrls.absolute(link.attr("href")) ?: return null
        val title = (link.attr("title").takeIf { it.isNotBlank() } ?: link.text()).squash()
        val threadId = ForumUrls.slugAndId(link.attr("href"), "threads")?.second
        val author = extra.selectFirst(".node-extra-user .username, .node-extra-user a")?.text()?.squash()
        val time = extra.selectFirst("time")?.epochSeconds()
        return LatestThread(threadId = threadId, title = title, url = url, author = author, timestamp = time)
    }

    private fun parseSubforums(node: Element): List<SubForum> {
        val links = node.select(".node-subNodeFlatList a, .node-subNodeMenu a.subNodeLink, ol.subNodeMenu a.subNodeLink")
        return links.mapNotNull { a ->
            val (slug, id) = ForumUrls.slugAndId(a.attr("href"), "forums") ?: return@mapNotNull null
            SubForum(
                id = id,
                slug = slug,
                title = a.text().squash(),
                unread = a.hasClass("subNodeLink--unread"),
                url = ForumUrls.absolute(a.attr("href")) ?: return@mapNotNull null,
            )
        }.distinctBy { it.id }
    }
}
