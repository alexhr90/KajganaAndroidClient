package com.alexhr90.muabet.data.post

import com.alexhr90.muabet.data.remote.FetchResult
import com.alexhr90.muabet.data.remote.ForumFetcher
import com.alexhr90.muabet.data.remote.Upload
import com.alexhr90.muabet.data.repo.ForumRepository
import com.alexhr90.muabet.domain.model.FormResult
import com.alexhr90.muabet.domain.model.Reaction
import com.alexhr90.muabet.domain.parse.BbCode
import com.alexhr90.muabet.domain.parse.FormFields
import com.alexhr90.muabet.domain.parse.SessionParser
import com.alexhr90.muabet.domain.parse.XfJson
import com.alexhr90.muabet.domain.url.ForumUrls
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/** How a form the app sent went. */
sealed interface Sent {
    /** Done. [redirect] is the new post or page, [data] the forum's other answers (a quote, an attachment id). */
    data class Ok(val redirect: String?, val message: String?, val data: Map<String, String> = emptyMap()) : Sent
    /** The forum said no, in its own words. */
    data class Refused(val errors: List<String>) : Sent
    data object Offline : Sent
    /** A bot check or block: never worked around; the user sees the page in the WebView. */
    data class Blocked(val url: String) : Sent
    /** No session (or it expired): the user needs to log in on the forum's page. */
    data object NeedsLogin : Sent
}

/** A post's text as the edit form had it, with the form's other fields to send back. */
data class EditSource(val text: String, val fields: List<Pair<String, String>>, val attachmentHash: String?)

/** Where an uploaded file belongs: a reply, a new thread, a message. */
sealed interface AttachTo {
    data class Thread(val id: Long) : AttachTo
    data class Forum(val id: Long) : AttachTo
    data class Conversation(val id: Long?) : AttachTo
}

/** A file on the forum, ready to go into a message as `[ATTACH=full]id[/ATTACH]`. */
data class Attached(val id: Long, val name: String, val thumbnail: String?)

/**
 * Posting without leaving the app (v1.1): replies, quotes, new threads, edits, reactions,
 * bookmarks, watching, reports, deletes, direct messages and photo uploads. Every form goes to
 * the forum's own endpoint with the session's form token and `_xfResponseType=json`, exactly as
 * the forum's pages send it with JavaScript; an expired token is renewed once.
 */
@Singleton
class PostingRepository @Inject constructor(
    private val fetcher: ForumFetcher,
    private val forum: ForumRepository,
) {
    private var reactionCache: List<Reaction>? = null

    suspend fun reply(slug: String, threadId: Long, message: String, hash: String?): Sent =
        send(ForumUrls.addReply(slug, threadId), listOf("message" to message) + attachmentFields(hash, AttachTo.Thread(threadId)))

    suspend fun newThread(forumSlug: String, forumId: Long, title: String, message: String, hash: String?): Sent =
        send(
            ForumUrls.postThread(forumSlug, forumId),
            listOf("title" to title, "message" to message, "discussion_type" to "discussion") + attachmentFields(hash, AttachTo.Forum(forumId)),
        )

    /** The forum's own quote of a post, as BB code. */
    suspend fun quote(postId: Long): String? = (send(ForumUrls.postQuote(postId), emptyList()) as? Sent.Ok)?.let { BbCode.quoteFromJson(it.data) }

    /** The post's text and form, for editing. Null when the forum did not hand over a form. */
    suspend fun editSource(postId: Long): EditSource? {
        val r = fetcher.getJson(ForumUrls.editPost(postId)) as? FetchResult.Page ?: return null
        val html = XfJson.html(r.html) ?: r.html
        val text = BbCode.fromEditForm(html) ?: return null
        val fields = FormFields.collect(html, "form[action*=/edit]")
        return EditSource(text, fields, FormFields.attachmentHash(fields))
    }

    suspend fun saveEdit(postId: Long, message: String, source: EditSource): Sent =
        send(ForumUrls.editPost(postId), source.fields.filter { it.first != "attachment_hash_combined" } + ("message" to message))

    suspend fun delete(postId: Long, reason: String): Sent =
        send(ForumUrls.deletePost(postId), listOf("hard_delete" to "0", "reason" to reason))

    suspend fun report(postId: Long, message: String): Sent =
        send(ForumUrls.reportPost(postId), listOf("message" to message))

    /** Gives or takes back a reaction: the forum toggles it. */
    suspend fun react(postId: Long, reactionId: Int): Sent = send(ForumUrls.reactTo(postId, reactionId), emptyList())

    /** The reactions the forum offers, read once from its picker; XenForo's six defaults otherwise. */
    suspend fun reactions(postId: Long): List<Reaction> {
        reactionCache?.let { return it }
        val r = fetcher.getJson(ForumUrls.react(postId)) as? FetchResult.Page
        val list = r?.let { XfJson.reactions(XfJson.html(it.html) ?: it.html) }.orEmpty()
        return if (list.isEmpty()) DEFAULT_REACTIONS else list.also { reactionCache = it }
    }

    suspend fun bookmark(postId: Long, remove: Boolean): Sent =
        send(ForumUrls.bookmarkPost(postId), if (remove) listOf("delete" to "1") else listOf("message" to ""))

    suspend fun watch(slug: String, threadId: Long, stop: Boolean): Sent =
        send(ForumUrls.watchThread(slug, threadId), if (stop) listOf("stop" to "1") else listOf("email_subscribe" to "0"))

    suspend fun conversationReply(slug: String, id: Long, message: String, hash: String?): Sent =
        send(ForumUrls.conversationReply(slug, id), listOf("message" to message) + attachmentFields(hash, AttachTo.Conversation(id)))

    suspend fun newConversation(recipients: String, title: String, message: String, hash: String?): Sent =
        send(
            ForumUrls.newConversation,
            listOf("recipients" to recipients, "title" to title, "message" to message, "open_invite" to "0", "conversation_locked" to "0") +
                attachmentFields(hash, AttachTo.Conversation(null)),
        )

    /** Uploads a photo for a message being written; [hash] ties it to that message. */
    suspend fun upload(hash: String, to: AttachTo, fileName: String, mime: String, bytes: ByteArray): Result<Attached> {
        val url = when (to) {
            is AttachTo.Thread -> ForumUrls.attachmentUpload(hash, threadId = to.id)
            is AttachTo.Forum -> ForumUrls.attachmentUpload(hash, forumId = to.id)
            is AttachTo.Conversation -> ForumUrls.attachmentUpload(hash, conversationId = to.id)
        }
        return when (val r = send(url, emptyList(), Upload("upload", fileName, mime, bytes))) {
            is Sent.Ok -> {
                val id = (r.data["attachment.attachment_id"] ?: r.data["attachment_id"])?.toLongOrNull()
                    ?: return Result.failure(IllegalStateException(r.message ?: "upload"))
                Result.success(Attached(id, r.data["attachment.filename"] ?: fileName, r.data["attachment.thumbnail_url"]?.let { ForumUrls.absolute(it) }))
            }
            is Sent.Refused -> Result.failure(IllegalStateException(r.errors.joinToString("\n")))
            else -> Result.failure(IllegalStateException(r.toString()))
        }
    }

    /** A new attachment hash: XenForo's are 32 hex characters chosen by the page. */
    fun newHash(): String {
        val bytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun attachmentFields(hash: String?, to: AttachTo): List<Pair<String, String>> {
        if (hash == null) return emptyList()
        val (type, context) = when (to) {
            is AttachTo.Thread -> "post" to """{"thread_id":${to.id}}"""
            is AttachTo.Forum -> "post" to """{"node_id":${to.id}}"""
            is AttachTo.Conversation -> "conversation_message" to (to.id?.let { """{"conversation_id":$it}""" } ?: "[]")
        }
        return listOf("attachment_hash" to hash, "attachment_hash_combined" to """{"type":"$type","context":$context,"hash":"$hash"}""")
    }

    private suspend fun token(fresh: Boolean): String? {
        if (!fresh) forum.session.value?.csrf?.let { return it }
        val r = fetcher.get(ForumUrls.home) as? FetchResult.Page ?: return null
        val info = withContext(Dispatchers.Default) { SessionParser.parse(r.html, r.url) }
        forum.noteSession(info)
        return info.csrf.takeIf { info.loggedIn }
    }

    private suspend fun send(url: String, fields: List<Pair<String, String>>, upload: Upload? = null): Sent {
        for (attempt in 0..1) {
            val token = token(fresh = attempt == 1) ?: return if (forum.session.value?.loggedIn == false) Sent.NeedsLogin else Sent.Offline
            val all = fields + listOf("_xfToken" to token, "_xfResponseType" to "json", "_xfRequestUri" to "/")
            val result = when (val r = fetcher.send(url, all, upload)) {
                is FetchResult.Page -> withContext(Dispatchers.Default) { XfJson.parse(r.html) }
                is FetchResult.Blocked -> return Sent.Blocked(r.url)
                is FetchResult.NetworkError -> return Sent.Offline
                is FetchResult.HttpError -> return Sent.Refused(listOf("HTTP ${r.code}"))
            }
            when (result) {
                is FormResult.Ok -> return Sent.Ok(result.redirect, result.message, result.data)
                is FormResult.Refused -> return Sent.Refused(result.errors)
                FormResult.TokenExpired -> Unit // A fresh token from a new page, then once more.
            }
        }
        return Sent.NeedsLogin
    }

    companion object {
        /** XenForo's default reactions, used when the forum's picker cannot be read. */
        val DEFAULT_REACTIONS = listOf(
            Reaction(1, "Like"),
            Reaction(2, "Love"),
            Reaction(3, "Haha"),
            Reaction(4, "Wow"),
            Reaction(5, "Sad"),
            Reaction(6, "Angry"),
        )
    }
}
