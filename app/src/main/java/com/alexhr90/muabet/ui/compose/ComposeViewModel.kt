package com.alexhr90.muabet.ui.compose

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alexhr90.muabet.data.post.AttachTo
import com.alexhr90.muabet.data.post.Attached
import com.alexhr90.muabet.data.post.Draft
import com.alexhr90.muabet.data.post.DraftStore
import com.alexhr90.muabet.data.post.EditSource
import com.alexhr90.muabet.data.post.PostingRepository
import com.alexhr90.muabet.data.post.Sent
import com.alexhr90.muabet.domain.url.ForumUrls
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject

/** What is being written. */
enum class ComposeKind { Reply, Thread, Edit, Message, NewMessage;

    companion object {
        fun of(name: String?): ComposeKind = entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: Reply
    }
}

/** Why a send did not go through, for the screen to explain. */
sealed interface ComposeProblem {
    data class Refused(val errors: List<String>) : ComposeProblem
    data object Offline : ComposeProblem
    data class Blocked(val url: String) : ComposeProblem
    data object NeedsLogin : ComposeProblem
    data object Empty : ComposeProblem
    data class Upload(val message: String?) : ComposeProblem
    /** The forum did not hand over the post to edit (or the quote). */
    data object SourceFailed : ComposeProblem
}

/**
 * The native compose screen (v1.1): a reply (with a quote), a new thread, an edit, a direct
 * message or a reply to one. The text is BB code, kept as a draft on the phone until it is sent;
 * photos upload to the forum and go in as attachments.
 */
@HiltViewModel
class ComposeViewModel @Inject constructor(
    saved: SavedStateHandle,
    private val posting: PostingRepository,
    private val drafts: DraftStore,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    val kind: ComposeKind = ComposeKind.of(saved.get<String>("kind"))
    /** The thread (reply, edit), forum (new thread) or conversation (message). */
    private val targetId: Long = saved.get<Long>("id") ?: 0L
    private val slug: String = saved.get<String>("slug").orEmpty()
    /** What the screen is about: the thread's, forum's or conversation's title. */
    val contextTitle: String = saved.get<String>("title").orEmpty()
    /** The quoted post (reply) or the post being edited. */
    private val postId: Long = saved.get<Long>("post") ?: 0L
    private val draftKey = "${kind.name.lowercase()}:$targetId:$postId"

    var text by mutableStateOf(TextFieldValue(""))
        private set
    var title by mutableStateOf("")
        private set
    var recipients by mutableStateOf(saved.get<String>("to").orEmpty())
        private set
    var attachments by mutableStateOf<List<Attached>>(emptyList())
        private set
    var uploading by mutableIntStateOf(0)
        private set
    var sending by mutableStateOf(false)
        private set
    var preparing by mutableStateOf(kind == ComposeKind.Edit || postId > 0)
        private set
    var problem by mutableStateOf<ComposeProblem?>(null)
        private set

    private val _done = MutableSharedFlow<String?>(extraBufferCapacity = 1)
    /** Sent: the address of the new post or page (null when the forum gave none). */
    val done: SharedFlow<String?> = _done.asSharedFlow()

    private var editSource: EditSource? = null
    private var hash: String? = null
    private var saveJob: Job? = null

    val needsTitle: Boolean get() = kind == ComposeKind.Thread || kind == ComposeKind.NewMessage
    val needsRecipients: Boolean get() = kind == ComposeKind.NewMessage
    /** Photos can be added except to an edit whose form gave no attachment hash. */
    val canAttach: Boolean get() = kind != ComposeKind.Edit || editSource?.attachmentHash != null

    init {
        viewModelScope.launch {
            val draft = if (kind == ComposeKind.Edit) null else drafts.get(draftKey)
            if (draft != null) {
                title = draft.title
                if (draft.to.isNotBlank()) recipients = draft.to
                setText(draft.text)
                preparing = false
                return@launch
            }
            when {
                kind == ComposeKind.Edit -> {
                    val source = posting.editSource(postId)
                    if (source == null) {
                        problem = ComposeProblem.SourceFailed
                    } else {
                        editSource = source
                        hash = source.attachmentHash
                        setText(source.text)
                    }
                }
                postId > 0 -> {
                    val quote = posting.quote(postId)
                    if (quote == null) problem = ComposeProblem.SourceFailed else setText(quote.trimEnd() + "\n\n")
                }
            }
            preparing = false
        }
    }

    private fun setText(value: String) {
        text = TextFieldValue(value, TextRange(value.length))
    }

    fun onText(value: TextFieldValue) {
        text = value
        dirty()
    }

    fun onTitle(value: String) {
        title = value.take(150)
        dirty()
    }

    fun onRecipients(value: String) {
        recipients = value
        dirty()
    }

    /** Wraps the selection in [open] and [close], or puts both at the cursor with the cursor between. */
    fun wrap(open: String, close: String) {
        val v = text
        val start = v.selection.min
        val end = v.selection.max
        val selected = v.text.substring(start, end)
        val next = v.text.substring(0, start) + open + selected + close + v.text.substring(end)
        val cursor = if (selected.isEmpty()) start + open.length else start + open.length + selected.length + close.length
        onText(TextFieldValue(next, TextRange(cursor)))
    }

    /** Inserts [insert] at the cursor (replacing the selection). */
    fun insert(insert: String) {
        val v = text
        val start = v.selection.min
        val end = v.selection.max
        val next = v.text.substring(0, start) + insert + v.text.substring(end)
        onText(TextFieldValue(next, TextRange(start + insert.length)))
    }

    /** The selected text, for a link's label. */
    fun selected(): String = text.text.substring(text.selection.min, text.selection.max)

    private fun dirty() {
        problem = (problem as? ComposeProblem.SourceFailed)
        if (kind == ComposeKind.Edit) return
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(600)
            drafts.put(draftKey, Draft(title = title, to = recipients, text = text.text))
        }
    }

    /** Saves the draft right away (leaving the screen). */
    @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
    fun saveNow() {
        if (kind == ComposeKind.Edit || sending) return
        saveJob?.cancel()
        val draft = Draft(title = title, to = recipients, text = text.text)
        // Outlives the screen: the draft must reach the disk.
        kotlinx.coroutines.GlobalScope.launch { drafts.put(draftKey, draft) }
    }

    fun dismissProblem() {
        problem = null
    }

    /** Uploads a photo the user picked and puts it in the text. */
    fun attach(uri: Uri) {
        if (!canAttach) return
        val h = hash ?: posting.newHash().also { hash = it }
        uploading++
        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) { readImage(uri) }
            val result = if (file == null) {
                Result.failure(IllegalStateException(null as String?))
            } else {
                posting.upload(h, attachTarget(), file.first, file.second, file.third)
            }
            uploading--
            result.onSuccess { a ->
                attachments = attachments + a
                insert((if (text.text.isEmpty() || text.text.endsWith("\n")) "" else "\n") + "[ATTACH=full]${a.id}[/ATTACH]\n")
            }.onFailure { problem = ComposeProblem.Upload(it.message) }
        }
    }

    /** Takes a photo out of the message (its upload stays unused on the forum and expires). */
    fun detach(a: Attached) {
        attachments = attachments - a
        val cleaned = text.text.replace(Regex("\\[ATTACH(=[a-z]+)?]${a.id}\\[/ATTACH]\\n?", RegexOption.IGNORE_CASE), "")
        onText(TextFieldValue(cleaned, TextRange(cleaned.length)))
    }

    private fun attachTarget(): AttachTo = when (kind) {
        ComposeKind.Reply, ComposeKind.Edit -> AttachTo.Thread(targetId)
        ComposeKind.Thread -> AttachTo.Forum(targetId)
        ComposeKind.Message -> AttachTo.Conversation(targetId)
        ComposeKind.NewMessage -> AttachTo.Conversation(null)
    }

    fun send() {
        if (sending || preparing || uploading > 0) return
        val body = text.text.trim()
        if (body.isEmpty() || (needsTitle && title.isBlank()) || (needsRecipients && recipients.isBlank())) {
            problem = ComposeProblem.Empty
            return
        }
        sending = true
        problem = null
        viewModelScope.launch {
            val h = hash?.takeIf { attachments.isNotEmpty() }
            val r = when (kind) {
                ComposeKind.Reply -> posting.reply(slug, targetId, body, h)
                ComposeKind.Thread -> posting.newThread(slug, targetId, title.trim(), body, h)
                ComposeKind.Edit -> editSource?.let { posting.saveEdit(postId, body, it) } ?: Sent.Refused(emptyList())
                ComposeKind.Message -> posting.conversationReply(slug, targetId, body, h)
                ComposeKind.NewMessage -> posting.newConversation(recipients.trim(), title.trim(), body, h)
            }
            sending = false
            when (r) {
                is Sent.Ok -> {
                    saveJob?.cancel()
                    drafts.clear(draftKey)
                    _done.tryEmit(r.redirect ?: fallback())
                }
                is Sent.Refused -> problem = ComposeProblem.Refused(r.errors)
                Sent.Offline -> problem = ComposeProblem.Offline
                is Sent.Blocked -> problem = ComposeProblem.Blocked(r.url)
                Sent.NeedsLogin -> problem = ComposeProblem.NeedsLogin
            }
        }
    }

    /** Where to go after sending when the forum did not say. */
    private fun fallback(): String? = when (kind) {
        ComposeKind.Reply -> ForumUrls.threadLatest(slug, targetId)
        ComposeKind.Edit -> ForumUrls.post(postId)
        ComposeKind.Thread -> ForumUrls.forum(slug, targetId)
        ComposeKind.Message -> ForumUrls.conversation(slug, targetId) + "latest"
        ComposeKind.NewMessage -> ForumUrls.conversations
    }

    /**
     * The picked photo as (name, type, bytes). Large photos are scaled to 2048 px on the long
     * side and saved as JPEG, so uploads stay small; GIFs go as they are.
     */
    private fun readImage(uri: Uri): Triple<String, String, ByteArray>? = runCatching {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: "image/jpeg"
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: "photo.jpg"
        val raw = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        if (mime == "image/gif" || raw.size < 600_000) return Triple(name, mime, raw)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val bitmap = BitmapFactory.decodeByteArray(raw, 0, raw.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return Triple(name, mime, raw)
        val scale = MAX_SIDE.toFloat() / maxOf(bitmap.width, bitmap.height)
        val scaled = if (scale < 1f) Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true) else bitmap
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
        Triple(name.substringBeforeLast('.') + ".jpg", "image/jpeg", out.toByteArray())
    }.getOrNull()

    private companion object {
        const val MAX_SIDE = 2048
    }
}

/** What the compose screen opens for: see the route's arguments in `Routes.compose`. */
data class ComposeTarget(
    val kind: ComposeKind,
    val id: Long = 0,
    val slug: String = "",
    val title: String = "",
    val post: Long = 0,
    val to: String = "",
)
