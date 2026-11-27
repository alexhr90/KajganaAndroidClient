package com.alexhr90.muabet.ui.thread

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alexhr90.muabet.R
import com.alexhr90.muabet.data.repo.ForumRepository
import com.alexhr90.muabet.data.repo.LibraryRepository
import com.alexhr90.muabet.data.repo.Loaded
import com.alexhr90.muabet.data.repo.ReadingRepository
import com.alexhr90.muabet.data.repo.SaveResult
import com.alexhr90.muabet.data.repo.SavedRepository
import com.alexhr90.muabet.domain.filter.MuteRules
import com.alexhr90.muabet.domain.model.ThreadPage
import com.alexhr90.muabet.domain.url.ForumUrls
import com.alexhr90.muabet.domain.url.Route
import com.alexhr90.muabet.ui.common.UiMessage
import com.alexhr90.muabet.ui.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
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

/** What the screen knows about the thread once its first page loaded. */
data class ThreadInfo(
    val threadId: Long,
    val slug: String,
    val title: String,
    val forumTitle: String?,
    val lastPage: Int,
    /** The page the pager opens at (1-based). */
    val startPage: Int,
    val canReply: Boolean,
    val watched: Boolean,
)

/** Where to scroll once [page] is on screen: the post with [postId]. */
data class ScrollTarget(val page: Int, val postId: Long)

/**
 * One thread, page by page, for the swipeable pager (SPEC §4 M1): any page of a 1,000+ page
 * thread opens directly, neighbouring pages load when swiped to, and the position (page and first
 * post on screen) is remembered per thread.
 */
@HiltViewModel
class ThreadViewModel @Inject constructor(
    private val saved: SavedStateHandle,
    private val forum: ForumRepository,
    private val reading: ReadingRepository,
    private val library: LibraryRepository,
    private val savedThreads: SavedRepository,
) : ViewModel() {
    val initialTitle: String = saved.get<String>("title").orEmpty()

    private val _info = MutableStateFlow<UiState<ThreadInfo>>(UiState.Loading)
    val info: StateFlow<UiState<ThreadInfo>> = _info.asStateFlow()

    private val _pages = MutableStateFlow<Map<Int, UiState<ThreadPage>>>(emptyMap())
    val pages: StateFlow<Map<Int, UiState<ThreadPage>>> = _pages.asStateFlow()

    private val _refreshing = MutableStateFlow<Int?>(null)
    val refreshing: StateFlow<Int?> = _refreshing.asStateFlow()

    private val _target = MutableStateFlow<ScrollTarget?>(null)
    val target: StateFlow<ScrollTarget?> = _target.asStateFlow()

    /** Pages the pager should move to (after a reply or a link into the same thread). */
    private val _jumps = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    val jumps: SharedFlow<Int> = _jumps.asSharedFlow()

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    val rules: StateFlow<MuteRules> = library.rules.stateIn(viewModelScope, SharingStarted.Eagerly, MuteRules.None)

    val loggedIn: StateFlow<Boolean> = forum.session.map { it?.loggedIn == true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val muted: StateFlow<Boolean> = library.mutedThreads.map { list -> list.any { it.id == threadId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val threadId: Long get() = saved.get<Long>("id") ?: -1L
    val slug: String get() = saved.get<String>("slug").orEmpty()

    /** The page on screen; kept in the saved state so a recreated screen returns to it. */
    var currentPage: Int
        get() = saved.get<Int>("page")?.takeIf { it > 0 } ?: 1
        private set(value) {
            saved["page"] = value
        }

    private val jobs = HashMap<Int, Job>()
    private var openJob: Job? = null

    init {
        open()
    }

    /** Resolves where to open: a link, the remembered position, or the page asked for. */
    fun open() {
        openJob?.cancel()
        _info.value = UiState.Loading
        openJob = viewModelScope.launch {
            val at = saved.get<String>("at")?.takeIf { it.isNotBlank() && saved.get<Boolean>("atResolved") != true }
            if (at != null) {
                openAt(at)
                return@launch
            }
            val asked = saved.get<Int>("page") ?: 1
            var page = asked.coerceAtLeast(1)
            var post: Long? = null
            // 0 means "where I stopped": the remembered page and post.
            if (asked == 0 && saved.get<Boolean>("resumed") != true) {
                reading.position(threadId)?.let { pos ->
                    page = pos.page
                    post = pos.postId
                }
                saved["resumed"] = true
            }
            val r = forum.thread(slug, threadId, page)
            settle(r, page, post)
        }
    }

    private suspend fun openAt(url: String) {
        val r = forum.threadAt(url)
        val postId = (ForumUrls.route(url) as? Route.Post)?.id
        if (r is Loaded.Ok) {
            saved["atResolved"] = true
            saved["id"] = r.value.threadId
            saved["slug"] = r.value.slug
        }
        val page = (r as? Loaded.Ok)?.value?.page?.current ?: 1
        val target = postId ?: (r as? Loaded.Ok)?.value?.posts?.firstOrNull { it.unread }?.id
        settle(r, page, target)
    }

    private fun settle(r: Loaded<ThreadPage>, page: Int, postId: Long?) {
        if (r is Loaded.Ok) {
            val tp = r.value
            val p = tp.page.current
            currentPage = p
            _pages.value = mapOf(p to UiState.Done(r))
            _info.value = UiState.Done(Loaded.Ok(tp.toInfo(startPage = p)))
            if (postId != null && tp.posts.any { it.id == postId }) _target.value = ScrollTarget(p, postId)
            rememberPosition(p, postId ?: tp.posts.firstOrNull()?.id)
        } else {
            _info.value = UiState.Done(
                when (r) {
                    is Loaded.Blocked -> r
                    is Loaded.Offline -> r
                    is Loaded.Failed -> r
                    is Loaded.Ok -> error("handled above")
                },
            )
        }
    }

    private fun ThreadPage.toInfo(startPage: Int) = ThreadInfo(
        threadId = threadId,
        slug = slug,
        title = title,
        forumTitle = forumTitle,
        lastPage = page.last.coerceAtLeast(startPage),
        startPage = startPage,
        canReply = canReply,
        watched = watched,
    )

    /** The pager settled on [page]: load it if needed, keep its neighbours' memory small. */
    fun onPageShown(page: Int) {
        currentPage = page
        ensure(page)
        // Only pages near the one on screen stay in memory.
        val keep = (page - 2)..(page + 2)
        val trimmed = _pages.value.filterKeys { it in keep }
        if (trimmed.size != _pages.value.size) _pages.value = trimmed
    }

    /** Starts loading [page] unless it is loaded or loading. */
    fun ensure(page: Int) {
        if (_pages.value.containsKey(page) || jobs[page]?.isActive == true) return
        load(page)
    }

    fun retry(page: Int) = load(page)

    /** Pull to refresh on a page: new posts and a new last page. */
    fun refresh(page: Int) {
        _refreshing.value = page
        load(page, keepOnFailure = true)
    }

    private fun load(page: Int, keepOnFailure: Boolean = false) {
        val info = (_info.value as? UiState.Done)?.result as? Loaded.Ok ?: return
        jobs[page]?.cancel()
        if (!keepOnFailure) _pages.value = _pages.value + (page to UiState.Loading)
        jobs[page] = viewModelScope.launch {
            val r = forum.thread(info.value.slug, info.value.threadId, page)
            if (!(keepOnFailure && r !is Loaded.Ok)) _pages.value = _pages.value + (page to UiState.Done(r))
            if (r is Loaded.Ok && r.value.page.last > info.value.lastPage) {
                val now = (_info.value as? UiState.Done)?.result as? Loaded.Ok
                if (now != null) _info.value = UiState.Done(Loaded.Ok(now.value.copy(lastPage = r.value.page.last)))
            }
            if (_refreshing.value == page) _refreshing.value = null
        }
    }

    /** The first post on screen changed: remember it (debounced by the screen). */
    fun rememberPosition(page: Int, postId: Long?) {
        val info = ((_info.value as? UiState.Done)?.result as? Loaded.Ok)?.value ?: return
        viewModelScope.launch { reading.save(info.threadId, info.slug, info.title, page, info.lastPage, postId) }
    }

    fun consumeTarget() {
        _target.value = null
    }

    /** A link into a thread (after a reply, from a quote): jump there if it is this thread, else reopen. */
    fun openLink(url: String) {
        val route = ForumUrls.route(url)
        val info = ((_info.value as? UiState.Done)?.result as? Loaded.Ok)?.value
        viewModelScope.launch {
            val r = forum.threadAt(url)
            if (r !is Loaded.Ok) {
                _messages.tryEmit(UiMessage(R.string.msg_link_failed))
                return@launch
            }
            val tp = r.value
            val postId = (route as? Route.Post)?.id ?: tp.posts.lastOrNull()?.id
            if (info != null && tp.threadId == info.threadId) {
                val p = tp.page.current
                _pages.value = _pages.value + (p to UiState.Done(r))
                if (tp.page.last != info.lastPage) _info.value = UiState.Done(Loaded.Ok(info.copy(lastPage = maxOf(tp.page.last, p))))
                if (postId != null) _target.value = ScrollTarget(p, postId)
                _jumps.tryEmit(p)
            } else {
                saved["id"] = tp.threadId
                saved["slug"] = tp.slug
                saved["atResolved"] = true
                _pages.value = emptyMap()
                settle(r, tp.page.current, postId)
            }
        }
    }

    fun toggleMute() {
        val info = ((_info.value as? UiState.Done)?.result as? Loaded.Ok)?.value ?: return
        viewModelScope.launch {
            if (muted.value) {
                library.unmuteThread(info.threadId)
                _messages.tryEmit(UiMessage(R.string.msg_thread_unmuted))
            } else {
                library.muteThread(info.threadId, info.title)
                _messages.tryEmit(UiMessage(R.string.msg_thread_muted, undo = { viewModelScope.launch { library.unmuteThread(info.threadId) } }))
            }
        }
    }

    /** Saves pages [from]..[to] for offline reading (SPEC §4 M3). */
    fun saveOffline(from: Int, to: Int) {
        val info = ((_info.value as? UiState.Done)?.result as? Loaded.Ok)?.value ?: return
        if (_saving.value) return
        _saving.value = true
        viewModelScope.launch {
            val lo = from.coerceIn(1, info.lastPage)
            val hi = to.coerceIn(lo, info.lastPage)
            val result = savedThreads.save(info.threadId, info.slug, info.title, info.forumTitle, lo, hi)
            _messages.tryEmit(
                when (result) {
                    is SaveResult.Saved -> UiMessage(R.string.msg_saved_pages, result.pages)
                    is SaveResult.Full -> UiMessage(R.string.msg_saved_full, result.pages)
                    is SaveResult.Failed -> UiMessage(R.string.msg_saved_failed, result.pages)
                },
            )
            _saving.value = false
        }
    }
}
