package com.alexhr90.muabet.domain.url

import java.net.URI

/** A forum page the app knows how to open natively, or something it hands to the web. */
sealed interface Route {
    data object Home : Route
    data class Category(val id: Long) : Route
    data class Forum(val id: Long, val slug: String, val page: Int = 1) : Route
    data class Thread(val id: Long, val slug: String, val page: Int = 1, val target: ThreadTarget = ThreadTarget.Page) : Route
    data class Post(val id: Long) : Route
    data class Member(val id: Long, val slug: String) : Route
    /** A forum page with no native screen yet (login, account, search…): opens in the WebView. */
    data class ForumWeb(val url: String) : Route
    /** Anything off the forum: opens in a Custom Tab. */
    data class External(val url: String) : Route
}

/** Where in a thread a link points. */
enum class ThreadTarget { Page, Unread, Latest }

/**
 * The forum's URLs (XenForo 2, SPEC §3): `/forums/<slug>.<id>/`, `/threads/<slug>.<id>/`,
 * `/page-<n>`, `/post-<id>`, `/posts/<id>/`, `/goto/post?id=<id>`.
 */
object ForumUrls {
    const val HOST = "forum.kajgana.com"
    const val BASE = "https://$HOST"

    val home: String = "$BASE/"
    val login: String = "$BASE/login/"
    val logout: String = "$BASE/logout/"
    val account: String = "$BASE/account/"
    val whatsNewPosts: String = "$BASE/whats-new/posts/"
    val watchedThreads: String = "$BASE/watched/threads"
    val alerts: String = "$BASE/account/alerts"
    val conversations: String = "$BASE/direct-messages/"
    val bookmarks: String = "$BASE/account/bookmarks"
    val rss: String = "$BASE/forums/-/index.rss"

    fun forum(slug: String, id: Long, page: Int = 1): String =
        "$BASE/forums/${node(slug, id)}/" + pageSuffix(page)

    fun thread(slug: String, id: Long, page: Int = 1): String =
        "$BASE/threads/${node(slug, id)}/" + pageSuffix(page)

    fun threadUnread(slug: String, id: Long): String = "$BASE/threads/${node(slug, id)}/unread"
    fun threadLatest(slug: String, id: Long): String = "$BASE/threads/${node(slug, id)}/latest"
    fun post(id: Long): String = "$BASE/posts/$id/"
    fun reply(slug: String, id: Long): String = "$BASE/threads/${node(slug, id)}/reply"

    private fun node(slug: String, id: Long) = if (slug.isBlank()) "$id" else "$slug.$id"
    private fun pageSuffix(page: Int) = if (page > 1) "page-$page" else ""

    /** True for the forum's own host (with or without www). */
    fun isForumHost(host: String?): Boolean {
        val h = host?.lowercase()?.removePrefix("www.") ?: return false
        return h == HOST
    }

    /** [href] made absolute against [base] (the forum by default); null when it is not a web link. */
    fun absolute(href: String?, base: String = BASE): String? {
        val raw = href?.trim().orEmpty()
        if (raw.isEmpty() || raw.startsWith("#") || raw.startsWith("javascript:", ignoreCase = true)) return null
        if (raw.startsWith("data:", ignoreCase = true)) return null
        if (raw.startsWith("//")) return "https:$raw"
        return runCatching { URI(base).resolve(raw.replace(" ", "%20")).toString() }.getOrNull()
    }

    private val forumPath = Regex("^/forums/(?:(.*)\\.)?(\\d+)/?(?:page-(\\d+))?/?$")
    private val threadPath = Regex("^/threads/(?:(.*)\\.)?(\\d+)/?(?:(page-(\\d+))|(post-(\\d+))|(unread)|(latest))?/?$")
    private val postPath = Regex("^/posts/(\\d+)(?:/.*)?$")
    private val memberPath = Regex("^/members/(?:(.*)\\.)?(\\d+)/?.*$")
    private val categoryPath = Regex("^/categories/(?:(.*)\\.)?(\\d+)/?$")

    /** Recognises a link (SPEC §3 "Links"): forum pages route natively, other sites go to a Custom Tab. */
    fun route(url: String): Route {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return Route.External(url)
        val scheme = uri.scheme?.lowercase()
        if (scheme != null && scheme != "http" && scheme != "https") return Route.External(url)
        if (uri.host != null && !isForumHost(uri.host)) return Route.External(url)
        val path = (uri.rawPath ?: "").ifEmpty { "/" }
        val query = uri.rawQuery.orEmpty()
        val fragment = uri.rawFragment.orEmpty()
        val full = absolute(url) ?: url

        if (path == "/" && query.isEmpty()) return Route.Home
        if (path == "/goto/post" || path == "/goto/post/") {
            queryParam(query, "id")?.toLongOrNull()?.let { return Route.Post(it) }
        }
        postPath.matchEntire(path)?.let { m ->
            return Route.Post(m.groupValues[1].toLong())
        }
        threadPath.matchEntire(path)?.let { m ->
            val slug = m.groupValues[1]
            val id = m.groupValues[2].toLong()
            m.groupValues[6].toLongOrNull()?.let { return Route.Post(it) }
            // A `#post-123` fragment on a thread page points at that post.
            fragment.removePrefix("post-").toLongOrNull()?.takeIf { fragment.startsWith("post-") }?.let { return Route.Post(it) }
            val target = when {
                m.groupValues[7].isNotEmpty() -> ThreadTarget.Unread
                m.groupValues[8].isNotEmpty() -> ThreadTarget.Latest
                else -> ThreadTarget.Page
            }
            val page = m.groupValues[4].toIntOrNull() ?: 1
            return Route.Thread(id, slug, page.coerceAtLeast(1), target)
        }
        forumPath.matchEntire(path)?.let { m ->
            return Route.Forum(m.groupValues[2].toLong(), m.groupValues[1], (m.groupValues[3].toIntOrNull() ?: 1).coerceAtLeast(1))
        }
        categoryPath.matchEntire(path)?.let { m -> return Route.Category(m.groupValues[2].toLong()) }
        memberPath.matchEntire(path)?.let { m -> return Route.Member(m.groupValues[2].toLong(), m.groupValues[1]) }
        return Route.ForumWeb(full)
    }

    /** The `<slug>.<id>` pair of a node or thread link, or null. */
    fun slugAndId(href: String?, kind: String): Pair<String, Long>? {
        val abs = absolute(href) ?: return null
        val path = runCatching { URI(abs).rawPath }.getOrNull() ?: return null
        val m = Regex("^/$kind/(?:(.*)\\.)?(\\d+)(?:/.*)?$").matchEntire(path) ?: return null
        return m.groupValues[1] to m.groupValues[2].toLong()
    }

    /** The number in a `/page-<n>` link, or null. */
    fun pageOf(href: String?): Int? = href?.let { Regex("/page-(\\d+)").find(it)?.groupValues?.get(1)?.toIntOrNull() }

    private fun queryParam(query: String, name: String): String? =
        query.split('&').firstNotNullOfOrNull { part ->
            val (k, v) = part.split('=', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
            if (k == name) v else null
        }
}
