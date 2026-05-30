package org.assistix.proto.nativeapp.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.assistix.proto.nativeapp.data.local.ConversationEntity
import org.assistix.proto.nativeapp.data.local.ProtoDao

class ProtoConversationRepository(
    private val dao: ProtoDao,
    private val api: ProtoApi,
) {
    fun observeConversations(): Flow<List<ConvItem>> =
        dao.observeConversations().map { rows -> rows.map { it.toConv() } }

    suspend fun syncFromServer(token: String): Boolean =
        withContext(Dispatchers.IO) {
            val list = api.conversations(token)
            if (api.lastHttpOk) {
                dao.clearConversations()
                if (list.isNotEmpty()) {
                    dao.upsertConversations(list.map { it.toEntity() })
                }
            }
            api.lastHttpOk
        }

    suspend fun clearLocal() {
        withContext(Dispatchers.IO) {
            dao.clearConversations()
        }
    }

    suspend fun myLastReadMessageId(conversationId: Int): Long =
        withContext(Dispatchers.IO) {
            dao.allConversations().find { it.id == conversationId }?.myLastReadMessageId ?: 0L
        }

    suspend fun hasCached(): Boolean =
        withContext(Dispatchers.IO) {
            dao.conversationCount() > 0
        }
}

private fun ConversationEntity.toConv(): ConvItem =
    ConvItem(
        id = id,
        kind = kind,
        title = title,
        preview = preview,
        updatedAt = updatedAt,
        peerUserId = peerUserId,
        peerDisplayName = peerDisplayName,
        peerStatusEmoji = peerStatusEmoji,
        peerAvatarUploadId = peerAvatarUploadId,
        unreadCount = unreadCount,
        myLastReadMessageId = myLastReadMessageId,
        groupOwnerId = groupOwnerId,
        groupMyRole = groupMyRole,
        groupMemberCount = groupMemberCount,
        lastSenderId = lastSenderId,
        lastMessageId = lastMessageId,
        channelNick = channelNick,
        channelVerified = channelVerified,
    )

private fun ConvItem.toEntity(): ConversationEntity =
    ConversationEntity(
        id = id,
        kind = kind,
        title = title,
        preview = preview,
        updatedAt = updatedAt,
        peerUserId = peerUserId,
        peerDisplayName = peerDisplayName,
        peerStatusEmoji = peerStatusEmoji,
        peerAvatarUploadId = peerAvatarUploadId,
        unreadCount = unreadCount,
        myLastReadMessageId = myLastReadMessageId,
        groupOwnerId = groupOwnerId,
        groupMyRole = groupMyRole,
        groupMemberCount = groupMemberCount,
        lastSenderId = lastSenderId,
        lastMessageId = lastMessageId,
        channelNick = channelNick,
        channelVerified = channelVerified,
    )
