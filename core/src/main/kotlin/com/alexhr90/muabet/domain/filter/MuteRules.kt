package com.alexhr90.muabet.domain.filter

import com.alexhr90.muabet.domain.model.Post
import com.alexhr90.muabet.domain.model.ThreadSummary
import java.util.Locale

/**
 * What the user does not want to see (SPEC §3 `domain.filter`): muted threads, muted keywords and
 * hidden forums. Applied to every thread list, so a muted thread never appears in a forum, New or
 * Watched, and hidden forums stay out of New.
 */
data class MuteRules(
    val threadIds: Set<Long> = emptySet(),
    val keywords: List<String> = emptyList(),
    val hiddenForumIds: Set<Long> = emptySet(),
) {
    private val needles: List<String> = keywords.map { normalize(it) }.filter { it.isNotEmpty() }.distinct()

    /** True when [thread] should not be shown. */
    fun hides(thread: ThreadSummary): Boolean =
        thread.id in threadIds ||
            (thread.forumId != null && thread.forumId in hiddenForumIds) ||
            matchesKeyword(thread.title)

    fun hidesForum(id: Long): Boolean = id in hiddenForumIds

    /** True when a muted keyword appears in [text], ignoring case and runs of spaces. */
    fun matchesKeyword(text: String?): Boolean {
        if (text.isNullOrBlank() || needles.isEmpty()) return false
        val hay = normalize(text)
        return needles.any { hay.contains(it) }
    }

    /** True when the post's own words (not quotes) contain a muted keyword. */
    fun hidesPost(post: Post): Boolean = needles.isNotEmpty() && matchesKeyword(PostText.plain(post.body, includeQuotes = false))

    fun filter(threads: List<ThreadSummary>): List<ThreadSummary> =
        if (isEmpty) threads else threads.filterNot(::hides)

    val isEmpty: Boolean get() = threadIds.isEmpty() && needles.isEmpty() && hiddenForumIds.isEmpty()

    companion object {
        val None = MuteRules()

        fun normalize(s: String): String = s.lowercase(Locale.ROOT).replace(Regex("[\\s\\u00a0]+"), " ").trim()
    }
}
