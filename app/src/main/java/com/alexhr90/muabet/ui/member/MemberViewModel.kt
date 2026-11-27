package com.alexhr90.muabet.ui.member

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alexhr90.muabet.data.repo.ForumRepository
import com.alexhr90.muabet.domain.model.MemberProfile
import com.alexhr90.muabet.ui.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A member's profile page, read natively. */
@HiltViewModel
class MemberViewModel @Inject constructor(saved: SavedStateHandle, private val forum: ForumRepository) : ViewModel() {
    val id: Long = saved.get<Long>("id") ?: 0L
    private val slug: String = saved.get<String>("slug").orEmpty()

    private val _state = MutableStateFlow<UiState<MemberProfile>>(UiState.Loading)
    val state: StateFlow<UiState<MemberProfile>> = _state.asStateFlow()

    val loggedIn: StateFlow<Boolean> = forum.session.map { it?.loggedIn == true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        load()
    }

    fun load() {
        _state.value = UiState.Loading
        viewModelScope.launch { _state.value = UiState.Done(forum.member(slug, id)) }
    }
}
