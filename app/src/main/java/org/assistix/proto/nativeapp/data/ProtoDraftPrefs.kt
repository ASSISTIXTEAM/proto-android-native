package org.assistix.proto.nativeapp.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Per-conversation composer drafts — device-local with Documents/PROTO mirror backup. */
class ProtoDraftPrefs(private val context: Context) {
    private val draftStore get() = ProtoDataStoreFactory.preferences(context, "proto_chat_drafts")

    private fun key(conversationId: Int) = stringPreferencesKey("draft_$conversationId")

    private fun backupFile(): File = File(ProtoPersistentStorage.rootDir(context), "offline/chat_drafts_backup.txt")

    fun draftFor(conversationId: Int): Flow<String> =
        draftStore.data.map { prefs ->
            prefs[key(conversationId)] ?: ""
        }

    val draftConversationIds: Flow<Set<Int>> =
        draftStore.data.map { prefs ->
            prefs.asMap().keys.mapNotNull { k ->
                val name = k.name
                if (!name.startsWith("draft_")) return@mapNotNull null
                val id = name.removePrefix("draft_").toIntOrNull() ?: return@mapNotNull null
                val text = (prefs[k] as? String).orEmpty()
                if (text.isNotEmpty()) id else null
            }.toSet()
        }

    suspend fun ensureRecovered() {
        val prefs = draftStore.data.first()
        val hasDrafts =
            prefs.asMap().keys.any { k ->
                k.name.startsWith("draft_") && (prefs[k] as? String).orEmpty().isNotBlank()
            }
        if (hasDrafts) return
        val file = backupFile()
        if (!file.isFile) return
        val raw = runCatching { file.readText() }.getOrNull().orEmpty()
        if (raw.isNotBlank()) importPayload(raw, overwriteEmptyOnly = false)
    }

    suspend fun setDraft(conversationId: Int, text: String) {
        draftStore.edit { prefs ->
            val k = key(conversationId)
            if (text.isBlank()) {
                prefs.remove(k)
            } else {
                prefs[k] = text
            }
        }
        persistBackup()
    }

    suspend fun persistBackup() {
        val payload = exportPayload()
        val file = backupFile()
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(payload)
        if (file.exists()) file.delete()
        tmp.renameTo(file)
    }

    suspend fun exportPayload(): String {
        val map = mutableMapOf<Int, String>()
        draftStore.data.map { prefs ->
            prefs.asMap().forEach { (k, v) ->
                val name = k.name
                if (!name.startsWith("draft_")) return@forEach
                val id = name.removePrefix("draft_").toIntOrNull() ?: return@forEach
                val text = (v as? String).orEmpty()
                if (text.isNotBlank()) map[id] = text
            }
        }.first()
        return map.entries.sortedBy { it.key }.joinToString("|") { (id, text) ->
            "$id:${text.replace('|', ' ').replace('\n', ' ')}"
        }
    }

    suspend fun importPayload(raw: String, overwriteEmptyOnly: Boolean = true) {
        if (raw.isBlank()) return
        draftStore.edit { prefs ->
            raw.split('|').forEach { chunk ->
                val sep = chunk.indexOf(':')
                if (sep <= 0) return@forEach
                val id = chunk.substring(0, sep).toIntOrNull() ?: return@forEach
                val text = chunk.substring(sep + 1)
                if (id <= 0 || text.isBlank()) return@forEach
                val k = key(id)
                if (overwriteEmptyOnly) {
                    val existing = (prefs[k] as? String).orEmpty()
                    if (existing.isNotBlank()) return@forEach
                }
                prefs[k] = text
            }
        }
        persistBackup()
    }

    suspend fun mergeRemotePayload(raw: String) {
        importPayload(raw, overwriteEmptyOnly = true)
    }
}
