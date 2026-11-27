package com.alexhr90.muabet.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alexhr90.muabet.data.local.HiddenForumEntity
import com.alexhr90.muabet.data.local.MutedKeywordEntity
import com.alexhr90.muabet.data.local.MutedThreadEntity
import com.alexhr90.muabet.data.local.SavedThreadSummary
import com.alexhr90.muabet.data.repo.LibraryRepository
import com.alexhr90.muabet.data.repo.SavedRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The user's own lists, for the hidden forums, mutes and saved threads screens. */
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val library: LibraryRepository,
    private val saved: SavedRepository,
) : ViewModel() {
    val hidden: StateFlow<List<HiddenForumEntity>?> = library.hidden.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val mutedThreads: StateFlow<List<MutedThreadEntity>?> = library.mutedThreads.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val keywords: StateFlow<List<MutedKeywordEntity>?> = library.keywords.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val savedThreads: StateFlow<List<SavedThreadSummary>?> = saved.threads.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun unhide(id: Long) = viewModelScope.launch { library.unhide(id) }
    fun unmute(id: Long) = viewModelScope.launch { library.unmuteThread(id) }
    fun addKeyword(k: String) = viewModelScope.launch { library.addKeyword(k) }
    fun removeKeyword(k: String) = viewModelScope.launch { library.removeKeyword(k) }
    fun deleteSaved(id: Long) = viewModelScope.launch { saved.delete(id) }
}
