package com.alexhr90.muabet.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A forum pinned to the top of the Forums tab, in the user's own order. */
@Entity(tableName = "favorite_forums")
data class FavoriteForumEntity(
    @PrimaryKey val id: Long,
    val slug: String,
    val title: String,
    val position: Int,
)

/** A forum the user hid from the Forums tab and from New. */
@Entity(tableName = "hidden_forums")
data class HiddenForumEntity(
    @PrimaryKey val id: Long,
    val title: String,
)

/** Where the user stopped in a thread: the page and the first post on screen. */
@Entity(tableName = "read_positions")
data class ReadPositionEntity(
    @PrimaryKey val threadId: Long,
    val slug: String,
    val title: String,
    val page: Int,
    val lastPage: Int,
    val postId: Long?,
    val updatedAt: Long,
)

@Entity(tableName = "muted_threads")
data class MutedThreadEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val mutedAt: Long,
)

@Entity(tableName = "muted_keywords")
data class MutedKeywordEntity(
    @PrimaryKey val keyword: String,
)

/** A thread with pages saved for offline reading. */
@Entity(tableName = "saved_threads")
data class SavedThreadEntity(
    @PrimaryKey val threadId: Long,
    val slug: String,
    val title: String,
    val forumTitle: String?,
    val savedAt: Long,
)

/**
 * One saved page: the forum's HTML, gzip-compressed, parsed again when read. Post content stays
 * on the phone only (never exported or backed up, SPEC §7).
 */
@Entity(tableName = "saved_pages", primaryKeys = ["threadId", "page"])
class SavedPageEntity(
    val threadId: Long,
    val page: Int,
    val html: ByteArray,
    val bytes: Int,
    val savedAt: Long,
)

/** The last Watched list, for the home-screen widget. Titles only. */
@Entity(tableName = "watched_cache")
data class WatchedCacheEntity(
    @PrimaryKey val threadId: Long,
    val slug: String,
    val title: String,
    val unread: Boolean,
    val lastPostAt: Long?,
    val lastPoster: String?,
    val position: Int,
)

/** Pages saved per thread, with their total size. */
data class SavedThreadSummary(
    val threadId: Long,
    val slug: String,
    val title: String,
    val forumTitle: String?,
    val savedAt: Long,
    val pages: Int,
    val bytes: Long,
    val firstPage: Int,
)
