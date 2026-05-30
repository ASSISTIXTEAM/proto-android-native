package org.assistix.proto.nativeapp.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.assistix.proto.nativeapp.data.local.AssistixMessageEntity
import org.assistix.proto.nativeapp.data.local.AssistixThreadEntity
import org.assistix.proto.nativeapp.data.local.AssistixThreadListRow
import org.assistix.proto.nativeapp.data.local.ProtoDao
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AssistixThread(
    val id: Long,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val preview: String = "",
)

data class AssistixHistoryLine(
    val id: Long,
    val role: String,
    val text: String,
    val createdAt: Long,
)

class AssistixChatRepository(
    private val dao: ProtoDao,
) {
    fun observeThreads(): Flow<List<AssistixThread>> =
        dao.observeAssistixThreads().map { rows ->
            rows.map {
                AssistixThread(
                    id = it.id,
                    title = it.title,
                    createdAt = it.createdAt,
                    updatedAt = it.updatedAt,
                    preview = it.lastPreview,
                )
            }
        }

    fun observeMessages(threadId: Long): Flow<List<AssistixHistoryLine>> =
        dao.observeAssistixMessages(threadId).map { rows ->
            rows.map { AssistixHistoryLine(it.id, it.role, it.text, it.createdAt) }
        }

    suspend fun ensureDefaultThread(): Long =
        withContext(Dispatchers.IO) {
            dao.firstAssistixThreadId() ?: createThread("PROTO AI")
        }

    suspend fun createThread(title: String): Long =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val trimmed = title.trim().ifBlank { defaultThreadTitle(now) }
            dao.insertAssistixThread(
                AssistixThreadEntity(
                    title = trimmed,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }

    suspend fun touchThread(threadId: Long) =
        withContext(Dispatchers.IO) {
            dao.touchAssistixThread(threadId, System.currentTimeMillis())
        }

    suspend fun renameThread(threadId: Long, title: String) =
        withContext(Dispatchers.IO) {
            dao.renameAssistixThread(threadId, title.trim().ifBlank { defaultThreadTitle() }, System.currentTimeMillis())
        }

    suspend fun deleteThread(threadId: Long) =
        withContext(Dispatchers.IO) {
            dao.deleteAssistixThread(threadId)
        }

    suspend fun appendUser(threadId: Long, text: String): Long =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val id =
                dao.insertAssistixMessage(
                    AssistixMessageEntity(threadId = threadId, role = "user", text = text, createdAt = now),
                )
            dao.touchAssistixThread(threadId, now)
            id
        }

    suspend fun appendAssistant(threadId: Long, text: String): Long =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val id =
                dao.insertAssistixMessage(
                    AssistixMessageEntity(threadId = threadId, role = "assistant", text = text, createdAt = now),
                )
            dao.touchAssistixThread(threadId, now)
            id
        }

    suspend fun clearThread(threadId: Long) =
        withContext(Dispatchers.IO) {
            dao.clearAssistixMessagesForThread(threadId)
        }

    suspend fun rollbackLastUserMessage(threadId: Long) =
        withContext(Dispatchers.IO) {
            dao.deleteLastUserMessageInThread(threadId)
        }

    suspend fun exportThread(context: Context, threadId: Long): File? =
        withContext(Dispatchers.IO) {
            val rows = dao.assistixMessagesForThread(threadId)
            if (rows.isEmpty()) return@withContext null
            val thread = dao.assistixThreadById(threadId)
            val root = ProtoPersistentStorage.exportsDir(context)
            val dir = File(root, "exports").apply { mkdirs() }
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val safeTitle = (thread?.title ?: "assistix").replace(Regex("[^a-zA-Z0-9_-]"), "_").take(24)
            val out = File(dir, "assistix_${safeTitle}_$stamp.json")
            val arr = JSONArray()
            rows.forEach { row ->
                arr.put(
                    JSONObject()
                        .put("role", row.role)
                        .put("text", row.text)
                        .put("created_at", row.createdAt),
                )
            }
            out.writeText(
                JSONObject()
                    .put("thread_id", threadId)
                    .put("title", thread?.title ?: "")
                    .put("exported_at", System.currentTimeMillis())
                    .put("messages", arr)
                    .toString(2),
            )
            out
        }

    private fun defaultThreadTitle(now: Long = System.currentTimeMillis()): String {
        val fmt = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault())
        return fmt.format(Date(now))
    }
}
