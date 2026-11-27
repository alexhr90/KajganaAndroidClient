package com.alexhr90.muabet.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alexhr90.muabet.data.repo.ForumRepository
import com.alexhr90.muabet.data.repo.SessionCheck
import com.alexhr90.muabet.data.repo.SessionRepository
import com.alexhr90.muabet.data.settings.AppSettings
import com.alexhr90.muabet.data.settings.SettingsRepository
import com.alexhr90.muabet.domain.model.SessionInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val sessions: SessionRepository,
    forum: ForumRepository,
) : ViewModel() {
    val state: StateFlow<AppSettings> = settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())
    val session: StateFlow<SessionInfo?> = forum.session

    private val _check = MutableStateFlow<SessionCheck?>(null)
    val check: StateFlow<SessionCheck?> = _check.asStateFlow()

    private val _checking = MutableStateFlow(false)
    val checking: StateFlow<Boolean> = _checking.asStateFlow()

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
        }
    }
}
