package com.alexhr90.muabet.data.repo

import com.alexhr90.muabet.data.local.ReadPositionEntity
import com.alexhr90.muabet.data.local.ReadingDao
import javax.inject.Inject
import javax.inject.Singleton

/** Remembered positions per thread (SPEC §4 M1): the page and the post the user stopped at. */
@Singleton
class ReadingRepository @Inject constructor(private val dao: ReadingDao) {

    suspend fun position(threadId: Long): ReadPositionEntity? = dao.position(threadId)

    suspend fun save(threadId: Long, slug: String, title: String, page: Int, lastPage: Int, postId: Long?) {
        if (threadId <= 0) return
        dao.upsert(ReadPositionEntity(threadId, slug, title, page, lastPage, postId, System.currentTimeMillis()))
        if (++writes % 50 == 0) dao.prune(KEEP)
    }

    suspend fun clear() = dao.clear()

    private var writes = 0

    private companion object {
        const val KEEP = 2_000
    }
}
