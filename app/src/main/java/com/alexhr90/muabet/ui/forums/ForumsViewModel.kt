package com.alexhr90.muabet.ui.forums

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alexhr90.muabet.data.repo.ForumRepository
import com.alexhr90.muabet.data.repo.Loaded
import com.alexhr90.muabet.domain.model.ForumHome
import com.alexhr90.muabet.ui.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ForumsViewModel @Inject constructor(private val forum: ForumRepository) : ViewModel() {
    private val _state = MutableStateFlow<UiState<ForumHome>>(UiState.Loading)
    val state: StateFlow<UiState<ForumHome>> = _state.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    /** Categories the user folded away this session (persisted from M1). */
    private val _collapsed = MutableStateFlow<Set<String>>(emptySet())
    val collapsed: StateFlow<Set<String>> = _collapsed.asStateFlow()

    init {
        load()
    }

    fun load(pull: Boolean = false) {
        viewModelScope.launch {
            if (pull) _refreshing.value = true else if (_state.value !is UiState.Done) _state.value = UiState.Loading
            val result = forum.home()
            // A failed pull keeps what is on screen.
            if (!(pull && result !is Loaded.Ok && _state.value is UiState.Done)) _state.value = UiState.Done(result)
            _refreshing.value = false
        }
    }

    fun toggle(category: String) {
        _collapsed.value = _collapsed.value.let { if (category in it) it - category else it + category }
    }
}
