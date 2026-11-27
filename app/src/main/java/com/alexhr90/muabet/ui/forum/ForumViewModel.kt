package com.alexhr90.muabet.ui.forum

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alexhr90.muabet.data.repo.ForumRepository
import com.alexhr90.muabet.data.repo.Loaded
import com.alexhr90.muabet.domain.model.ForumPage
import com.alexhr90.muabet.domain.model.ThreadSummary
import com.alexhr90.muabet.ui.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A forum's thread list so far: the first page's header and every thread loaded by scrolling. */
data class ForumList(
    val first: ForumPage,
    val threads: List<ThreadSummary>,
    val loadedPage: Int,
    val lastPage: Int,
    val loadingMore: Boolean = false,
    val moreFailed: Boolean = false,
) {
    val hasMore: Boolean get() = loadedPage < lastPage
}

@HiltViewModel
class ForumViewModel @Inject constructor(
    saved: SavedStateHandle,
    private val forum: ForumRepository,
) : ViewModel() {
    val forumId: Long = checkNotNull(saved.get<Long>("id"))
    val slug: String = saved.get<String>("slug").orEmpty()
    val initialTitle: String = saved.get<String>("title").orEmpty()

    private val _state = MutableStateFlow<UiState<ForumList>>(UiState.Loading)
    val state: StateFlow<UiState<ForumList>> = _state.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    init {
        load()
    }

    fun load(pull: Boolean = false) {
        viewModelScope.launch {
            if (pull) _refreshing.value = true else _state.value = UiState.Loading
            val result = forum.forum(slug, forumId, 1)
            val mapped: Loaded<ForumList> = when (result) {
                is Loaded.Ok -> Loaded.Ok(ForumList(result.value, result.value.threads, 1, result.value.page.last))
                is Loaded.Blocked -> result
                is Loaded.Failed -> result
                is Loaded.Offline -> result
            }
            if (!(pull && mapped !is Loaded.Ok && _state.value is UiState.Done)) _state.value = UiState.Done(mapped)
            _refreshing.value = false
        }
    }

    /** Infinite scroll: the next page, appended. Threads that moved up meanwhile are not repeated. */
    fun loadMore() {
        val current = ((_state.value as? UiState.Done)?.result as? Loaded.Ok)?.value ?: return
        if (current.loadingMore || !current.hasMore) return
        set(current.copy(loadingMore = true, moreFailed = false))
        viewModelScope.launch {
            val next = current.loadedPage + 1
            when (val r = forum.forum(slug, forumId, next)) {
                is Loaded.Ok -> {
                    val seen = current.threads.mapTo(HashSet()) { it.id }
                    set(
                        current.copy(
                            threads = current.threads + r.value.threads.filter { it.id !in seen },
                            loadedPage = next,
                            lastPage = maxOf(r.value.page.last, next),
                            loadingMore = false,
                        ),
                    )
                }
                else -> set(current.copy(loadingMore = false, moreFailed = true))
            }
        }
    }

    private fun set(list: ForumList) {
        _state.value = UiState.Done(Loaded.Ok(list))
    }
}
