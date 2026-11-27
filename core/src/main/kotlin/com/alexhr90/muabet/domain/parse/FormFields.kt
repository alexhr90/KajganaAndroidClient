package com.alexhr90.muabet.domain.parse

import com.alexhr90.muabet.domain.url.ForumUrls
import org.jsoup.Jsoup

/**
 * The fields a browser would send with a forum form, read from the form's HTML: text and hidden
 * inputs, ticked boxes, chosen options and text areas. The app sends them back with its own
 * message, so an edit keeps the thread's title, prefix and other settings exactly as they were.
 */
object FormFields {
    /** Fields the app always sets itself. */
    private val own = setOf("_xfToken", "_xfResponseType", "_xfWithData", "_xfRequestUri", "message", "message_html")

    fun collect(html: String, formSelector: String = "form"): List<Pair<String, String>> {
        val doc = Jsoup.parse(html, ForumUrls.BASE)
        val form = doc.selectFirst(formSelector) ?: doc.body()
        val out = mutableListOf<Pair<String, String>>()
        for (el in form.select("input[name], textarea[name], select[name]")) {
            val name = el.attr("name")
            if (name in own || el.hasAttr("disabled")) continue
            when (el.normalName()) {
                "input" -> when (el.attr("type").lowercase()) {
                    "checkbox", "radio" -> if (el.hasAttr("checked")) out += name to el.attr("value").ifEmpty { "1" }
                    "submit", "button", "file", "image", "reset" -> Unit
                    else -> out += name to el.attr("value")
                }
                "textarea" -> out += name to el.wholeText()
                "select" -> {
                    val chosen = el.select("option[selected]").ifEmpty { el.select("option").take(1) }
                    chosen.forEach { out += name to it.attr("value").ifEmpty { it.text() } }
                }
            }
        }
        return out
    }

    /** The attachment hash the form was given, for uploads that belong to it. */
    fun attachmentHash(fields: List<Pair<String, String>>): String? = fields.firstOrNull { it.first == "attachment_hash" }?.second?.takeIf { it.isNotBlank() }
}
