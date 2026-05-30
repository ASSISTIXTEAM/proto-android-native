package org.assistix.proto.nativeapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProtoDao {
    @Query("SELECT * FROM messages WHERE conversationId = :cid ORDER BY createdAt ASC, rowId ASC")
    fun observeMessages(cid: Int): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :cid ORDER BY createdAt ASC, rowId ASC")
    suspend fun messagesForConversation(cid: Int): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessage(row: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessages(rows: List<MessageEntity>)

    @Query("UPDATE messages SET serverId = :serverId, status = :status WHERE localId = :localId")
    suspend fun markSent(localId: String, serverId: Long, status: String)

    @Query(
        "UPDATE messages SET localId = :newLocalId, serverId = :serverId, status = :status WHERE localId = :oldLocalId",
    )
    suspend fun markSentRekey(oldLocalId: String, newLocalId: String, serverId: Long, status: String)

    @Query("SELECT * FROM messages WHERE serverId = :serverId LIMIT 1")
    suspend fun findByServerId(serverId: Long): MessageEntity?

    @Query("DELETE FROM messages WHERE localId = :localId")
    suspend fun deleteByLocalId(localId: String)

    @Query("UPDATE messages SET status = :status WHERE localId = :localId")
    suspend fun updateStatus(localId: String, status: String)

    @Query("DELETE FROM messages WHERE serverId = :serverId")
    suspend fun deleteByServerId(serverId: Long)

    @Query("SELECT COALESCE(MAX(serverId), 0) FROM messages WHERE conversationId = :cid AND serverId IS NOT NULL")
    suspend fun maxServerId(cid: Int): Long

    @Query(
        "UPDATE messages SET readByPeer = :read, peerReadAt = :readAt WHERE conversationId = :cid AND mine = 1 AND serverId IS NOT NULL AND serverId <= :through",
    )
    suspend fun markReadThrough(cid: Int, through: Long, read: Boolean, readAt: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOutbox(row: OutboxEntity)

    @Query("SELECT * FROM outbox ORDER BY createdAt ASC")
    suspend fun outboxAll(): List<OutboxEntity>

    @Query("SELECT COUNT(*) FROM outbox")
    suspend fun outboxCount(): Int

    @Query("DELETE FROM outbox WHERE localId = :localId")
    suspend fun deleteOutbox(localId: String)

    @Query("UPDATE outbox SET retryCount = retryCount + 1 WHERE localId = :localId")
    suspend fun bumpOutboxRetry(localId: String)

    @Query("DELETE FROM messages")
    suspend fun clearMessages()

    @Query("DELETE FROM outbox")
    suspend fun clearOutbox()

    @Query(
        """
        DELETE FROM messages WHERE serverId IS NOT NULL AND rowId NOT IN (
            SELECT MIN(rowId) FROM messages WHERE serverId IS NOT NULL
            GROUP BY conversationId, serverId
        )
        """,
    )
    suspend fun dedupeByServerId()

    @Query("DELETE FROM messages WHERE conversationId = :cid")
    suspend fun deleteMessagesForConversation(cid: Int)

    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun observeConversations(): Flow<List<ConversationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConversations(rows: List<ConversationEntity>)

    @Query("SELECT COUNT(*) FROM conversations")
    suspend fun conversationCount(): Int

    @Query("DELETE FROM conversations")
    suspend fun clearConversations()

    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    suspend fun allConversations(): List<ConversationEntity>

    @Query(
        """
        SELECT t.id, t.title, t.createdAt, t.updatedAt,
            COALESCE(
                (SELECT m.text FROM assistix_messages m WHERE m.threadId = t.id
                 ORDER BY m.createdAt DESC, m.id DESC LIMIT 1),
                ''
            ) AS lastPreview
        FROM assistix_threads t
        ORDER BY t.updatedAt DESC
        """,
    )
    fun observeAssistixThreads(): Flow<List<AssistixThreadListRow>>

    @Query("SELECT * FROM assistix_threads WHERE id = :threadId LIMIT 1")
    suspend fun assistixThreadById(threadId: Long): AssistixThreadEntity?

    @Query("SELECT id FROM assistix_threads ORDER BY updatedAt DESC LIMIT 1")
    suspend fun firstAssistixThreadId(): Long?

    @Insert
    suspend fun insertAssistixThread(row: AssistixThreadEntity): Long

    @Query("UPDATE assistix_threads SET updatedAt = :updatedAt WHERE id = :threadId")
    suspend fun touchAssistixThread(threadId: Long, updatedAt: Long)

    @Query("UPDATE assistix_threads SET title = :title, updatedAt = :updatedAt WHERE id = :threadId")
    suspend fun renameAssistixThread(threadId: Long, title: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM assistix_threads WHERE id = :threadId")
    suspend fun deleteAssistixThread(threadId: Long)

    @Query("SELECT * FROM assistix_messages WHERE threadId = :threadId ORDER BY createdAt ASC, id ASC")
    fun observeAssistixMessages(threadId: Long): Flow<List<AssistixMessageEntity>>

    @Query("SELECT * FROM assistix_messages WHERE threadId = :threadId ORDER BY createdAt ASC, id ASC")
    suspend fun assistixMessagesForThread(threadId: Long): List<AssistixMessageEntity>

    @Insert
    suspend fun insertAssistixMessage(row: AssistixMessageEntity): Long

    @Query("DELETE FROM assistix_messages WHERE threadId = :threadId")
    suspend fun clearAssistixMessagesForThread(threadId: Long)

    @Query(
        """
        DELETE FROM assistix_messages
        WHERE id = (
            SELECT id FROM assistix_messages
            WHERE threadId = :threadId AND role = 'user'
            ORDER BY id DESC LIMIT 1
        )
        """,
    )
    suspend fun deleteLastUserMessageInThread(threadId: Long)

    @Query("SELECT * FROM message_translations WHERE messageId = :messageId AND targetLang = :targetLang LIMIT 1")
    suspend fun translation(messageId: Long, targetLang: String): MessageTranslationEntity?

    @Query(
        "SELECT * FROM message_translations WHERE targetLang = :targetLang AND messageId IN (:messageIds)",
    )
    suspend fun translationsForMessages(messageIds: List<Long>, targetLang: String): List<MessageTranslationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTranslation(row: MessageTranslationEntity)

    @Query("DELETE FROM message_translations WHERE messageId = :messageId AND targetLang = :targetLang")
    suspend fun deleteTranslation(messageId: Long, targetLang: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMediaLocal(row: MediaLocalEntity)

    @Query("SELECT * FROM media_local WHERE uploadId = :uploadId LIMIT 1")
    suspend fun mediaLocal(uploadId: String): MediaLocalEntity?
}
