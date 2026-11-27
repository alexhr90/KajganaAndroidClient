package com.alexhr90.muabet.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        FavoriteForumEntity::class,
        HiddenForumEntity::class,
        ReadPositionEntity::class,
        MutedThreadEntity::class,
        MutedKeywordEntity::class,
        SavedThreadEntity::class,
        SavedPageEntity::class,
        WatchedCacheEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class MuabetDatabase : RoomDatabase() {
    abstract fun library(): LibraryDao
    abstract fun reading(): ReadingDao
    abstract fun saved(): SavedDao
    abstract fun watched(): WatchedCacheDao

    companion object {
        const val NAME = "muabet.db"
    }
}
