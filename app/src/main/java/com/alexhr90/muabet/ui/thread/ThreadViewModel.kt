package com.alexhr90.muabet.ui.thread

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alexhr90.muabet.data.repo.ForumRepository
import com.alexhr90.muabet.data.repo.Loaded
import com.alexhr90.muabet.domain.model.ThreadPage
import com.alexhr90.muabet.ui.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One thread, one page at a time; any page of a 1,000+ page thread opens directly. */
@HiltViewModel
class ThreadViewModel @Inject constructor(
    private val saved: SavedStateHandle,
    private val forum: ForumRepository,
) : ViewModel() {
    /** The thread; -1 until a link given as [at] (a post, unread, latest) has been resolved. */
    val threadId: Long get() = saved.get<Long>("id") ?: -1L
    val slug: String get() = saved.get<String>("slug").orEmpty()
    val initialTitle: String = saved.get<String>("title").orEmpty()
    /** A thread link to open at (a post, unread, latest): the forum redirects to the right page. */
    private val at: String? = saved.get<String>("at")?.takeIf { it.isNotBlank() }

    private val _state = MutableStateFlow<UiState<ThreadPage>>(UiState.Loading)
    val state: StateFlow<UiState<ThreadPage>> = _state.asStateFlow()

    /** The page on screen; survives process death through the saved state. */
    var page: Int
        get() = saved.get<Int>("page")?.coerceAtLeast(1) ?: 1
        private set(value) {
            saved["page"] = value
        }

    private var job: Job? = null

    init {
        load()
    }

    fun load() {
        job?.cancel()
        _state.value = UiState.Loading
        job = viewModelScope.launch {
            val result = if (at != null && saved.get<Boolean>("atResolved") != true) {
                forum.threadAt(at).also { r ->
                    if (r is Loaded.Ok) {
                        saved["atResolved"] = true
                        saved["id"] = r.value.threadId
                        saved["slug"] = r.value.slug
                        page = r.value.page.current
                    }
                }
            } else {
                forum.thread(slug, threadId, page)
            }
            _state.value = UiState.Done(result)
        }
    }

    fun goTo(target: Int) {
        val last = ((_state.value as? UiState.Done)?.result as? Loaded.Ok)?.value?.page?.last ?: Int.MAX_VALUE
        val p = target.coerceIn(1, last)
        if (p == page && _state.value is UiState.Done) return
        saved["atResolved"] = true
        page = p
        load()
    }
}
