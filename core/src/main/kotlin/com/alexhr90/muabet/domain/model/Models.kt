package com.alexhr90.muabet.domain.model

/** What the forum page said about the visitor: guest or logged in, and the CSRF token if any. */
data class SessionInfo(
    val loggedIn: Boolean,
    val username: String? = null,
    val csrf: String? = null,
) {
    companion object {
        val Guest = SessionInfo(loggedIn = false)
    }
}

/** Where a list is: the current page and the last one (1-based). */
data class PageInfo(val current: Int, val last: Int) {
    val hasNext: Boolean get() = current < last
    val hasPrevious: Boolean get() = current > 1

    companion object {
        val Single = PageInfo(1, 1)
    }
}

/** The forum home: categories with their forums, in the forum's own order. */
data class ForumHome(
    val categories: List<Category>,
    val session: SessionInfo,
)

data class Category(
    /** The category node id, when the markup shows one. */
    val id: Long?,
    val title: String,
    val forums: List<Forum>,
)

data class Forum(
    val id: Long,
    val slug: String,
    val title: String,
    val description: String? = null,
    val unread: Boolean = false,
    /** A link forum points somewhere else and has no threads. */
    val isLink: Boolean = false,
    val url: String,
    val latest: LatestThread? = null,
    val subforums: List<SubForum> = emptyList(),
)

data class SubForum(
    val id: Long,
    val slug: String,
    val title: String,
    val unread: Boolean,
    val url: String,
)

/** The thread with the newest post in a forum, as the forum list shows it. */
data class LatestThread(
    val threadId: Long?,
    val title: String,
    val url: String,
    val author: String?,
    /** Epoch seconds. */
    val timestamp: Long?,
)

/** One page of a forum's thread list. */
data class ForumPage(
    val forumId: Long,
    val title: String,
    val subforums: List<Forum>,
    /** Pinned threads (only on the first page). */
    val sticky: List<ThreadSummary>,
    val threads: List<ThreadSummary>,
    val page: PageInfo,
    val session: SessionInfo,
)

data class ThreadSummary(
    val id: Long,
    val slug: String,
    val title: String,
    val url: String,
    val prefix: String? = null,
    val author: String? = null,
    /** Epoch seconds. */
    val startedAt: Long? = null,
    val replies: Int? = null,
    /** Epoch seconds. */
    val lastPostAt: Long? = null,
    val lastPoster: String? = null,
    val unread: Boolean = false,
    val sticky: Boolean = false,
    val locked: Boolean = false,
    val lastPage: Int = 1,
)

/** One page of a thread. */
data class ThreadPage(
    val threadId: Long,
    val slug: String,
    val title: String,
    val forumId: Long?,
    val forumTitle: String?,
    val posts: List<Post>,
    val page: PageInfo,
    val session: SessionInfo,
    val canReply: Boolean,
)

data class Post(
    val id: Long,
    /** The post's number in the thread (#1 is the first post), when shown. */
    val number: Int?,
    val author: PostAuthor,
    /** Epoch seconds. */
    val timestamp: Long?,
    /** The forum's own wording of the time ("Today at 9:00"), as a fallback. */
    val timeText: String?,
    val body: List<Block>,
    val reactions: ReactionSummary?,
) {
    /** True when part of the body could not be shown natively: offer "Open on the forum". */
    val hasUnknown: Boolean get() = body.any { it.containsUnknown() }
}

data class PostAuthor(
    val name: String,
    val userId: Long? = null,
    val avatarUrl: String? = null,
    /** The user title or role ("Member", "Moderator"). */
    val title: String? = null,
    /** Label and value pairs in the forum's own words: joined, messages, reaction score, age, location. */
    val details: List<Pair<String, String>> = emptyList(),
)

data class ReactionSummary(
    /** The reaction types shown, most used first, by their forum ids. */
    val reactionIds: List<Int>,
    /** The reaction type names (Like, Love), in the same order, when the markup names them. */
    val names: List<String>,
    /** How many people reacted, as far as the summary says. */
    val total: Int,
    val listUrl: String?,
)

/** An alert from /account/alerts (milestone 2). */
data class Alert(
    val id: Long,
    val text: String,
    val url: String?,
    val unread: Boolean,
    /** Epoch seconds. */
    val timestamp: Long?,
)

/** Post content as blocks the app renders natively (SPEC §3). */
sealed interface Block {
    data class Paragraph(val runs: List<Run>) : Block
    data class Quote(val author: String?, val postId: Long?, val blocks: List<Block>) : Block
    data class Image(val url: String, val alt: String?, val link: String?) : Block
    /** A standalone link preview (an "unfurl"). */
    data class Link(val url: String, val title: String?) : Block
    data class Embed(val site: String?, val url: String) : Block
    data class Spoiler(val title: String?, val blocks: List<Block>) : Block
    data class Code(val text: String) : Block
    data class ListBlock(val ordered: Boolean, val items: List<List<Block>>) : Block
    /** Something the app does not render yet; [kind] says what, for the fallback label. */
    data class Unknown(val kind: String) : Block
}

/** A run of text with one style. */
data class Run(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strike: Boolean = false,
    val href: String? = null,
) {
    fun sameStyle(other: Run): Boolean =
        bold == other.bold && italic == other.italic && underline == other.underline && strike == other.strike && href == other.href
}

fun Block.containsUnknown(): Boolean = when (this) {
    is Block.Unknown -> true
    is Block.Quote -> blocks.any { it.containsUnknown() }
    is Block.Spoiler -> blocks.any { it.containsUnknown() }
    is Block.ListBlock -> items.any { item -> item.any { it.containsUnknown() } }
    else -> false
}
