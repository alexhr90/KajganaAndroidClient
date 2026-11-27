package com.alexhr90.muabet.ui.forums

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alexhr90.muabet.R
import com.alexhr90.muabet.data.local.FavoriteForumEntity
import com.alexhr90.muabet.data.local.HiddenForumEntity
import com.alexhr90.muabet.data.repo.ForumRepository
import com.alexhr90.muabet.data.repo.LibraryRepository
import com.alexhr90.muabet.data.repo.Loaded
import com.alexhr90.muabet.data.settings.SettingsRepository
import com.alexhr90.muabet.domain.model.Forum
import com.alexhr90.muabet.domain.model.ForumHome
import com.alexhr90.muabet.ui.common.UiMessage
import com.alexhr90.muabet.ui.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ForumsViewModel @Inject constructor(
    private val forum: ForumRepository,
    private val library: LibraryRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<UiState<ForumHome>>(UiState.Loading)
    val state: StateFlow<UiState<ForumHome>> = _state.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    /** Categories the user folded away; remembered (SPEC §4 M1). */
    val collapsed: StateFlow<Set<String>> = settings.settings.map { it.collapsedCategories }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val favorites: StateFlow<List<FavoriteForumEntity>> = library.favorites
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val hidden: StateFlow<List<HiddenForumEntity>> = library.hidden
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    init {
        load()
        // A login or logout elsewhere: reload as the new visitor.
        viewModelScope.launch {
            forum.session.filterNotNull().map { it.loggedIn }.distinctUntilChanged().drop(1).collect { load(pull = true) }
        }
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
        viewModelScope.launch {
            settings.update { s ->
                val set = s.collapsedCategories
                s.copy(collapsedCategories = if (category in set) set - category else set + category)
            }
        }
    }

    fun pin(f: Forum) = viewModelScope.launch {
        library.pin(f.id, f.slug, f.title)
        _messages.tryEmit(UiMessage(R.string.msg_pinned))
    }

    fun unpin(id: Long) = viewModelScope.launch { library.unpin(id) }

    fun move(id: Long, by: Int) = viewModelScope.launch { library.move(id, by) }

    fun hide(f: Forum) = viewModelScope.launch {
        library.hide(f.id, f.title)
        _messages.tryEmit(UiMessage(R.string.msg_forum_hidden, undo = { viewModelScope.launch { library.unhide(f.id) } }))
    }
}
