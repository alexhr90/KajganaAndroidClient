package com.alexhr90.muabet.ui.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alexhr90.muabet.data.repo.ForumRepository
import com.alexhr90.muabet.data.repo.Loaded
import com.alexhr90.muabet.domain.model.SearchResult
import com.alexhr90.muabet.domain.url.ForumUrls
import com.alexhr90.muabet.ui.common.UiState
import com.alexhr90.muabet.ui.lists.Endless
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Search (SPEC §4 M3) across the forum or within one thread, newest first, through the forum's own
 * search. Results load page by page as the list scrolls.
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val saved: SavedStateHandle,
    private val forum: ForumRepository,
) : ViewModel() {
    /** Set when searching within one thread. */
    val threadId: Long? = saved.get<Long>("thread")?.takeIf { it > 0 }
    val threadTitle: String = saved.get<String>("title").orEmpty()
    /** A ready search address (a member's posts), run at once. */
    private val presetUrl: String? = saved.get<String>("url")?.takeIf { it.isNotBlank() }
    val isPreset: Boolean get() = presetUrl != null

    private val _query = MutableStateFlow(saved.get<String>("q").orEmpty())
    val query: StateFlow<String> = _query.asStateFlow()

    private val _titlesOnly = MutableStateFlow(false)
    val titlesOnly: StateFlow<Boolean> = _titlesOnly.asStateFlow()

    /** Null until the first search. */
    private val _state = MutableStateFlow<UiState<Endless<SearchResult>>?>(null)
    val state: StateFlow<UiState<Endless<SearchResult>>?> = _state.asStateFlow()

    private var job: Job? = null

    init {
        presetUrl?.let { run(it) }
    }

    fun setQuery(q: String) {
        _query.value = q
        saved["q"] = q
    }

    fun setTitlesOnly(on: Boolean) {
        _titlesOnly.value = on
        if (_state.value != null) search()
    }

    fun search() {
        val q = _query.value.trim()
        if (q.length < 2) return
        run(ForumUrls.search(q, threadId, titlesOnly = _titlesOnly.value && threadId == null))
    }

    private fun run(url: String) {
        job?.cancel()
        _state.value = UiState.Loading
        job = viewModelScope.launch {
            _state.value = UiState.Done(
                when (val r = forum.search(url)) {
                    is Loaded.Ok -> Loaded.Ok(Endless(r.value.results, r.value.nextUrl))
                    is Loaded.Blocked -> r
                    is Loaded.Failed -> r
                    is Loaded.Offline -> r
                },
            )
        }
    }

    fun loadMore() {
        val current = ((_state.value as? UiState.Done)?.result as? Loaded.Ok)?.value ?: return
        val next = current.nextUrl ?: return
        if (current.loadingMore) return
        set(current.copy(loadingMore = true, moreFailed = false))
        viewModelScope.launch {
            when (val r = forum.search(next)) {
                is Loaded.Ok -> {
                    val seen = current.items.mapTo(HashSet()) { it.url }
                    set(current.copy(items = current.items + r.value.results.filter { it.url !in seen }, nextUrl = r.value.nextUrl, loadingMore = false))
                }
                else -> set(current.copy(loadingMore = false, moreFailed = true))
            }
        }
    }

    private fun set(e: Endless<SearchResult>) {
        _state.value = UiState.Done(Loaded.Ok(e))
    }
}
