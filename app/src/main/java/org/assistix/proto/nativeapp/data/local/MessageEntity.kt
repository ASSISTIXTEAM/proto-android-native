package org.assistix.proto.nativeapp.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    indices = [
        Index("conversationId"),
        Index("localId", unique = true),
        Index(value = ["conversationId", "serverId"], unique = true),
    ],
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val localId: String,
    val serverId: Long?,
    val conversationId: Int,
    val body: String,
    val mine: Boolean,
    val isE2e: Boolean,
    val createdAt: Long,
    val editedAt: Long = 0,
    val status: String,
    val readByPeer: Boolean = false,
    val peerReadAt: Long = 0,
    val mediaUploadId: String? = null,
    val mediaMime: String? = null,
    val mediaName: String? = null,
    val mediaKind: String? = null,
    val messageType: String = "text",
    val senderId: Int = 0,
    val senderName: String = "",
    val forwardJson: String? = null,
    val callJson: String? = null,
    val reactionsJson: String? = null,
    val replyJson: String? = null,
)

@Entity(tableName = "outbox")
data class OutboxEntity(
    @PrimaryKey val localId: String,
    val conversationId: Int,
    val body: String,
    val isE2e: Boolean,
    val mediaUploadId: String? = null,
    val forwardJson: String? = null,
    val replyToId: Long? = null,
    val createdAt: Long,
    val retryCount: Int = 0,
)
