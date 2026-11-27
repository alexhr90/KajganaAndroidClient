package com.alexhr90.muabet.ui.lists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alexhr90.muabet.R
import com.alexhr90.muabet.data.local.WatchedCacheDao
import com.alexhr90.muabet.data.local.WatchedCacheEntity
import com.alexhr90.muabet.data.repo.ForumRepository
import com.alexhr90.muabet.data.repo.LibraryRepository
import com.alexhr90.muabet.data.repo.Loaded
import com.alexhr90.muabet.data.settings.AlertState
import com.alexhr90.muabet.data.settings.SettingsRepository
import com.alexhr90.muabet.domain.filter.MuteRules
import com.alexhr90.muabet.domain.model.Alert
import com.alexhr90.muabet.domain.model.ThreadList
import com.alexhr90.muabet.domain.model.ThreadSummary
import com.alexhr90.muabet.domain.url.ForumUrls
import com.alexhr90.muabet.notify.Notifications
import com.alexhr90.muabet.ui.common.UiMessage
import com.alexhr90.muabet.ui.common.UiState
import com.alexhr90.muabet.widget.WatchedWidget
import dagger.hilt.android.lifecycle.HiltViewModel
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** An endless list so far: the items, the next page's address and how loading more went. */
data class Endless<T>(
    val items: List<T>,
    val nextUrl: String?,
    val loadingMore: Boolean = false,
    val moreFailed: Boolean = false,
)

/**
 * The shared logic of the account lists: load the first page, more on scroll, pull to refresh,
 * and wait for a login when [needsLogin] (SPEC §4 M2).
 */
abstract class EndlessViewModel<T, P>(
    protected val forum: ForumRepository,
    private val needsLogin: Boolean,
) : ViewModel() {
    protected val _state = MutableStateFlow<UiState<Endless<T>>>(UiState.Loading)
    val state: StateFlow<UiState<Endless<T>>> = _state.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    /** True for a guest on a list that needs the forum account. */
    val loginNeeded: StateFlow<Boolean> = forum.session.map { needsLogin && it?.loggedIn == false }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    protected abstract val firstUrl: String
    protected abstract suspend fun fetch(url: String): Loaded<P>
    protected abstract fun itemsOf(page: P): List<T>
    protected abstract fun nextOf(page: P): String?
    protected abstract fun idOf(item: T): Long
    protected open suspend fun onFirstPage(page: P) = Unit

    private var job: Job? = null

    /** Who the list was last loaded for (null: not yet), so a session update reloads only on a change. */
    private var loadedAs: Boolean? = null

    protected fun start() {
        viewModelScope.launch {
            // Wait until a page has shown who the visitor is; then load, and reload on a login or logout.
            forum.session.filterNotNull().map { it.loggedIn }.distinctUntilChanged().collect { loggedIn ->
                when {
                    needsLogin && !loggedIn -> {
                        job?.cancel()
                        loadedAs = false
                        _state.value = UiState.Loading
                    }
                    loadedAs == loggedIn -> Unit
                    !needsLogin && job?.isActive == true -> Unit
                    else -> load(pull = _state.value is UiState.Done)
                }
            }
        }
        // Guests can read New even before another tab loaded a page.
        if (!needsLogin && forum.session.value == null) load()
    }

    fun load(pull: Boolean = false) {
        job?.cancel()
        job = viewModelScope.launch {
            if (pull) _refreshing.value = true else if (_state.value !is UiState.Done) _state.value = UiState.Loading
            val r = fetch(firstUrl)
            loadedAs = forum.session.value?.loggedIn
            if (r is Loaded.Ok) onFirstPage(r.value)
            val mapped: Loaded<Endless<T>> = when (r) {
                is Loaded.Ok -> Loaded.Ok(Endless(itemsOf(r.value), nextOf(r.value)))
                is Loaded.Blocked -> r
                is Loaded.Failed -> r
                is Loaded.Offline -> r
            }
            if (!(pull && mapped !is Loaded.Ok && _state.value is UiState.Done)) _state.value = UiState.Done(mapped)
            _refreshing.value = false
        }
    }

    fun loadMore() {
        val current = ((_state.value as? UiState.Done)?.result as? Loaded.Ok)?.value ?: return
        val next = current.nextUrl ?: return
        if (current.loadingMore) return
        set(current.copy(loadingMore = true, moreFailed = false))
        viewModelScope.launch {
            when (val r = fetch(next)) {
                is Loaded.Ok -> {
                    val seen = current.items.mapTo(HashSet()) { idOf(it) }
                    set(current.copy(items = current.items + itemsOf(r.value).filter { idOf(it) !in seen }, nextUrl = nextOf(r.value), loadingMore = false))
                }
                else -> set(current.copy(loadingMore = false, moreFailed = true))
            }
        }
    }

    private fun set(e: Endless<T>) {
        _state.value = UiState.Done(Loaded.Ok(e))
    }
}

/** Thread lists (New posts, Watched) with the user's mutes applied. */
abstract class ThreadListViewModel(
    forum: ForumRepository,
    private val library: LibraryRepository,
    needsLogin: Boolean,
) : EndlessViewModel<ThreadSummary, ThreadList>(forum, needsLogin) {
    val rules: StateFlow<MuteRules> = library.rules.stateIn(viewModelScope, SharingStarted.Eagerly, MuteRules.None)

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    override suspend fun fetch(url: String) = forum.threadList(url)
    override fun itemsOf(page: ThreadList) = page.threads
    override fun nextOf(page: ThreadList) = page.nextUrl
    override fun idOf(item: ThreadSummary) = item.id

    fun mute(t: ThreadSummary) = viewModelScope.launch {
        library.muteThread(t.id, t.title)
        _messages.tryEmit(UiMessage(R.string.msg_thread_muted, undo = { viewModelScope.launch { library.unmuteThread(t.id) } }))
    }
}

/** New posts (`/whats-new/posts/`): guests can read it too. Hidden forums stay out (SPEC §4 M3). */
@HiltViewModel
class NewViewModel @Inject constructor(forum: ForumRepository, library: LibraryRepository) :
    ThreadListViewModel(forum, library, needsLogin = false) {
    override val firstUrl: String = ForumUrls.whatsNewPosts

    init {
        start()
    }
}

/** Watched threads; the list also feeds the home-screen widget. */
@HiltViewModel
class WatchedViewModel @Inject constructor(
    forum: ForumRepository,
    library: LibraryRepository,
    private val cache: WatchedCacheDao,
    @ApplicationContext private val context: Context,
) : ThreadListViewModel(forum, library, needsLogin = true) {
    override val firstUrl: String = ForumUrls.watchedThreads

    init {
        start()
    }

    override suspend fun onFirstPage(page: ThreadList) {
        cache.replace(page.threads.take(20).mapIndexed { i, t -> WatchedCacheEntity(t.id, t.slug, t.title, t.unread, t.lastPostAt, t.lastPoster, i) })
        WatchedWidget.refreshAll(context)
    }
}

/** Alerts. Opening the list marks them read on the forum, so the notification goes away too. */
@HiltViewModel
class AlertsViewModel @Inject constructor(
    forum: ForumRepository,
    private val settings: SettingsRepository,
    private val notifications: Notifications,
) : EndlessViewModel<Alert, com.alexhr90.muabet.domain.model.AlertList>(forum, needsLogin = true) {
    override val firstUrl: String = ForumUrls.alerts

    /** Unread direct messages, for the badge on the messages button. */
    val messagesUnread: StateFlow<Int> = forum.session.map { it?.conversationsUnread ?: 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    override suspend fun fetch(url: String) = forum.alerts(url)
    override fun itemsOf(page: com.alexhr90.muabet.domain.model.AlertList) = page.alerts
    override fun nextOf(page: com.alexhr90.muabet.domain.model.AlertList) = page.nextUrl
    override fun idOf(item: Alert) = item.id

    init {
        start()
    }

    override suspend fun onFirstPage(page: com.alexhr90.muabet.domain.model.AlertList) {
        forum.noteSession(page.session.copy(alertsUnread = 0))
        settings.setAlertState(AlertState(0, page.session.conversationsUnread))
        notifications.clearAlerts()
    }
}
