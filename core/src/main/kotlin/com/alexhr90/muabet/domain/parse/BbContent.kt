package com.alexhr90.muabet.domain.parse

import com.alexhr90.muabet.domain.model.Block
import com.alexhr90.muabet.domain.model.Run
import com.alexhr90.muabet.domain.model.TableCell
import com.alexhr90.muabet.domain.url.ForumUrls
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/**
 * Turns a post's rendered BB code (`.bbWrapper`) into [Block]s: paragraphs of styled runs,
 * quotes, images, link previews, embeds, spoilers, code, lists, tables and other boxes. Anything
 * else becomes [Block.Unknown] carrying its HTML, which the app renders in place.
 */
object BbContent {
    fun parse(wrapper: Element, base: String = ForumUrls.BASE): List<Block> {
        val b = Builder(base)
        wrapper.childNodes().forEach { b.walk(it, Style()) }
        b.flush()
        return b.blocks
    }

    private data class Style(
        val bold: Boolean = false,
        val italic: Boolean = false,
        val underline: Boolean = false,
        val strike: Boolean = false,
        val href: String? = null,
    )

    private val blockTags = setOf("div", "p", "h1", "h2", "h3", "h4", "h5", "h6", "center", "section", "article", "hr")
    private val ignoredTags = setOf("script", "style", "noscript", "template", "button", "input", "form")

    private class Builder(val base: String) {
        val blocks = mutableListOf<Block>()
        private val runs = mutableListOf<Run>()

        fun flush() {
            if (runs.isEmpty()) return
            val merged = mutableListOf<Run>()
            for (r in runs) {
                val last = merged.lastOrNull()
                if (last != null && last.sameStyle(r)) merged[merged.size - 1] = last.copy(text = last.text + r.text) else merged += r
            }
            runs.clear()
            // Trim the paragraph's outer whitespace, fold 3+ line breaks to one blank line.
            val cleaned = merged.map { it.copy(text = it.text.replace(Regex("[ \\t]*\\n[ \\t]*"), "\n").replace(Regex("\\n{3,}"), "\n\n")) }
                .toMutableList()
            while (cleaned.isNotEmpty() && cleaned.first().text.isBlank()) cleaned.removeAt(0)
            while (cleaned.isNotEmpty() && cleaned.last().text.isBlank()) cleaned.removeAt(cleaned.size - 1)
            if (cleaned.isEmpty()) return
            cleaned[0] = cleaned[0].copy(text = cleaned[0].text.trimStart())
            cleaned[cleaned.size - 1] = cleaned.last().copy(text = cleaned.last().text.trimEnd())
            blocks += Block.Paragraph(cleaned)
        }

        private fun text(t: String, s: Style) {
            if (t.isEmpty()) return
            runs += Run(t, bold = s.bold, italic = s.italic, underline = s.underline, strike = s.strike, href = s.href)
        }

        private fun add(block: Block) {
            flush()
            blocks += block
        }

        private fun children(el: Element): List<Block> = parse(el, base)

        fun walk(node: Node, s: Style) {
            when (node) {
                is TextNode -> text(node.wholeText.replace(Regex("[\\s\\u00a0]+"), " "), s)
                is Element -> element(node, s)
            }
        }

        private fun element(el: Element, s: Style) {
            val tag = el.normalName()
            when {
                tag in ignoredTags -> Unit
                tag == "br" -> text("\n", s)
                tag == "img" -> image(el, s.href)
                tag == "blockquote" || el.hasClass("bbCodeBlock--quote") -> quote(el)
                el.hasClass("bbCodeSpoiler") || el.hasClass("bbCodeBlock--spoiler") -> spoiler(el)
                el.hasClass("bbCodeInlineSpoiler") -> el.childNodes().forEach { walk(it, s) }
                el.hasClass("bbCodeBlock--code") || tag == "pre" -> code(el)
                el.hasClass("bbCodeBlock--unfurl") -> unfurl(el)
                isMedia(el) -> embed(el)
                tag == "audio" -> embed(el)
                el.hasClass("bbTable") -> el.selectFirst("table")?.let(::table) ?: add(Block.Unknown("table", el.outerHtml().cap()))
                tag == "table" -> table(el)
                el.hasClass("bbCodeBlock--attachment") || el.hasClass("file") && el.selectFirst("a[href*=/attachments/]") != null -> file(el)
                el.hasClass("bbCodeBlock") -> box(el)
                tag == "ul" || tag == "ol" -> list(el, ordered = tag == "ol")
                tag == "a" -> link(el, s)
                tag == "b" || tag == "strong" -> el.childNodes().forEach { walk(it, s.copy(bold = true)) }
                tag == "i" || tag == "em" -> el.childNodes().forEach { walk(it, s.copy(italic = true)) }
                tag == "u" || tag == "ins" -> el.childNodes().forEach { walk(it, s.copy(underline = true)) }
                tag == "s" || tag == "strike" || tag == "del" -> el.childNodes().forEach { walk(it, s.copy(strike = true)) }
                tag == "hr" -> flush()
                tag.length == 2 && tag[0] == 'h' && tag[1].isDigit() -> {
                    flush()
                    el.childNodes().forEach { walk(it, s.copy(bold = true)) }
                    flush()
                }
                tag in blockTags -> {
                    flush()
                    el.childNodes().forEach { walk(it, s) }
                    flush()
                }
                else -> el.childNodes().forEach { walk(it, s) }
            }
        }

        private fun link(el: Element, s: Style) {
            val href = ForumUrls.absolute(el.attr("href"), base)
            val onlyImage = el.select("img").isNotEmpty() && el.text().isBlank()
            if (onlyImage) {
                el.select("img").forEach { image(it, href) }
                return
            }
            el.childNodes().forEach { walk(it, s.copy(href = href ?: s.href)) }
        }

        private fun image(el: Element, link: String?) {
            // Smilies are part of the sentence: keep their text.
            if (el.hasClass("smilie") || el.hasClass("smilie--emoji")) {
                val alt = el.attr("alt").ifBlank { el.attr("title") }
                if (alt.isNotBlank()) text(alt, Style(href = link))
                return
            }
            // The forum's own copy first (lazy `data-src`, then `src`, which may be the image proxy);
            // `data-url` is the original address on another site, used only when nothing else is there.
            val src = listOf(el.attr("data-src"), el.attr("src"), el.attr("data-url"))
                .firstNotNullOfOrNull { ForumUrls.absolute(it, base) }
                ?.let(ForumUrls::preferHttps)
                ?: return
            add(Block.Image(url = src, alt = el.attr("alt").takeIf { it.isNotBlank() }, link = link?.takeIf { it != src }))
        }

        private fun quote(el: Element) {
            val author = el.attr("data-quote").takeIf { it.isNotBlank() }
                ?: el.selectFirst(".bbCodeBlock-title")?.text()?.squash()?.removeSuffix(":")
                    ?.replace(Regex("\\s+(said|рече|напиша|написа|schrieb|написал)$", RegexOption.IGNORE_CASE), "")
                    ?.takeIf { it.isNotBlank() }
            val postId = Regex("post:\\s*(\\d+)").find(el.attr("data-source"))?.groupValues?.get(1)?.toLongOrNull()
                ?: ForumUrls.absolute(el.selectFirst("a.bbCodeBlock-sourceJump")?.attr("href"), base)?.let { href ->
                    Regex("[?&]id=(\\d+)").find(href)?.groupValues?.get(1)?.toLongOrNull()
                }
            val content = el.selectFirst(".bbCodeBlock-expandContent") ?: el.selectFirst(".bbCodeBlock-content") ?: el
            val copy = content.clone()
            copy.select(".bbCodeBlock-title, .bbCodeBlock-expandLink").remove()
            add(Block.Quote(author = author, postId = postId, blocks = children(copy)))
        }

        private fun spoiler(el: Element) {
            val title = el.selectFirst(".bbCodeSpoiler-button-title")?.text()?.squash()?.takeIf { it.isNotEmpty() }
            val content = el.selectFirst(".bbCodeSpoiler-content .bbCodeBlock-content")
                ?: el.selectFirst(".bbCodeSpoiler-content")
                ?: el.selectFirst(".bbCodeBlock-content")
                ?: el
            val copy = content.clone()
            copy.select(".bbCodeSpoiler-button").remove()
            add(Block.Spoiler(title = title, blocks = children(copy)))
        }

        private fun code(el: Element) {
            val code = el.selectFirst("code") ?: el.selectFirst("pre") ?: el
            add(Block.Code(code.wholeText().trimEnd()))
        }

        private fun unfurl(el: Element) {
            val url = ForumUrls.absolute(el.attr("data-url").ifBlank { el.selectFirst("a[href]")?.attr("href").orEmpty() }, base)
            if (url == null) {
                add(Block.Unknown("unfurl", el.outerHtml().cap()))
                return
            }
            // A link to media is better played than previewed.
            MediaSites.fromLink(url)?.let {
                add(it)
                return
            }
            val title = el.selectFirst(".contentRow-header")?.text()?.squash()?.takeIf { it.isNotEmpty() }
            val description = el.selectFirst(".contentRow-snippet")?.text()?.squash()?.takeIf { it.isNotEmpty() }
            val image = el.selectFirst(".contentRow-figure img, img.bbCodeBlockUnfurl-image, img")?.let { img ->
                listOf(img.attr("data-src"), img.attr("src")).firstNotNullOfOrNull { ForumUrls.absolute(it, base) }?.let(ForumUrls::preferHttps)
            }
            add(Block.Link(url = url, title = title, description = description, image = image))
        }

        private fun isMedia(el: Element): Boolean {
            val tag = el.normalName()
            return el.hasClass("bbMediaWrapper") || el.hasClass("bbMediaJustifier") || el.hasClass("bbOembed") ||
                el.hasAttr("data-s9e-mediaembed") || el.hasAttr("data-media-site-id") ||
                tag == "iframe" || tag == "video" ||
                el.hasClass("bbCodeBlock--media") || el.hasClass("bbCodeBlock--oembed")
        }

        /** Every address an embed's markup carries, from the element and its descendants. */
        private fun candidates(el: Element): List<String> {
            val attrs = listOf("src", "data-src", "data-s9e-mediaembed-src", "data-url", "href", "data-href", "poster")
            val out = mutableListOf<String>()
            val all = listOf(el) + el.select("iframe, video, source, audio, a[href], [data-url], [data-s9e-mediaembed-src], [data-src], blockquote[cite]")
            for (e in all) {
                for (a in attrs) e.attr(a).takeIf { it.isNotBlank() && !it.startsWith("data:") && !it.startsWith("javascript:") }?.let { out += it }
                e.attr("cite").takeIf { it.isNotBlank() }?.let { out += it }
                // s9e puts the real player's address in a JSON-ish onload or in a style url().
                Regex("url\\(['\"]?([^'\")]+)").find(e.attr("style"))?.groupValues?.get(1)?.let { out += it }
            }
            el.attr("data-s9e-mediaembed-c2l-src").takeIf { it.isNotBlank() }?.let { out += it }
            return out.mapNotNull { ForumUrls.absolute(it, base) ?: it.takeIf { u -> u.startsWith("//") } }.distinct()
        }

        private fun embed(el: Element) {
            val site = el.attr("data-media-site-id").ifBlank { el.attr("data-s9e-mediaembed") }.ifBlank {
                el.selectFirst("[data-media-site-id], [data-s9e-mediaembed]")?.let { it.attr("data-media-site-id").ifBlank { it.attr("data-s9e-mediaembed") } }.orEmpty()
            }.ifBlank { null }
            val key = el.attr("data-media-key").ifBlank { el.selectFirst("[data-media-key]")?.attr("data-media-key").orEmpty() }.ifBlank { null }
            val urls = candidates(el)
            val frames = (listOf(el).filter { it.normalName() == "iframe" } + el.select("iframe")).flatMap { f ->
                listOf(f.attr("data-s9e-mediaembed-src"), f.attr("src"), f.attr("data-src")).filter { it.isNotBlank() }
            }.mapNotNull { ForumUrls.absolute(it, base) }
            val block = MediaSites.classify(site, key, urls, frames)
            when {
                block != null -> add(block)
                el.normalName() == "audio" || el.selectFirst("audio") != null ->
                    urls.firstOrNull()?.let { add(Block.Embed(site = "audio", url = it, kind = com.alexhr90.muabet.domain.model.EmbedKind.Audio)) }
                        ?: add(Block.Unknown("embed", el.outerHtml().cap()))
                else -> {
                    // Not a player we know: a link if there is one, the markup itself otherwise.
                    val link = urls.firstOrNull { it.startsWith("http") }
                    if (link != null && el.selectFirst("iframe, video") == null) add(Block.Link(url = link, title = el.text().squash().takeIf { it.isNotEmpty() }))
                    else add(Block.Unknown("embed", el.outerHtml().cap()))
                }
            }
        }

        private fun table(el: Element) {
            val rows = el.select("tr").filter { tr -> tr.parents().firstOrNull { it.normalName() == "table" } == el }.map { tr ->
                tr.children().filter { it.normalName() == "td" || it.normalName() == "th" }.map { cell ->
                    TableCell(blocks = children(cell), header = cell.normalName() == "th")
                }
            }.filter { it.isNotEmpty() }
            if (rows.isEmpty()) return
            add(Block.Table(rows))
        }

        private fun box(el: Element) {
            val title = el.selectFirst(".bbCodeBlock-title")?.text()?.squash()?.takeIf { it.isNotEmpty() }
            val content = el.selectFirst(".bbCodeBlock-content") ?: el
            val copy = content.clone()
            copy.select(".bbCodeBlock-title, .bbCodeBlock-expandLink").remove()
            val blocks = children(copy)
            if (blocks.isEmpty() && title == null) return
            add(Block.Box(title = title, blocks = blocks))
        }

        private fun file(el: Element) {
            val a = el.selectFirst("a[href*=/attachments/]") ?: el.selectFirst("a[href]")
            val url = ForumUrls.absolute(a?.attr("href"), base) ?: return box(el)
            if (MediaSites.isVideoFile(url) || MediaSites.isVideoFile(a?.text().orEmpty())) {
                add(Block.Embed(site = "video", url = url, kind = com.alexhr90.muabet.domain.model.EmbedKind.VideoFile))
                return
            }
            val name = el.selectFirst(".file-name, .contentRow-title")?.text()?.squash()?.takeIf { it.isNotEmpty() }
                ?: a?.text()?.squash()?.takeIf { it.isNotEmpty() } ?: url.substringAfterLast('/').substringBefore('?')
            val meta = el.selectFirst(".file-meta, .contentRow-minor")?.text()?.squash()?.takeIf { it.isNotEmpty() }
            add(Block.File(name = name, url = url, meta = meta))
        }

        private fun String.cap(): String = if (length <= 20_000) this else substring(0, 20_000)

        private fun list(el: Element, ordered: Boolean) {
            val items = el.children().filter { it.normalName() == "li" }.map { li -> children(li) }
            add(Block.ListBlock(ordered = ordered, items = items))
        }
    }
}
