package com.alexhr90.muabet.domain.parse

import com.alexhr90.muabet.domain.model.PageInfo
import com.alexhr90.muabet.domain.model.SessionInfo
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/** The page did not look like the forum page we expected. The app then offers "Open on the forum". */
class ParseException(message: String) : Exception(message)

internal fun parseDocument(html: String, url: String): Document = Jsoup.parse(html, url)

/**
 * The session as the page shows it. XenForo puts `data-logged-in` and `data-csrf` on `<html>`;
 * the visitor's name is in the account link of the top navigation.
 */
object SessionParser {
    fun parse(doc: Document): SessionInfo {
        val html = doc.selectFirst("html")
        val flag = html?.attr("data-logged-in")
        val nameEl = doc.selectFirst(".p-navgroup-link--user .p-navgroup-linkText")
            ?: doc.selectFirst("a.p-navgroup-link--user")
        val loggedIn = when (flag) {
            "true" -> true
            "false" -> false
            else -> nameEl != null
        }
        val name = if (loggedIn) {
            nameEl?.text()?.trim()?.takeIf { it.isNotEmpty() }
                ?: doc.selectFirst(".p-navgroup-link--user img[alt]")?.attr("alt")?.trim()?.takeIf { it.isNotEmpty() }
        } else {
            null
        }
        val csrf = html?.attr("data-csrf")?.takeIf { it.isNotBlank() }
            ?: doc.selectFirst("input[name=_xfToken]")?.attr("value")?.takeIf { it.isNotBlank() }
        return SessionInfo(loggedIn = loggedIn, username = name, csrf = csrf)
    }

    fun parse(html: String, url: String): SessionInfo = parse(parseDocument(html, url))
}

/** The page position from XenForo's page navigation; a page without one is page 1 of 1. */
internal fun parsePageInfo(doc: Document): PageInfo {
    val nav = doc.selectFirst(".pageNav") ?: return PageInfo.Single
    val numbers = nav.select(".pageNav-page").mapNotNull { it.text().trim().toIntOrNull() }
    val current = nav.selectFirst(".pageNav-page--current")?.text()?.trim()?.toIntOrNull()
        ?: doc.selectFirst("input.js-pageJumpPage")?.attr("value")?.toIntOrNull()
        ?: 1
    val jumpMax = doc.selectFirst("input.js-pageJumpPage")?.attr("max")?.toIntOrNull()
    val last = maxOf(numbers.maxOrNull() ?: 1, jumpMax ?: 1, current)
    return PageInfo(current = current.coerceAtLeast(1), last = last)
}

private val offsetNoColon = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssxx")

/** Epoch seconds of a XenForo `<time>`: `data-timestamp` or `data-time`, else its `datetime`. */
internal fun Element.epochSeconds(): Long? {
    attr("data-timestamp").toLongOrNull()?.let { return it }
    attr("data-time").toLongOrNull()?.let { return it }
    val dt = attr("datetime").trim()
    if (dt.isEmpty()) return null
    return runCatching { OffsetDateTime.parse(dt, offsetNoColon).toEpochSecond() }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(dt).toEpochSecond() }.getOrNull()
}

/**
 * Counts as the forum prints them: "1,234", "1.234", "1 234", "12K", "1.2K", "3M".
 * Returns null when there is no number.
 */
fun parseCount(text: String?): Int? {
    val t = text?.trim()?.replace(' ', ' ')?.uppercase() ?: return null
    if (t.isEmpty()) return null
    val m = Regex("^([0-9]+(?:[.,][0-9]+)?)\\s*([KM])$").find(t)
    if (m != null) {
        val n = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        val mult = if (m.groupValues[2] == "K") 1_000 else 1_000_000
        return (n * mult).toInt()
    }
    val digits = t.filter { it.isDigit() }
    return digits.takeIf { it.isNotEmpty() && it.length <= 9 }?.toInt()
}

/** Text with runs of whitespace (including no-break spaces) folded to one space. */
internal fun String.squash(): String = replace(Regex("[\\s\\u00a0]+"), " ").trim()
