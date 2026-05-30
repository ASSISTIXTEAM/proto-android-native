package org.assistix.proto.nativeapp.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assistix.proto.nativeapp.ui.formatStoredMessagePreview

data class PulseUnreadItem(
    val conversationId: Int,
    val title: String,
    val preview: String,
    val unreadCount: Int,
    val kind: String,
    val peerUserId: Int,
)

data class PulseProtoPost(
    val title: String,
    val body: String,
    val conversationId: Int,
)

data class PulseSavedHint(
    val preview: String,
    val conversationId: Int,
)

data class PulseDigest(
    val unread: List<PulseUnreadItem>,
    val protoPost: PulseProtoPost?,
    val savedHints: List<PulseSavedHint>,
    val aiSummary: String,
    val totalUnread: Int,
)

object ProtoPulseRepository {
    suspend fun load(token: String, api: ProtoApi, languageCode: String): PulseDigest =
        withContext(Dispatchers.IO) {
            val chats = runCatching { api.conversations(token) }.getOrElse { emptyList() }
            val unread =
                chats
                    .filter { it.unreadCount > 0 && it.kind != "saved" }
                    .sortedByDescending { it.updatedAt }
                    .take(8)
                    .map {
                        PulseUnreadItem(
                            conversationId = it.id,
                            title = it.title.ifBlank { it.peerDisplayName }.ifBlank { "Chat" },
                            preview = formatStoredMessagePreview(it.preview, it.lastSenderId, 0),
                            unreadCount = it.unreadCount,
                            kind = it.kind,
                            peerUserId = it.peerUserId,
                        )
                    }
            val saved =
                chats
                    .firstOrNull { it.kind == "saved" }
                    ?.let { s ->
                        listOf(
                            PulseSavedHint(
                                preview = formatStoredMessagePreview(s.preview, s.lastSenderId, 0),
                                conversationId = s.id,
                            ),
                        )
                    } ?: emptyList()
            val protoChannel = runCatching { api.channelByNick(token, "proto") }.getOrNull()
            val protoPost =
                protoChannel?.takeIf { it.conversationId > 0 }?.let { ch ->
                    val feed =
                        api.fetchChannelFeed(
                            token = token,
                            conversationId = ch.conversationId,
                            lang = languageCode,
                            limit = 1,
                        )
                    val post = feed?.posts?.firstOrNull()
                    if (post != null) {
                        PulseProtoPost(
                            title = ch.title.ifBlank { "@proto" },
                            body = post.shownText.take(280),
                            conversationId = ch.conversationId,
                        )
                    } else {
                        null
                    }
                }
            val previewLines = buildList {
                if (unread.isNotEmpty()) {
                    add("Unread:")
                    unread.take(5).forEach { add("${it.title} (${it.unreadCount}): ${it.preview}") }
                }
                protoPost?.let { add("@proto: ${it.body.take(120)}") }
                saved.firstOrNull()?.let { add("Saved: ${it.preview}") }
            }
            val aiSummary =
                if (previewLines.isNotEmpty()) {
                    val reply =
                        runCatching {
                            api.assistixRequest(
                                token = token,
                                action = "pulse_brief",
                                previewLines = previewLines,
                                language = languageCode,
                            )
                        }.getOrNull()
                    if (reply?.ok == true) reply.text.trim() else ""
                } else {
                    ""
                }
            PulseDigest(
                unread = unread,
                protoPost = protoPost,
                savedHints = saved,
                aiSummary = aiSummary,
                totalUnread = chats.sumOf { it.unreadCount },
            )
        }
}
