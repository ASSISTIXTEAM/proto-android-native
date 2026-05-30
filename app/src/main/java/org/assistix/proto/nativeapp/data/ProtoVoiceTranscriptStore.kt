package org.assistix.proto.nativeapp.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject

private fun Context.voiceTranscriptStore() = ProtoDataStoreFactory.preferences(this, "proto_voice_transcripts")

/**
 * Локальный кэш расшифровок голосовых для поиска в чате («найди, где говорили про…»).
 */
object ProtoVoiceTranscriptStore {
    private fun key(conversationId: Int, messageId: Long, uploadId: String): String =
        when {
            messageId > 0L -> "m:$conversationId:$messageId"
            uploadId.isNotBlank() -> "u:$conversationId:$uploadId"
            else -> ""
        }

    suspend fun put(
        context: Context,
        conversationId: Int,
        messageId: Long,
        uploadId: String,
        text: String,
        source: String = "local",
    ) {
        val k = key(conversationId, messageId, uploadId)
        if (k.isEmpty() || text.isBlank()) return
        context.voiceTranscriptStore().edit { prefs ->
            val raw = prefs[stringPreferencesKey("map")] ?: "{}"
            val root =
                try {
                    JSONObject(raw)
                } catch (_: Exception) {
                    JSONObject()
                }
            root.put(
                k,
                JSONObject()
                    .put("text", text.trim().take(4000))
                    .put("source", source),
            )
            prefs[stringPreferencesKey("map")] = root.toString()
        }
    }

    fun transcriptSource(
        map: Map<String, String>,
        conversationId: Int,
        message: MsgItem,
    ): String? = entryForMessage(map, conversationId, message)?.second

    private fun entryFromStoredValue(stored: String?): Pair<String, String>? {
        if (stored.isNullOrBlank()) return null
        val sep = stored.indexOf('\u0000')
        if (sep < 0) return Pair(stored.trim(), "local")
        val text = stored.substring(0, sep).trim()
        val source = stored.substring(sep + 1).trim()
        if (text.isBlank()) return null
        return Pair(text, source.ifBlank { "local" })
    }

    private fun entryForMessage(
        map: Map<String, String>,
        conversationId: Int,
        message: MsgItem,
    ): Pair<String, String>? {
        val k1 = key(conversationId, message.id, "")
        val k2 = key(conversationId, 0L, message.mediaUploadId.orEmpty())
        return entryFromStoredValue(map[k1] ?: map[k2])
    }

    private fun readEntry(root: JSONObject, k: String): Pair<String, String>? {
        val obj = root.optJSONObject(k)
        if (obj != null) {
            val t = obj.optString("text").trim()
            if (t.isBlank()) return null
            return Pair(t, obj.optString("source", "local"))
        }
        val legacy = root.optString(k).trim()
        if (legacy.isBlank()) return null
        return Pair(legacy, "local")
    }

    suspend fun get(
        context: Context,
        conversationId: Int,
        messageId: Long,
        uploadId: String,
    ): String? = transcriptMap(context)[key(conversationId, messageId, uploadId)]

    suspend fun transcriptMap(context: Context): Map<String, String> {
        val raw = context.voiceTranscriptStore().data.map { it[stringPreferencesKey("map")] }.first()
        return parseMap(raw)
    }

    fun transcriptForMessage(
        map: Map<String, String>,
        conversationId: Int,
        message: MsgItem,
    ): String? = entryForMessage(map, conversationId, message)?.first

    private fun parseMap(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return try {
            val root = JSONObject(raw)
            buildMap {
                val keys = root.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val entry = readEntry(root, k) ?: continue
                    put(
                        k,
                        if (entry.second.isBlank()) {
                            entry.first
                        } else {
                            "${entry.first}\u0000${entry.second}"
                        },
                    )
                }
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }
}
