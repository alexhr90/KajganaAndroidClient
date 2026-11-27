package com.alexhr90.muabet.domain.parse

import com.alexhr90.muabet.domain.model.FormResult
import com.alexhr90.muabet.domain.model.Reaction
import com.alexhr90.muabet.domain.url.ForumUrls
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.jsoup.Jsoup

/**
 * XenForo's answers to forms sent with `_xfResponseType=json`: `{"status":"ok","redirect":…}`, or
 * `{"status":"error","errors":[…]}`. A form token that expired is a "security error", which the
 * app answers by fetching a fresh token and trying once more.
 */
object XfJson {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(body: String): FormResult {
        val root = runCatching { json.parseToJsonElement(body.trim()) }.getOrNull() as? JsonObject
            ?: return FormResult.Refused(listOf(htmlError(body) ?: "Unexpected answer from the forum"))
        val status = root.str("status")
        val errors = errors(root["errors"]) + listOfNotNull(root.str("error"))
        if (status == "error" || errors.isNotEmpty()) {
            if (errors.any(::isSecurityError)) return FormResult.TokenExpired
            return FormResult.Refused(errors.ifEmpty { listOf("The forum refused the request") })
        }
        if (root["exception"] != null) return FormResult.Refused(listOf(root.str("exception") ?: "The forum hit an error"))
        val data = mutableMapOf<String, String>()
        flatten("", root, data)
        return FormResult.Ok(
            redirect = root.str("redirect")?.let { ForumUrls.absolute(it) },
            message = root.str("message"),
            data = data,
        )
    }

    /** The HTML a JSON answer carries (`html.content`), for overlays like the reaction picker. */
    fun html(body: String): String? {
        val root = runCatching { json.parseToJsonElement(body.trim()) }.getOrNull() as? JsonObject ?: return null
        return ((root["html"] as? JsonObject)?.get("content") as? JsonPrimitive)?.contentOrNull
            ?: (root["html"] as? JsonPrimitive)?.contentOrNull
    }

    /**
     * The reactions the forum offers, from the react overlay (`/posts/<id>/react`), which lists
     * one link per reaction with `data-reaction-id` and the reaction's picture or name.
     */
    fun reactions(html: String): List<Reaction> {
        val doc = Jsoup.parse(html, ForumUrls.BASE)
        return doc.select("[data-reaction-id], a[href*=reaction_id=]").mapNotNull { a ->
            val id = a.attr("data-reaction-id").toIntOrNull()
                ?: Regex("reaction_id=(\\d+)").find(a.attr("href"))?.groupValues?.get(1)?.toIntOrNull()
                ?: return@mapNotNull null
            if (id <= 0) return@mapNotNull null
            val img = a.selectFirst("img")
            val name = (img?.attr("title")?.ifBlank { img.attr("alt") } ?: a.attr("title")).ifBlank { a.attr("data-xf-init-title") }
                .ifBlank { a.text() }.squash().ifBlank { "#$id" }
            val src = img?.let { listOf(it.attr("src"), it.attr("data-src")).firstNotNullOfOrNull { s -> ForumUrls.absolute(s) } }
            Reaction(id, name, src?.takeIf { !it.contains("/data:") })
        }.distinctBy { it.id }
    }

    private fun isSecurityError(e: String): Boolean {
        val t = e.lowercase()
        return "security error" in t || "безбедносна грешка" in t || "безбедносна" in t && "грешка" in t || "sicherheitsfehler" in t
    }

    private fun errors(e: JsonElement?): List<String> = when (e) {
        null, is JsonNull -> emptyList()
        is JsonPrimitive -> listOfNotNull(e.contentOrNull?.let(::plain))
        is JsonArray -> e.flatMap { errors(it) }
        is JsonObject -> e.values.flatMap { errors(it) }
    }.filter { it.isNotBlank() }

    private fun plain(s: String): String = if ('<' in s) Jsoup.parse(s).text().squash() else s.squash()

    private fun htmlError(body: String): String? {
        if (!body.contains('<')) return null
        val doc = Jsoup.parse(body)
        return doc.selectFirst(".blockMessage, .p-body-pageContent .block-body, .errorOverlay")?.text()?.squash()?.takeIf { it.isNotEmpty() }
    }

    private fun flatten(prefix: String, e: JsonElement, out: MutableMap<String, String>) {
        when (e) {
            is JsonObject -> e.forEach { (k, v) -> if (k != "html") flatten(if (prefix.isEmpty()) k else "$prefix.$k", v, out) }
            is JsonPrimitive -> e.contentOrNull?.let { out[prefix] = it }
            else -> Unit
        }
    }

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
}
