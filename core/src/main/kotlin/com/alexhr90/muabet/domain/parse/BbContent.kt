package com.alexhr90.muabet.domain.parse

import com.alexhr90.muabet.domain.model.Block
import com.alexhr90.muabet.domain.model.Run
import com.alexhr90.muabet.domain.url.ForumUrls
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/**
 * Turns a post's rendered BB code (`.bbWrapper`) into [Block]s: paragraphs of styled runs,
 * quotes, images, link previews, embeds, spoilers, code and lists. Anything else becomes
 * [Block.Unknown], so the app can offer "Open on the forum" instead of showing nothing.
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
                el.hasClass("bbMediaWrapper") || el.hasClass("bbMediaJustifier") || el.hasAttr("data-s9e-mediaembed") ||
                    tag == "iframe" || tag == "video" -> embed(el)
                el.hasClass("bbTable") || tag == "table" -> add(Block.Unknown("table"))
                el.hasClass("bbCodeBlock") -> add(Block.Unknown(el.classNames().firstOrNull { it.startsWith("bbCodeBlock--") }?.removePrefix("bbCodeBlock--") ?: "block"))
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
            val src = listOf(el.attr("data-url"), el.attr("data-src"), el.attr("src"))
                .firstNotNullOfOrNull { ForumUrls.absolute(it, base) }
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
                add(Block.Unknown("unfurl"))
                return
            }
            val title = el.selectFirst(".contentRow-header")?.text()?.squash()?.takeIf { it.isNotEmpty() }
            add(Block.Link(url = url, title = title))
        }

        private fun embed(el: Element) {
            val site = el.attr("data-media-site-id").ifBlank { el.attr("data-s9e-mediaembed") }.ifBlank { null }
            val key = el.attr("data-media-key").ifBlank { null }
            val frame = if (el.normalName() == "iframe" || el.normalName() == "video") el else el.selectFirst("iframe, video, video source")
            val src = ForumUrls.absolute(frame?.attr("src") ?: frame?.attr("data-src"), base)
            val url = when {
                site == "youtube" && key != null -> "https://www.youtube.com/watch?v=$key"
                src != null -> youtubeWatch(src) ?: src
                else -> el.selectFirst("a[href]")?.let { ForumUrls.absolute(it.attr("href"), base) }
            }
            if (url == null) add(Block.Unknown("embed")) else add(Block.Embed(site = site ?: if (frame?.normalName() == "video") "video" else null, url = url))
        }

        private fun youtubeWatch(src: String): String? =
            Regex("youtube(?:-nocookie)?\\.com/embed/([A-Za-z0-9_-]{6,})").find(src)?.groupValues?.get(1)?.let { "https://www.youtube.com/watch?v=$it" }

        private fun list(el: Element, ordered: Boolean) {
            val items = el.children().filter { it.normalName() == "li" }.map { li -> children(li) }
            add(Block.ListBlock(ordered = ordered, items = items))
        }
    }
}
