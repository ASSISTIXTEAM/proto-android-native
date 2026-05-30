package org.assistix.proto.nativeapp.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.assistix.proto.nativeapp.data.local.MessageEntity
import org.assistix.proto.nativeapp.data.local.ProtoDao

class ProtoCachePrefetcher(
    private val dao: ProtoDao,
    private val cache: ProtoCacheManager,
    private val api: ProtoApi,
    private val messages: ProtoMessageRepository,
    private val conversations: ProtoConversationRepository,
    private val mediaResolver: ProtoMediaResolver? = null,
) {
    private val mutex = Mutex()

    suspend fun warmAll(token: String, myUserId: Int, wifiOnly: Boolean = false) =
        mutex.withLock {
            withContext(Dispatchers.IO) {
                runCatching {
                    conversations.syncFromServer(token)
                    val convs = dao.allConversations()
                    for (conv in convs) {
                        if (!conv.peerAvatarUploadId.isNullOrBlank()) {
                            runCatching {
                                ProtoAvatarCache.localFile(cache, api, token, conv.peerAvatarUploadId)
                            }
                        }
                        runCatching {
                            messages.refreshFromServer(
                                token,
                                conv.id,
                                myUserId,
                                sendReadReceipts = false,
                            )
                        }
                        val rows = dao.messagesForConversation(conv.id).takeLast(PREFETCH_MESSAGES_PER_CHAT)
                        for (row in rows) {
                            prefetchMessageMedia(token, row, wifiOnly)
                        }
                    }
                }.onFailure { e ->
                    Log.w(TAG, "warmAll failed", e)
                }
            }
        }

    suspend fun warmConversation(token: String, conversationId: Int, myUserId: Int) =
        withContext(Dispatchers.IO) {
            runCatching {
                messages.refreshFromServer(token, conversationId, myUserId, sendReadReceipts = false)
                dao.messagesForConversation(conversationId).takeLast(PREFETCH_MESSAGES_PER_CHAT).forEach { row ->
                    prefetchMessageMedia(token, row, wifiOnly = false)
                }
            }
        }

    private suspend fun prefetchMessageMedia(token: String, row: MessageEntity, wifiOnly: Boolean) {
        if (wifiOnly) return
        val uploadId = row.mediaUploadId?.trim()?.takeIf { it.isNotEmpty() } ?: return
        val resolver = mediaResolver
        if (resolver != null) {
            resolver.fetch(token, uploadId, row.mediaMime, row.mediaName, row.conversationId)
            return
        }
        val mime = row.mediaMime.orEmpty()
        val kind = row.mediaKind ?: mediaKindFromMime(mime, row.mediaName)
        when (kind) {
            "image" -> {
                val f = cache.photoFile(uploadId)
                if (!f.exists() || f.length() == 0L) api.downloadMedia(token, uploadId, f)
            }
            "video" -> {
                val f = cache.videoFile(uploadId)
                if (!f.exists() || f.length() == 0L) api.downloadMedia(token, uploadId, f)
            }
            "voice", "audio" -> {
                val ext = cacheExtForMime(mime)
                val f = cache.audioFile(uploadId, ext)
                if (!f.exists() || f.length() == 0L) api.downloadMedia(token, uploadId, f)
            }
        }
    }

    companion object {
        private const val TAG = "ProtoCachePrefetch"
        private const val PREFETCH_MESSAGES_PER_CHAT = 48
    }
}
