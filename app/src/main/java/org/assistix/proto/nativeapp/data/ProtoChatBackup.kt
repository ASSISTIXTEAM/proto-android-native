package org.assistix.proto.nativeapp.data

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assistix.proto.nativeapp.data.local.ProtoDao
import org.json.JSONArray
import org.json.JSONObject

/** Local chat-history backups only (no statuses, gifts, subscriptions). */
object ProtoChatBackup {
    private const val TAG = "ProtoChatBackup"
    private const val PREFS = "proto_chat_backup"
    private const val KEY_LAST_MS = "last_run_ms"
    private const val INTERVAL_MS = 12L * 60 * 60 * 1000
    private const val KEEP_FILES = 5

    suspend fun runIfDue(context: Context, dao: ProtoDao, api: ProtoApi, token: String?) {
        if (token.isNullOrBlank()) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(KEY_LAST_MS, 0L) < INTERVAL_MS) return
        runCatching {
            withContext(Dispatchers.IO) {
                export(context, dao, api, token)
                prefs.edit().putLong(KEY_LAST_MS, now).apply()
            }
        }.onFailure { Log.w(TAG, "backup failed", it) }
    }

    private suspend fun export(context: Context, dao: ProtoDao, api: ProtoApi, token: String) {
        val dir = ProtoPersistentStorage.backupsDir(context)
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        val out = File(dir, "chats_$stamp.json")
        val convs = api.conversations(token)
        val root = JSONObject()
        root.put("version", 1)
        root.put("exported_at", System.currentTimeMillis() / 1000)
        val convArr = JSONArray()
        for (c in convs) {
            val row = JSONObject()
            row.put("conversation_id", c.id)
            row.put("kind", c.kind)
            row.put("title", c.title)
            row.put("updated_at", c.updatedAt)
            val msgs = JSONArray()
            for (m in dao.messagesForConversation(c.id)) {
                msgs.put(
                    JSONObject()
                        .put("server_id", m.serverId)
                        .put("local_id", m.localId)
                        .put("body", m.body)
                        .put("mine", m.mine)
                        .put("created_at", m.createdAt)
                        .put("message_type", m.messageType)
                        .put("sender_id", m.senderId),
                )
            }
            row.put("messages", msgs)
            convArr.put(row)
        }
        root.put("conversations", convArr)
        out.writeText(root.toString())
        val files = dir.listFiles()?.filter { it.name.startsWith("chats_") && it.name.endsWith(".json") }?.sortedByDescending { it.lastModified() } ?: emptyList()
        files.drop(KEEP_FILES).forEach { it.delete() }
        Log.i(TAG, "saved ${out.absolutePath}")
    }
}
