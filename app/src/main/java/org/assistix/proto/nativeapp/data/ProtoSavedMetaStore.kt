package org.assistix.proto.nativeapp.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

data class SavedMessageMeta(
    val tags: List<String> = emptyList(),
    val reminderAtMs: Long = 0L,
    val note: String = "",
)

object ProtoSavedMetaStore {
    private fun savedMetaStore(context: Context) = ProtoDataStoreFactory.preferences(context, "proto_saved_meta")
    private val mapKey = stringPreferencesKey("by_message")

    suspend fun get(context: Context, messageId: Long): SavedMessageMeta {
        if (messageId <= 0L) return SavedMessageMeta()
        return loadAll(context)[messageId.toString()] ?: SavedMessageMeta()
    }

    suspend fun set(context: Context, messageId: Long, meta: SavedMessageMeta) {
        if (messageId <= 0L) return
        savedMetaStore(context).edit { prefs ->
            val all = decode(prefs[mapKey]).toMutableMap()
            if (meta.tags.isEmpty() && meta.reminderAtMs <= 0L && meta.note.isBlank()) {
                all.remove(messageId.toString())
            } else {
                all[messageId.toString()] = meta
            }
            prefs[mapKey] = encode(all)
        }
    }

    suspend fun loadAll(context: Context): Map<String, SavedMessageMeta> =
        savedMetaStore(context).data.map { decode(it[mapKey]) }.first()

    suspend fun allTags(context: Context): List<String> =
        loadAll(context).values
            .flatMap { it.tags }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()

    private fun encode(map: Map<String, SavedMessageMeta>): String {
        val root = JSONObject()
        map.forEach { (id, meta) ->
            root.put(
                id,
                JSONObject()
                    .put("tags", JSONArray(meta.tags))
                    .put("reminder", meta.reminderAtMs)
                    .put("note", meta.note),
            )
        }
        return root.toString()
    }

    private fun decode(raw: String?): Map<String, SavedMessageMeta> {
        if (raw.isNullOrBlank()) return emptyMap()
        return try {
            val root = JSONObject(raw)
            buildMap {
                val keys = root.keys()
                while (keys.hasNext()) {
                    val id = keys.next()
                    val o = root.optJSONObject(id) ?: continue
                    val tags = mutableListOf<String>()
                    val arr = o.optJSONArray("tags") ?: JSONArray()
                    for (i in 0 until arr.length()) {
                        val t = arr.optString(i).trim()
                        if (t.isNotEmpty()) tags += t
                    }
                    put(
                        id,
                        SavedMessageMeta(
                            tags = tags,
                            reminderAtMs = o.optLong("reminder", 0L),
                            note = o.optString("note", ""),
                        ),
                    )
                }
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }
}
