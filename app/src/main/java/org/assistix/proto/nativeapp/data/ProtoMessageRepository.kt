package org.assistix.proto.nativeapp.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.assistix.proto.nativeapp.data.local.MessageEntity
import org.assistix.proto.nativeapp.data.local.OutboxEntity
import org.assistix.proto.nativeapp.data.local.ProtoDao
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class ProtoMessageRepository(
    private val dao: ProtoDao,
    private val api: ProtoApi,
) {
    fun observeConversation(conversationId: Int): Flow<List<MsgItem>> =
        dao.observeMessages(conversationId).map { rows -> rows.map { it.toMsg() } }

    suspend fun refreshFromServer(
        token: String,
        conversationId: Int,
        myUserId: Int,
        sendReadReceipts: Boolean = true,
    ): Boolean =
        withContext(Dispatchers.IO) {
            val since = dao.maxServerId(conversationId)
            val batch = api.messages(token, conversationId, myUserId, since)
            if (batch.isNotEmpty()) {
                dao.upsertMessages(batch.map { mergeServerRow(it, conversationId) })
                dao.dedupeByServerId()
            }
            val maxId = batch.maxOfOrNull { it.id } ?: since
            if (sendReadReceipts && maxId > 0) api.markRead(token, conversationId, maxId)
            val ownMax = dao.maxServerId(conversationId)
            if (ownMax > 0) {
                val patchSince = (ownMax - 80).coerceAtLeast(0)
                val patch = api.messages(token, conversationId, myUserId, patchSince)
                if (patch.isNotEmpty()) {
                    dao.upsertMessages(patch.map { mergeServerRow(it, conversationId) })
                    dao.dedupeByServerId()
                }
            }
            api.lastHttpOk
        }

    suspend fun applyPeerRead(conversationId: Int, throughMessageId: Long, readAtSec: Long) =
        withContext(Dispatchers.IO) {
            if (throughMessageId <= 0) return@withContext
            dao.markReadThrough(conversationId, throughMessageId, true, readAtSec * 1000L)
        }

    suspend fun sendText(
        token: String,
        conversationId: Int,
        myUserId: Int,
        body: String,
        isE2e: Boolean,
        replyTo: ReplyMeta? = null,
    ): MsgItem = sendInternal(token, conversationId, body, isE2e, null, null, null, null, null, replyTo)

    suspend fun sendMedia(token: String, conversationId: Int, uploadId: String, mime: String, name: String, caption: String): MsgItem {
        val kind = mediaKindFromMime(mime, name)
        val body = if (kind == "voice") "" else caption.ifBlank { name }
        return sendInternal(token, conversationId, body, false, uploadId, mime, name, null, kind, null)
    }

    suspend fun sendAlbum(
        token: String,
        conversationId: Int,
        items: List<AlbumItem>,
        caption: String = "",
    ): MsgItem {
        val meta = AlbumMeta(items)
        val body = meta.toJsonBody(caption)
        val first = items.firstOrNull()?.uploadId
        return sendInternal(token, conversationId, body, false, first, "image/jpeg", null, null, "image", null)
    }

    suspend fun sendChannelPost(
        token: String,
        conversationId: Int,
        text: String,
        imageUploadId: String? = null,
    ): Boolean =
        withContext(Dispatchers.IO) {
            val body =
                ChannelPostMeta(
                    text = text.trim(),
                    imageUploadId = imageUploadId?.trim()?.takeIf { it.isNotEmpty() },
                ).toJsonBody()
            if (body.isBlank()) return@withContext false
            val mediaId = imageUploadId?.trim()?.takeIf { it.isNotEmpty() }
            val result = api.sendMessage(token, conversationId, body, false, mediaId)
            result.ok
        }

    suspend fun forwardMessage(
        token: String,
        targetConversationId: Int,
        source: MsgItem,
        fromLabel: String,
        fromUserId: Int,
    ): Boolean =
        withContext(Dispatchers.IO) {
            val fwd =
                ForwardMeta(
                    fromLabel = fromLabel,
                    bodySnippet = source.bodyRaw.take(160),
                    fromUserId = fromUserId,
                    originalMessageId = source.id.takeIf { it > 0 } ?: 0,
                )
            val body = source.bodyRaw.ifBlank { source.body }
            val result = api.sendMessage(token, targetConversationId, body, source.isE2e, source.mediaUploadId, fwd)
            result.ok
        }

    suspend fun saveToSaved(
        token: String,
        source: MsgItem,
        fromLabel: String,
        fromUserId: Int,
    ): Boolean =
        withContext(Dispatchers.IO) {
            val savedId = api.ensureSavedConversation(token) ?: return@withContext false
            forwardMessage(token, savedId, source, fromLabel, fromUserId)
        }

    suspend fun editMessage(token: String, conversationId: Int, messageId: Long, newBody: String): Boolean =
        withContext(Dispatchers.IO) {
            val ok = api.editMessage(token, conversationId, messageId, newBody)
            if (ok) {
                dao.messagesForConversation(conversationId).filter { it.serverId == messageId }.forEach {
                    dao.upsertMessage(it.copy(body = newBody, editedAt = System.currentTimeMillis()))
                }
            }
            ok
        }

    suspend fun toggleReaction(token: String, conversationId: Int, myUserId: Int, messageId: Long, emoji: String): Boolean =
        withContext(Dispatchers.IO) {
            val ok = api.toggleReaction(token, conversationId, messageId, emoji)
            if (ok) refreshFromServer(token, conversationId, myUserId)
            ok
        }

    suspend fun deleteMessage(
        token: String,
        conversationId: Int,
        messageId: Long,
        scope: String = "all",
    ): Boolean =
        withContext(Dispatchers.IO) {
            val ok = api.deleteMessage(token, conversationId, messageId, scope)
            if (ok) {
                dao.deleteByServerId(messageId)
            }
            ok
        }

    suspend fun insertCallLog(token: String, conversationId: Int, meta: CallMeta, mine: Boolean): MsgItem? =
        withContext(Dispatchers.IO) {
            val body = CallMeta.toJson(meta)
            val localId = "call-${UUID.randomUUID()}"
            val now = System.currentTimeMillis()
            val entity =
                MessageEntity(
                    localId = localId,
                    serverId = null,
                    conversationId = conversationId,
                    body = meta.displayText(mine),
                    mine = mine,
                    isE2e = false,
                    createdAt = now,
                    status = "sent",
                    messageType = "call",
                    callJson = body,
                )
            dao.upsertMessage(entity)
            val result = api.sendMessage(token, conversationId, body, false)
            if (result.ok && result.messageId != null) {
                finalizeSent(localId, result.messageId, "sent")
                entity.copy(serverId = result.messageId).toMsg()
            } else {
                entity.toMsg()
            }
        }

    suspend fun retryFailed(localId: String, token: String): Boolean =
        withContext(Dispatchers.IO) {
            val item = dao.outboxAll().find { it.localId == localId } ?: return@withContext false
            dao.updateStatus(localId, "sending")
            val fwd = item.forwardJson?.let { parseForwardJson(it) }
            val result =
                api.sendMessage(
                    token,
                    item.conversationId,
                    item.body,
                    item.isE2e,
                    item.mediaUploadId,
                    fwd,
                    item.replyToId,
                )
            if (result.ok && result.messageId != null) {
                finalizeSent(localId, result.messageId, "sent")
                dao.deleteOutbox(localId)
                true
            } else {
                dao.bumpOutboxRetry(localId)
                dao.updateStatus(localId, if (api.lastHttpOk) "failed" else "queued")
                false
            }
        }

    suspend fun flushOutbox(token: String): Int =
        withContext(Dispatchers.IO) {
            var sent = 0
            for (item in dao.outboxAll()) {
                if (item.retryCount > 15) continue
                val fwd = item.forwardJson?.let { parseForwardJson(it) }
                val result =
                    api.sendMessage(
                        token,
                        item.conversationId,
                        item.body,
                        item.isE2e,
                        item.mediaUploadId,
                        fwd,
                        item.replyToId,
                    )
                if (result.ok && result.messageId != null) {
                    finalizeSent(item.localId, result.messageId, "sent")
                    dao.deleteOutbox(item.localId)
                    sent++
                } else {
                    dao.bumpOutboxRetry(item.localId)
                    dao.updateStatus(item.localId, "queued")
                }
            }
            sent
        }

    suspend fun pendingOutboxCount(): Int = withContext(Dispatchers.IO) { dao.outboxCount() }

    suspend fun clearAll() {
        withContext(Dispatchers.IO) {
            dao.clearMessages()
            dao.clearOutbox()
        }
    }

    suspend fun clearConversationLocal(conversationId: Int) {
        withContext(Dispatchers.IO) {
            dao.deleteMessagesForConversation(conversationId)
        }
    }

    private suspend fun sendInternal(
        token: String,
        conversationId: Int,
        body: String,
        isE2e: Boolean,
        mediaUploadId: String?,
        mediaMime: String?,
        mediaName: String?,
        forward: ForwardMeta?,
        mediaKind: String? = null,
        replyTo: ReplyMeta? = null,
    ): MsgItem =
        withContext(Dispatchers.IO) {
            val localId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            val replyJson = replyTo?.let { replyToJson(it) }
            val pending =
                MessageEntity(
                    localId = localId,
                    serverId = null,
                    conversationId = conversationId,
                    body = body,
                    mine = true,
                    isE2e = isE2e,
                    createdAt = now,
                    status = "sending",
                    mediaUploadId = normalizeUploadId(mediaUploadId),
                    mediaMime = mediaMime?.trim()?.takeIf { it.isNotEmpty() && !it.equals("null", true) },
                    mediaName = mediaName?.trim()?.takeIf { it.isNotEmpty() && !it.equals("null", true) },
                    mediaKind = mediaKind ?: mediaKindFromMime(mediaMime, mediaName),
                    messageType =
                        when {
                            AlbumMeta.fromJson(body) != null -> "album"
                            normalizeUploadId(mediaUploadId) != null -> "media"
                            else -> "text"
                        },
                    forwardJson = forward?.let { forwardToJson(it) },
                    replyJson = replyJson,
                )
            dao.upsertMessage(pending)
            dao.upsertOutbox(
                OutboxEntity(
                    localId,
                    conversationId,
                    body,
                    isE2e,
                    mediaUploadId,
                    pending.forwardJson,
                    replyTo?.messageId,
                    now,
                    0,
                ),
            )
            val result =
                api.sendMessage(
                    token,
                    conversationId,
                    body,
                    isE2e,
                    mediaUploadId,
                    forward,
                    replyTo?.messageId,
                )
            if (result.ok && result.messageId != null) {
                finalizeSent(localId, result.messageId, "sent")
                dao.deleteOutbox(localId)
                pending.copy(serverId = result.messageId, status = "sent").toMsg()
            } else {
                val st = if (api.lastHttpOk) "failed" else "queued"
                dao.updateStatus(localId, st)
                pending.copy(status = st).toMsg()
            }
        }

    private suspend fun mergeServerRow(msg: MsgItem, conversationId: Int): MessageEntity {
        val base = msg.toEntity(conversationId)
        val sid = base.serverId ?: return base
        val existing = dao.findByServerId(sid) ?: return base
        return base.copy(localId = existing.localId, rowId = existing.rowId)
    }

    private suspend fun finalizeSent(localId: String, serverId: Long, status: String) {
        val srvKey = "srv-$serverId"
        val existing = dao.findByServerId(serverId)
        when {
            existing != null && existing.localId != localId -> dao.deleteByLocalId(localId)
            existing != null -> dao.markSent(existing.localId, serverId, status)
            else -> dao.markSentRekey(localId, srvKey, serverId, status)
        }
    }

    private fun MsgItem.toEntity(conversationId: Int): MessageEntity =
        MessageEntity(
            localId = localId,
            serverId = id,
            conversationId = conversationId,
            body = if (messageType == "poll") bodyRaw.ifBlank { body } else body,
            mine = mine,
            isE2e = isE2e,
            createdAt = createdAt,
            editedAt = editedAt,
            status = status,
            readByPeer = readByPeer,
            peerReadAt = peerReadAt,
            mediaUploadId = normalizeUploadId(mediaUploadId),
            mediaMime = mediaMime?.trim()?.takeIf { it.isNotEmpty() && !it.equals("null", true) },
            mediaName = mediaName?.trim()?.takeIf { it.isNotEmpty() && !it.equals("null", true) },
            mediaKind = mediaKind ?: mediaKindFromMime(mediaMime, mediaName),
            messageType =
                when {
                    messageType == "poll" -> "poll"
                    normalizeUploadId(mediaUploadId) != null -> messageType
                    else -> messageType.ifBlank { "text" }
                },
            senderId = senderId,
            senderName = senderName,
            forwardJson = forward?.let { forwardToJson(it) },
            callJson = callMeta?.let { CallMeta.toJson(it) },
            reactionsJson = if (reactions.isEmpty()) null else reactionsToJson(reactions),
            replyJson = reply?.let { replyToJson(it) },
        )

    private fun MessageEntity.toMsg(): MsgItem {
        val call = callJson?.let { CallMeta.fromJson(it) }
        val album = AlbumMeta.fromJson(body)
        val poll = if (album == null && messageType == "poll") PollMeta.fromJson(body) else null
        val fwd = forwardJson?.let { parseForwardJson(it) }
        val reply = parseReplyJson(replyJson)
        val display =
            when {
                poll != null -> "📊 ${poll.question}"
                call != null -> call.displayText(mine)
                album != null -> {
                    val cap = AlbumMeta.captionFromJson(body)
                    if (cap.isNotBlank()) cap else "📷 ${album.items.size}"
                }
                status == "queued" -> "$body ⏳"
                status == "sending" -> "$body …"
                status == "failed" -> "$body ⚠"
                else -> body
            }
        return MsgItem(
            id = serverId ?: -rowId,
            localId = localId,
            body = display,
            bodyRaw = if (poll != null) body else body,
            mine = mine,
            createdAt = createdAt,
            editedAt = editedAt,
            isE2e = isE2e,
            readByPeer = readByPeer,
            peerReadAt = peerReadAt,
            status = status,
            mediaUploadId = normalizeUploadId(mediaUploadId),
            mediaMime = mediaMime?.trim()?.takeIf { it.isNotEmpty() && !it.equals("null", true) },
            mediaName = mediaName?.trim()?.takeIf { it.isNotEmpty() && !it.equals("null", true) },
            mediaKind = mediaKind,
            messageType =
                when {
                    poll != null -> "poll"
                    album != null -> "album"
                    normalizeUploadId(mediaUploadId) != null -> messageType
                    else -> messageType.ifBlank { "text" }
                },
            forward = fwd,
            callMeta = call,
            pollMeta = poll,
            albumMeta = album,
            reactions = reactionsFromJson(reactionsJson),
            senderId = senderId,
            senderName = senderName,
            reply = reply,
        )
    }

    private fun reactionsToJson(list: List<MsgReaction>): String {
        val arr = JSONArray()
        list.forEach { r ->
            repeat(r.count) { arr.put(JSONObject().put("emoji", r.emoji).put("user_id", if (r.mine) 1 else 0)) }
        }
        return arr.toString()
    }

    private fun reactionsFromJson(raw: String?): List<MsgReaction> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            val map = linkedMapOf<String, Pair<Int, Boolean>>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val em = o.optString("emoji")
                val cur = map[em] ?: (0 to false)
                map[em] = (cur.first + 1) to (cur.second || o.optInt("user_id") == 1)
            }
            map.map { (e, v) -> MsgReaction(e, v.first, v.second) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun forwardToJson(f: ForwardMeta): String =
        JSONObject()
            .put("from_label", f.fromLabel)
            .put("body_snippet", f.bodySnippet)
            .put("from_user_id", f.fromUserId)
            .put("original_message_id", f.originalMessageId)
            .toString()

    private fun parseForwardJson(raw: String): ForwardMeta? =
        try {
            val o = JSONObject(raw)
            ForwardMeta(o.optString("from_label"), o.optString("body_snippet"), o.optInt("from_user_id"), o.optLong("original_message_id"))
        } catch (_: Exception) {
            null
        }

    suspend fun exportConversation(context: Context, conversationId: Int, title: String): File? =
        withContext(Dispatchers.IO) {
            val rows = dao.messagesForConversation(conversationId)
            if (rows.isEmpty()) return@withContext null
            val root = ProtoPersistentStorage.exportsDir(context)
            val dir = File(root, "exports").apply { mkdirs() }
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val safeTitle = title.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(24)
            val out = File(dir, "chat_${safeTitle}_$stamp.json")
            val transcriptMap = ProtoVoiceTranscriptStore.transcriptMap(context)
            val arr = JSONArray()
            rows.forEach { row ->
                val msg = row.toMsg()
                val voiceTranscript =
                    ProtoVoiceTranscriptStore.transcriptForMessage(transcriptMap, conversationId, msg)
                val voiceSource =
                    ProtoVoiceTranscriptStore.transcriptSource(transcriptMap, conversationId, msg)
                val entry =
                    JSONObject()
                        .put("id", msg.id)
                        .put("mine", msg.mine)
                        .put("sender", msg.senderName)
                        .put("type", msg.messageType)
                        .put("body", msg.bodyRaw.ifBlank { msg.body })
                        .put("created_at", msg.createdAt)
                if (!voiceTranscript.isNullOrBlank()) {
                    entry.put("voice_transcript", voiceTranscript)
                    if (!voiceSource.isNullOrBlank()) entry.put("voice_transcript_source", voiceSource)
                }
                arr.put(entry)
            }
            out.writeText(
                JSONObject()
                    .put("conversation_id", conversationId)
                    .put("title", title)
                    .put("exported_at", System.currentTimeMillis())
                    .put("messages", arr)
                    .toString(2),
            )
            out
        }
}
