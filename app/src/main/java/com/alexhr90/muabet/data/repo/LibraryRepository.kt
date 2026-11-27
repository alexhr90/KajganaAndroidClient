package com.alexhr90.muabet.data.repo

import com.alexhr90.muabet.data.local.FavoriteForumEntity
import com.alexhr90.muabet.data.local.HiddenForumEntity
import com.alexhr90.muabet.data.local.LibraryDao
import com.alexhr90.muabet.data.local.MutedKeywordEntity
import com.alexhr90.muabet.data.local.MutedThreadEntity
import com.alexhr90.muabet.domain.filter.MuteRules
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

/** The user's own lists: favourite forums, hidden forums, muted threads and keywords. */
@Singleton
class LibraryRepository @Inject constructor(private val dao: LibraryDao) {

    val favorites: Flow<List<FavoriteForumEntity>> = dao.favorites()
    val hidden: Flow<List<HiddenForumEntity>> = dao.hidden()
    val mutedThreads: Flow<List<MutedThreadEntity>> = dao.mutedThreads()
    val keywords: Flow<List<MutedKeywordEntity>> = dao.keywords()

    /** Everything the lists hide, for every thread list in the app. */
    val rules: Flow<MuteRules> = combine(mutedThreads, keywords, hidden) { t, k, h ->
        MuteRules(threadIds = t.mapTo(HashSet()) { it.id }, keywords = k.map { it.keyword }, hiddenForumIds = h.mapTo(HashSet()) { it.id })
    }.distinctUntilChanged()

    suspend fun pin(id: Long, slug: String, title: String) {
        val now = dao.favoritesNow()
        if (now.any { it.id == id }) return
        dao.upsertFavorites(listOf(FavoriteForumEntity(id, slug, title, (now.maxOfOrNull { it.position } ?: -1) + 1)))
    }

    suspend fun unpin(id: Long) = dao.deleteFavorite(id)

    /** Moves a favourite one place up (-1) or down (+1). */
    suspend fun move(id: Long, by: Int) {
        val list = dao.favoritesNow().toMutableList()
        val i = list.indexOfFirst { it.id == id }
        val j = i + by
        if (i < 0 || j !in list.indices) return
        val item = list.removeAt(i)
        list.add(j, item)
        dao.upsertFavorites(list.mapIndexed { index, f -> f.copy(position = index) })
    }

    suspend fun hide(id: Long, title: String) {
        dao.upsertHidden(listOf(HiddenForumEntity(id, title)))
        dao.deleteFavorite(id)
    }

    suspend fun unhide(id: Long) = dao.deleteHidden(id)

    suspend fun muteThread(id: Long, title: String) = dao.upsertMutedThreads(listOf(MutedThreadEntity(id, title, System.currentTimeMillis())))

    suspend fun unmuteThread(id: Long) = dao.deleteMutedThread(id)

    /** Adds a keyword; returns false when it was empty. */
    suspend fun addKeyword(keyword: String): Boolean {
        val k = MuteRules.normalize(keyword)
        if (k.isEmpty()) return false
        dao.insertKeywords(listOf(MutedKeywordEntity(k)))
        return true
    }

    suspend fun removeKeyword(keyword: String) = dao.deleteKeyword(keyword)
}
