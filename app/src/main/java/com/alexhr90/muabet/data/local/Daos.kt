package com.alexhr90.muabet.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryDao {
    @Query("SELECT * FROM favorite_forums ORDER BY position, title")
    fun favorites(): Flow<List<FavoriteForumEntity>>

    @Query("SELECT * FROM favorite_forums ORDER BY position, title")
    suspend fun favoritesNow(): List<FavoriteForumEntity>

    @Upsert
    suspend fun upsertFavorites(items: List<FavoriteForumEntity>)

    @Query("DELETE FROM favorite_forums WHERE id = :id")
    suspend fun deleteFavorite(id: Long)

    @Query("DELETE FROM favorite_forums")
    suspend fun clearFavorites()

    @Query("SELECT * FROM hidden_forums ORDER BY title")
    fun hidden(): Flow<List<HiddenForumEntity>>

    @Query("SELECT * FROM hidden_forums ORDER BY title")
    suspend fun hiddenNow(): List<HiddenForumEntity>

    @Upsert
    suspend fun upsertHidden(items: List<HiddenForumEntity>)

    @Query("DELETE FROM hidden_forums WHERE id = :id")
    suspend fun deleteHidden(id: Long)

    @Query("SELECT * FROM muted_threads ORDER BY mutedAt DESC")
    fun mutedThreads(): Flow<List<MutedThreadEntity>>

    @Query("SELECT * FROM muted_threads ORDER BY mutedAt DESC")
    suspend fun mutedThreadsNow(): List<MutedThreadEntity>

    @Upsert
    suspend fun upsertMutedThreads(items: List<MutedThreadEntity>)

    @Query("DELETE FROM muted_threads WHERE id = :id")
    suspend fun deleteMutedThread(id: Long)

    @Query("SELECT * FROM muted_keywords ORDER BY keyword")
    fun keywords(): Flow<List<MutedKeywordEntity>>

    @Query("SELECT * FROM muted_keywords ORDER BY keyword")
    suspend fun keywordsNow(): List<MutedKeywordEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertKeywords(items: List<MutedKeywordEntity>)

    @Query("DELETE FROM muted_keywords WHERE keyword = :keyword")
    suspend fun deleteKeyword(keyword: String)
}

@Dao
interface ReadingDao {
    @Query("SELECT * FROM read_positions WHERE threadId = :threadId")
    suspend fun position(threadId: Long): ReadPositionEntity?

    @Upsert
    suspend fun upsert(position: ReadPositionEntity)

    /** Keeps the newest [keep] positions; older threads start again at their first page. */
    @Query("DELETE FROM read_positions WHERE threadId NOT IN (SELECT threadId FROM read_positions ORDER BY updatedAt DESC LIMIT :keep)")
    suspend fun prune(keep: Int)

    @Query("DELETE FROM read_positions")
    suspend fun clear()
}

@Dao
interface SavedDao {
    @Query(
        """SELECT t.threadId, t.slug, t.title, t.forumTitle, t.savedAt,
           COUNT(p.page) AS pages, COALESCE(SUM(p.bytes), 0) AS bytes, COALESCE(MIN(p.page), 1) AS firstPage
           FROM saved_threads t LEFT JOIN saved_pages p ON p.threadId = t.threadId
           GROUP BY t.threadId ORDER BY t.savedAt DESC""",
    )
    fun threads(): Flow<List<SavedThreadSummary>>

    @Query("SELECT page FROM saved_pages WHERE threadId = :threadId ORDER BY page")
    fun pages(threadId: Long): Flow<List<Int>>

    @Query("SELECT * FROM saved_pages WHERE threadId = :threadId AND page = :page")
    suspend fun page(threadId: Long, page: Int): SavedPageEntity?

    @Query("SELECT COALESCE(SUM(bytes), 0) FROM saved_pages")
    suspend fun totalBytes(): Long

    @Upsert
    suspend fun upsertThread(thread: SavedThreadEntity)

    @Upsert
    suspend fun upsertPage(page: SavedPageEntity)

    @Query("DELETE FROM saved_pages WHERE threadId = :threadId")
    suspend fun deletePages(threadId: Long)

    @Query("DELETE FROM saved_threads WHERE threadId = :threadId")
    suspend fun deleteThreadRow(threadId: Long)

    @Transaction
    suspend fun deleteThread(threadId: Long) {
        deletePages(threadId)
        deleteThreadRow(threadId)
    }
}

@Dao
interface WatchedCacheDao {
    @Query("SELECT * FROM watched_cache ORDER BY position LIMIT :limit")
    suspend fun top(limit: Int): List<WatchedCacheEntity>

    @Query("DELETE FROM watched_cache")
    suspend fun clear()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(items: List<WatchedCacheEntity>)

    @Transaction
    suspend fun replace(items: List<WatchedCacheEntity>) {
        clear()
        insert(items)
    }
}
