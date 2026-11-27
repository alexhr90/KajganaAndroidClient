package com.alexhr90.muabet.ui.messages

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alexhr90.muabet.data.repo.ForumRepository
import com.alexhr90.muabet.data.repo.Loaded
import com.alexhr90.muabet.domain.model.ConversationList
import com.alexhr90.muabet.domain.model.ConversationPage
import com.alexhr90.muabet.domain.model.ConversationSummary
import com.alexhr90.muabet.domain.model.Post
import com.alexhr90.muabet.domain.model.SearchPage
import com.alexhr90.muabet.domain.model.SearchResult
import com.alexhr90.muabet.domain.url.ForumUrls
import com.alexhr90.muabet.ui.common.UiState
import com.alexhr90.muabet.ui.lists.EndlessViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Direct messages, newest first (v1.1: native, no web page). */
@HiltViewModel
class ConversationsViewModel @Inject constructor(forum: ForumRepository) :
    EndlessViewModel<ConversationSummary, ConversationList>(forum, needsLogin = true) {
    override val firstUrl: String = ForumUrls.conversations
    override suspend fun fetch(url: String) = forum.conversations(url)
    override fun itemsOf(page: ConversationList) = page.conversations
    override fun nextOf(page: ConversationList) = page.nextUrl
    override fun idOf(item: ConversationSummary) = item.id

    init {
        start()
    }
}

/** The visitor's bookmarks: posts and threads they kept. */
@HiltViewModel
class BookmarksViewModel @Inject constructor(forum: ForumRepository) :
    EndlessViewModel<SearchResult, SearchPage>(forum, needsLogin = true) {
    override val firstUrl: String = ForumUrls.bookmarks
    override suspend fun fetch(url: String) = forum.bookmarks(url)
    override fun itemsOf(page: SearchPage) = page.results
    override fun nextOf(page: SearchPage) = page.nextUrl
    override fun idOf(item: SearchResult) = item.postId ?: item.url.hashCode().toLong()

    init {
        start()
    }
}

/** A conversation so far: its pages from [firstPage] to the last, as one list of messages. */
data class ConversationView(
    val id: Long,
    val slug: String,
    val title: String,
    val participants: List<String>,
    val messages: List<Post>,
    val firstPage: Int,
    val lastPage: Int,
    val canReply: Boolean,
    val loadingEarlier: Boolean = false,
)

/**
 * One conversation: it opens at the first unread message, earlier pages load on demand, and it
 * reloads to the newest message after a reply.
 */
@HiltViewModel
class ConversationViewModel @Inject constructor(
    private val saved: SavedStateHandle,
    private val forum: ForumRepository,
) : ViewModel() {
    private val id: Long = saved.get<Long>("id") ?: 0L
    private val slug: String = saved.get<String>("slug").orEmpty()
    val initialTitle: String = saved.get<String>("title").orEmpty()

    private val _state = MutableStateFlow<UiState<ConversationView>>(UiState.Loading)
    val state: StateFlow<UiState<ConversationView>> = _state.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    /** The message to scroll to once loaded (the first unread, or the newest after a reply). */
    private val _target = MutableStateFlow<Long?>(null)
    val target: StateFlow<Long?> = _target.asStateFlow()

    val loggedIn: StateFlow<Boolean> = forum.session.map { it?.loggedIn == true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        load(ForumUrls.conversation(slug, id) + "unread", unread = true)
    }

    fun retry() = load(ForumUrls.conversation(slug, id) + "unread", unread = true)

    /** After a reply or a pull: the newest page, scrolled to its last message. */
    fun refresh() {
        _refreshing.value = true
        load(ForumUrls.conversation(slug, id) + "latest", unread = false, pull = true)
    }

    private fun load(url: String, unread: Boolean, pull: Boolean = false) {
        viewModelScope.launch {
            if (!pull && _state.value !is UiState.Done) _state.value = UiState.Loading
            val r = forum.conversation(url)
            if (r is Loaded.Ok) {
                val p = r.value
                val view = p.toView()
                _state.value = UiState.Done(Loaded.Ok(view))
                _target.value = if (unread) p.messages.firstOrNull { it.unread }?.id ?: p.messages.lastOrNull()?.id else p.messages.lastOrNull()?.id
            } else if (!(pull && _state.value is UiState.Done)) {
                _state.value = UiState.Done(
                    when (r) {
                        is Loaded.Blocked -> r
                        is Loaded.Offline -> r
                        is Loaded.Failed -> r
                        is Loaded.Ok -> error("handled")
                    },
                )
            }
            _refreshing.value = false
        }
    }

    private fun ConversationPage.toView() = ConversationView(
        id = id,
        slug = slug,
        title = title,
        participants = participants,
        messages = messages,
        firstPage = page.current,
        lastPage = page.last,
        canReply = canReply,
    )

    /** The page before the first one shown, put above it. */
    fun loadEarlier() {
        val v = ((_state.value as? UiState.Done)?.result as? Loaded.Ok)?.value ?: return
        if (v.firstPage <= 1 || v.loadingEarlier) return
        _state.value = UiState.Done(Loaded.Ok(v.copy(loadingEarlier = true)))
        viewModelScope.launch {
            val r = forum.conversation(ForumUrls.conversation(v.slug, v.id, v.firstPage - 1))
            val now = ((_state.value as? UiState.Done)?.result as? Loaded.Ok)?.value ?: return@launch
            _state.value = UiState.Done(
                Loaded.Ok(
                    if (r is Loaded.Ok) {
                        val seen = now.messages.mapTo(HashSet()) { it.id }
                        now.copy(messages = r.value.messages.filter { it.id !in seen } + now.messages, firstPage = r.value.page.current, loadingEarlier = false)
                    } else {
                        now.copy(loadingEarlier = false)
                    },
                ),
            )
        }
    }

    fun consumeTarget() {
        _target.value = null
    }
}
