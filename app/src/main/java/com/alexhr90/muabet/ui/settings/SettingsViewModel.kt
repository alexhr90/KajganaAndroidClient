package com.alexhr90.muabet.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alexhr90.muabet.R
import com.alexhr90.muabet.data.backup.BackupManager
import com.alexhr90.muabet.data.repo.ForumRepository
import com.alexhr90.muabet.data.repo.LibraryRepository
import com.alexhr90.muabet.data.repo.SavedRepository
import com.alexhr90.muabet.data.repo.SessionCheck
import com.alexhr90.muabet.data.repo.SessionRepository
import com.alexhr90.muabet.data.settings.AppSettings
import com.alexhr90.muabet.data.settings.SettingsRepository
import com.alexhr90.muabet.domain.model.SessionInfo
import com.alexhr90.muabet.ui.common.UiMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Counts for the "Your lists" rows. */
data class LibraryCounts(val hidden: Int = 0, val mutedThreads: Int = 0, val keywords: Int = 0, val saved: Int = 0, val savedBytes: Long = 0)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val sessions: SessionRepository,
    private val backup: BackupManager,
    library: LibraryRepository,
    saved: SavedRepository,
    forum: ForumRepository,
) : ViewModel() {
    val state: StateFlow<AppSettings> = settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())
    val session: StateFlow<SessionInfo?> = forum.session

    val counts: StateFlow<LibraryCounts> = kotlinx.coroutines.flow.combine(
        library.hidden,
        library.mutedThreads,
        library.keywords,
        saved.threads,
    ) { h, t, k, s ->
        LibraryCounts(h.size, t.size, k.size, s.size, s.sumOf { it.bytes })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryCounts())

    private val _check = MutableStateFlow<SessionCheck?>(null)
    val check: StateFlow<SessionCheck?> = _check.asStateFlow()

    private val _checking = MutableStateFlow(false)
    val checking: StateFlow<Boolean> = _checking.asStateFlow()

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    val loggedIn: StateFlow<Boolean> = forum.session.map { it?.loggedIn == true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun update(change: (AppSettings) -> AppSettings) {
        viewModelScope.launch { settings.update(change) }
    }

    /** Spike 1 (SPEC §4 M0): one request with OkHttp and the WebView's cookies. */
    fun runCheck() {
        if (_checking.value) return
        viewModelScope.launch {
            _checking.value = true
            _check.value = sessions.check()
            _checking.value = false
        }
    }

    fun logout() {
        viewModelScope.launch {
            sessions.logout()
            _check.value = null
            _messages.tryEmit(UiMessage(R.string.msg_logged_out))
        }
    }

    fun export(uri: Uri) {
        viewModelScope.launch {
            val ok = runCatching { backup.export(uri) }.isSuccess
            _messages.tryEmit(UiMessage(if (ok) R.string.msg_backup_saved else R.string.msg_backup_failed))
        }
    }

    fun restore(uri: Uri) {
        viewModelScope.launch {
            val r = runCatching { backup.restore(uri) }.getOrNull()
            _messages.tryEmit(if (r != null) UiMessage(R.string.msg_restored, r.favorites + r.hidden + r.mutedThreads + r.keywords) else UiMessage(R.string.msg_restore_failed))
        }
    }
}
