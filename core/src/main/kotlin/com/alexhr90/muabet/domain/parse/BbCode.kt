package com.alexhr90.muabet.domain.parse

import com.alexhr90.muabet.domain.url.ForumUrls
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/**
 * BB code for the compose screen. The forum takes BB code in the `message` field; its edit form
 * hands back either BB code (`textarea[name=message]`) or the rich editor's HTML
 * (`message_html`), which [fromEditorHtml] turns back into BB code.
 */
object BbCode {

    /** The text of a post to edit, from the edit form's HTML (or the JSON overlay's `html.content`). */
    fun fromEditForm(html: String): String? {
        val doc = Jsoup.parse(html, ForumUrls.BASE)
        doc.selectFirst("textarea[name=message]")?.let { return it.wholeText().trimEnd() }
        doc.selectFirst("textarea[name=message_html]")?.let { return fromEditorHtml(it.wholeText()) }
        doc.selectFirst("[data-original-html]")?.let { return fromEditorHtml(it.attr("data-original-html")) }
        return null
    }

    /** The `title` of a thread's first post in the edit form, when the form offers to change it. */
    fun titleFromEditForm(html: String): String? =
        Jsoup.parse(html, ForumUrls.BASE).selectFirst("input[name=title]")?.attr("value")?.takeIf { it.isNotBlank() }

    /** The quote BB code of a post (`/posts/<id>/quote` answers `{"quote": "[QUOTE=…]…[/QUOTE]"}`). */
    fun quoteFromJson(data: Map<String, String>): String? = data["quote"]?.takeIf { it.isNotBlank() }

    /** A quote built by the app when the forum's own is not at hand. */
    fun quote(author: String, postId: Long?, memberId: Long?, body: String): String {
        val attrs = buildList {
            if (postId != null) add("post: $postId")
            if (memberId != null) add("member: $memberId")
        }
        val head = if (attrs.isEmpty()) "\"$author\"" else "\"$author, ${attrs.joinToString(", ")}\""
        return "[QUOTE=$head]\n${body.trim()}\n[/QUOTE]\n"
    }

    /** The rich editor's HTML (Froala) as BB code. */
    fun fromEditorHtml(html: String): String {
        val body = Jsoup.parseBodyFragment(html, ForumUrls.BASE).body()
        val sb = StringBuilder()
        body.childNodes().forEach { walk(it, sb) }
        return sb.toString().replace(Regex("\\n{3,}"), "\n\n").trim()
    }

    private fun walk(node: Node, sb: StringBuilder) {
        when (node) {
            is TextNode -> sb.append(node.wholeText.replace(' ', ' '))
            is Element -> element(node, sb)
        }
    }

    private fun inner(el: Element, sb: StringBuilder) = el.childNodes().forEach { walk(it, sb) }

    private fun wrap(tag: String, el: Element, sb: StringBuilder, option: String? = null) {
        sb.append('[').append(tag.uppercase())
        if (option != null) sb.append('=').append(option)
        sb.append(']')
        inner(el, sb)
        sb.append("[/").append(tag.uppercase()).append(']')
    }

    private fun element(el: Element, sb: StringBuilder) {
        val style = el.attr("style")
        when (el.normalName()) {
            "br" -> sb.append('\n')
            "p", "div" -> {
                val align = Regex("text-align:\\s*(left|center|right)").find(style)?.groupValues?.get(1)
                if (align != null && align != "left") wrap(align, el, sb) else inner(el, sb)
                if (el.nextSibling() != null) sb.append('\n')
            }
            "b", "strong" -> wrap("B", el, sb)
            "i", "em" -> wrap("I", el, sb)
            "u", "ins" -> wrap("U", el, sb)
            "s", "strike", "del" -> wrap("S", el, sb)
            "h1", "h2", "h3", "h4" -> { wrap("HEADING", el, sb, el.normalName().substring(1)); sb.append('\n') }
            "a" -> {
                val href = el.attr("href")
                if (href.isBlank()) inner(el, sb) else if (el.text() == href) sb.append("[URL]").append(href).append("[/URL]") else wrap("URL", el, sb, href)
            }
            "img" -> {
                val attach = el.attr("data-attachment")
                when {
                    attach.isNotBlank() -> {
                        val (size, id) = attach.split(':', limit = 2).let { (it.getOrNull(0) ?: "full") to (it.getOrNull(1) ?: "") }
                        sb.append("[ATTACH=").append(size).append(']').append(id).append("[/ATTACH]")
                    }
                    el.hasClass("smilie") -> sb.append(el.attr("alt"))
                    else -> sb.append("[IMG]").append(el.attr("src")).append("[/IMG]")
                }
            }
            "blockquote" -> {
                val who = el.attr("data-quote")
                val src = el.attr("data-source")
                val attrs = el.attr("data-attributes")
                val option = when {
                    who.isBlank() -> null
                    else -> "\"" + listOf(who, src, attrs).filter { it.isNotBlank() }.joinToString(", ") + "\""
                }
                sb.append(if (option == null) "[QUOTE]" else "[QUOTE=$option]")
                inner(el, sb)
                sb.append("[/QUOTE]\n")
            }
            "pre" -> {
                val lang = el.attr("data-code-type").ifBlank { null }
                sb.append(if (lang == null) "[CODE]" else "[CODE=$lang]").append(el.wholeText()).append("[/CODE]\n")
            }
            "code" -> sb.append("[ICODE]").append(el.wholeText()).append("[/ICODE]")
            "ul", "ol" -> {
                sb.append(if (el.normalName() == "ol") "[LIST=1]\n" else "[LIST]\n")
                el.children().filter { it.normalName() == "li" }.forEach { li -> sb.append("[*]"); inner(li, sb); sb.append('\n') }
                sb.append("[/LIST]\n")
            }
            "span" -> {
                val color = Regex("color:\\s*([^;]+)").find(style)?.groupValues?.get(1)?.trim()
                if (color != null && !style.contains("background")) wrap("COLOR", el, sb, color) else inner(el, sb)
            }
            "iframe", "script", "style" -> Unit
            else -> inner(el, sb)
        }
    }
}
