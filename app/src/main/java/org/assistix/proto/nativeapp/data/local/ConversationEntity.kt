package org.assistix.proto.nativeapp.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: Int,
    val kind: String,
    val title: String,
    val preview: String,
    val updatedAt: Long,
    val peerUserId: Int,
    val peerDisplayName: String,
    val peerStatusEmoji: String,
    val peerAvatarUploadId: String?,
    val unreadCount: Int,
    val myLastReadMessageId: Long,
    val groupOwnerId: Int,
    val groupMyRole: String,
    val groupMemberCount: Int,
    val lastSenderId: Int,
    val lastMessageId: Long,
    val channelNick: String = "",
    val channelVerified: Boolean = false,
    val cachedAt: Long = System.currentTimeMillis(),
)
