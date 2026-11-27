package com.alexhr90.muabet.ui.thread

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alexhr90.muabet.R
import com.alexhr90.muabet.data.post.PostingRepository
import com.alexhr90.muabet.data.post.Sent
import com.alexhr90.muabet.domain.model.Post
import com.alexhr90.muabet.domain.model.PostAbilities
import com.alexhr90.muabet.domain.model.Reaction
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
    private val posting: PostingRepository,
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

    private val _reactions = MutableStateFlow<List<Reaction>?>(null)
    /** The reactions the forum offers; null until the picker first opens. */
    val reactions: StateFlow<List<Reaction>?> = _reactions.asStateFlow()

    /** Reads the forum's reactions once, for the picker. */
    fun loadReactions(postId: Long) {
        if (_reactions.value != null) return
        viewModelScope.launch { _reactions.value = posting.reactions(postId) }
    }

    /** The page on screen that holds [postId]. */
    private fun pageOf(postId: Long): Int? = _pages.value.entries.firstOrNull { (_, s) ->
        ((s as? UiState.Done)?.result as? Loaded.Ok)?.value?.posts?.any { it.id == postId } == true
    }?.key

    /** Replaces [post] on its page right away, before the forum's answer (undone on failure). */
    private fun patch(postId: Long, change: (Post) -> Post): Pair<Int, ThreadPage>? {
        val page = pageOf(postId) ?: return null
        val tp = ((_pages.value[page] as? UiState.Done)?.result as? Loaded.Ok)?.value ?: return null
        // A post whose action bar could not be read has no abilities: keep the assumed ones the screen shows.
        val assumed = { p: Post -> if (p.can == PostAbilities.None) p.copy(can = PostAbilities(react = true, quote = true, bookmark = true)) else p }
        _pages.value = _pages.value + (page to UiState.Done(Loaded.Ok(tp.copy(posts = tp.posts.map { if (it.id == postId) change(assumed(it)) else it }))))
        return page to tp
    }

    private fun restore(saved: Pair<Int, ThreadPage>?) {
        saved ?: return
        _pages.value = _pages.value + (saved.first to UiState.Done(Loaded.Ok(saved.second)))
    }

    /** Gives [reactionId] to the post, or takes it back when it is already the visitor's. */
    fun react(post: Post, reactionId: Int) {
        val taking = post.can.myReactionId == reactionId
        val before = patch(post.id) { it.copy(can = it.can.copy(myReactionId = if (taking) null else reactionId)) }
        viewModelScope.launch {
            val r = posting.react(post.id, reactionId)
            if (r is Sent.Ok) {
                // The forum's own summary (names, counts) comes with the page.
                before?.first?.let { load(it, keepOnFailure = true) }
            } else {
                restore(before)
                report(r)
            }
        }
    }

    fun toggleBookmark(post: Post) {
        val remove = post.can.bookmarked
        val before = patch(post.id) { it.copy(can = it.can.copy(bookmarked = !remove)) }
        viewModelScope.launch {
            val r = posting.bookmark(post.id, remove)
            if (r is Sent.Ok) _messages.tryEmit(UiMessage(if (remove) R.string.msg_bookmark_removed else R.string.msg_bookmarked)) else {
                restore(before)
                report(r)
            }
        }
    }

    fun toggleWatch() {
        val info = ((_info.value as? UiState.Done)?.result as? Loaded.Ok)?.value ?: return
        val stop = info.watched
        _info.value = UiState.Done(Loaded.Ok(info.copy(watched = !stop)))
        viewModelScope.launch {
            val r = posting.watch(info.slug, info.threadId, stop)
            if (r is Sent.Ok) _messages.tryEmit(UiMessage(if (stop) R.string.msg_unwatched else R.string.msg_watching)) else {
                val now = ((_info.value as? UiState.Done)?.result as? Loaded.Ok)?.value
                if (now != null) _info.value = UiState.Done(Loaded.Ok(now.copy(watched = stop)))
                report(r)
            }
        }
    }

    fun delete(post: Post, reason: String) {
        viewModelScope.launch {
            val r = posting.delete(post.id, reason)
            if (r is Sent.Ok) {
                _messages.tryEmit(UiMessage(R.string.msg_post_deleted))
                pageOf(post.id)?.let { load(it, keepOnFailure = true) }
            } else {
                report(r)
            }
        }
    }

    fun reportPost(post: Post, reason: String) {
        viewModelScope.launch {
            val r = posting.report(post.id, reason)
            if (r is Sent.Ok) _messages.tryEmit(UiMessage(R.string.msg_reported)) else report(r)
        }
    }

    /** Says why a quick action (reaction, bookmark, watch…) did not go through. */
    private fun report(r: Sent) {
        if (r is Sent.Refused && r.errors.isNotEmpty()) {
            _errors.tryEmit(r.errors.joinToString("\n"))
            return
        }
        _messages.tryEmit(
            UiMessage(
                when (r) {
                    Sent.Offline -> R.string.msg_action_offline
                    Sent.NeedsLogin -> R.string.compose_needs_login
                    is Sent.Blocked -> R.string.error_blocked_title
                    else -> R.string.msg_action_failed
                },
            ),
        )
    }

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 2)
    /** The forum's own words when it refused a quick action. */
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    /** A short message in the screen's message bar. */
    fun say(text: Int) {
        _messages.tryEmit(UiMessage(text))
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
