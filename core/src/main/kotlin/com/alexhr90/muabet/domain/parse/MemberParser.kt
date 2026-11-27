package com.alexhr90.muabet.domain.parse

import com.alexhr90.muabet.domain.model.Block
import com.alexhr90.muabet.domain.model.MemberProfile
import com.alexhr90.muabet.domain.url.ForumUrls
import org.jsoup.nodes.Document

/** A member's page (`/members/<slug>.<id>/`): the `.memberHeader` block and its stats. */
object MemberParser {
    fun parse(html: String, url: String): MemberProfile = parse(parseDocument(html, url), url)

    fun parse(doc: Document, url: String): MemberProfile {
        val base = doc.location().ifBlank { ForumUrls.BASE }
        val (slug, id) = ForumUrls.slugAndId(doc.selectFirst("link[rel=canonical]")?.attr("href"), "members")
            ?: ForumUrls.slugAndId(url, "members")
            ?: throw ParseException("Not a member URL: $url")
        val header = doc.selectFirst(".memberHeader") ?: throw ParseException("No member header on the page")
        val name = header.selectFirst(".memberHeader-name .username, .memberHeader-name")?.text()?.squash()?.takeIf { it.isNotEmpty() }
            ?: pageTitle(doc)
        val avatar = header.selectFirst(".memberHeader-avatar img, .avatar img")?.let { img ->
            listOf(img.attr("src"), img.attr("data-src")).firstNotNullOfOrNull { ForumUrls.absolute(it, base) }
        }
        val title = header.selectFirst(".userTitle, .memberHeader-banners")?.text()?.squash()?.takeIf { it.isNotEmpty() }
        val blurb = header.select(".memberHeader-blurb").map { it.text().squash() }.filter { it.isNotEmpty() }
        val stats = header.select(".memberHeader-stats dl.pairs, .memberHeader-stats dl").mapNotNull { dl ->
            val label = dl.selectFirst("dt")?.text()?.squash().orEmpty()
            val value = dl.selectFirst("dd")?.text()?.squash().orEmpty()
            if (label.isEmpty() || value.isEmpty()) null else label.trimEnd(':') to value
        }.distinct()
        val aboutWrapper = doc.selectFirst("#about .bbWrapper, .memberAbout .bbWrapper, [data-href*=/about] .bbWrapper")
        val about: List<Block> = aboutWrapper?.let { BbContent.parse(it, base) }.orEmpty()
        return MemberProfile(
            id = id,
            slug = slug,
            name = name,
            avatarUrl = avatar,
            title = title,
            blurb = blurb,
            stats = stats,
            about = about,
            canMessage = doc.selectFirst("a[href*=/direct-messages/add], a[href*=/conversations/add]") != null,
            session = SessionParser.parse(doc),
        )
    }
}
