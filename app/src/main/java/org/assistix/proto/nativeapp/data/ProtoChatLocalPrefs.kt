package org.assistix.proto.nativeapp.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private fun Context.chatLocalStore() = ProtoDataStoreFactory.preferences(this, "proto_chat_local")

data class ChatFolder(
    val id: String,
    val name: String,
    val conversationIds: Set<Int>,
    val colorId: Int = 0,
)

/** Pin (max 5), mute, folders, chat accent — device-local. */
class ProtoChatLocalPrefs(private val context: Context) {
    private val pinnedKey = stringPreferencesKey("pinned_ids")
    private val mutedKey = stringPreferencesKey("muted_ids")
    private val accentKey = stringPreferencesKey("accent_map")
    private val foldersKey = stringPreferencesKey("chat_folders_v1")
    private val archivedKey = stringPreferencesKey("archived_ids")
    private val vaultKey = stringPreferencesKey("vault_ids")
    private val vaultPinKey = stringPreferencesKey("vault_pin_hash")
    private val vaultUnlockedKey = booleanPreferencesKey("vault_unlocked_session")
    private val starredKey = stringPreferencesKey("starred_msgs_v1")
    private val chatNotesKey = stringPreferencesKey("chat_notes_v1")
    private val recentOpenKey = stringPreferencesKey("recent_open_ids_v1")

    val pinnedIds: Flow<Set<Int>> = context.chatLocalStore().data.map { parseIds(it[pinnedKey]) }
    val mutedIds: Flow<Set<Int>> = context.chatLocalStore().data.map { parseIds(it[mutedKey]) }
    val folders: Flow<List<ChatFolder>> = context.chatLocalStore().data.map { parseFolders(it[foldersKey]) }
    val archivedIds: Flow<Set<Int>> = context.chatLocalStore().data.map { parseIds(it[archivedKey]) }
    val vaultIds: Flow<Set<Int>> = context.chatLocalStore().data.map { parseIds(it[vaultKey]) }
    val vaultUnlocked: Flow<Boolean> = context.chatLocalStore().data.map { it[vaultUnlockedKey] == true }

    fun accentFor(conversationId: Int): Flow<Int> =
        context.chatLocalStore().data.map { parseAccentMap(it[accentKey])[conversationId] ?: 0 }

    suspend fun togglePin(conversationId: Int): Boolean {
        var ok = true
        context.chatLocalStore().edit { prefs ->
            val set = parseIds(prefs[pinnedKey]).toMutableSet()
            if (set.contains(conversationId)) {
                set.remove(conversationId)
            } else {
                if (set.size >= MAX_PINS) {
                    ok = false
                    return@edit
                }
                set.add(conversationId)
            }
            prefs[pinnedKey] = encodeIds(set)
        }
        return ok
    }

    suspend fun saveFolders(items: List<ChatFolder>) {
        context.chatLocalStore().edit { prefs ->
            prefs[foldersKey] = encodeFolders(items)
        }
    }

    suspend fun replaceFoldersPayload(payload: String) {
        context.chatLocalStore().edit { prefs ->
            prefs[foldersKey] = payload
        }
    }

    suspend fun upsertFolder(folder: ChatFolder) {
        context.chatLocalStore().edit { prefs ->
            val list = parseFolders(prefs[foldersKey]).toMutableList()
            val idx = list.indexOfFirst { it.id == folder.id }
            if (idx >= 0) list[idx] = folder else list.add(folder)
            prefs[foldersKey] = encodeFolders(list)
        }
    }

    suspend fun toggleMute(conversationId: Int) {
        context.chatLocalStore().edit { prefs ->
            val set = parseIds(prefs[mutedKey]).toMutableSet()
            if (!set.add(conversationId)) set.remove(conversationId)
            prefs[mutedKey] = encodeIds(set)
        }
    }

    suspend fun setAccent(conversationId: Int, accentId: Int) {
        context.chatLocalStore().edit { prefs ->
            val map = parseAccentMap(prefs[accentKey]).toMutableMap()
            if (accentId <= 0) map.remove(conversationId) else map[conversationId] = accentId.coerceIn(0, 4)
            prefs[accentKey] = encodeAccentMap(map)
        }
    }

    suspend fun toggleArchive(conversationId: Int) {
        context.chatLocalStore().edit { prefs ->
            val set = parseIds(prefs[archivedKey]).toMutableSet()
            if (!set.add(conversationId)) set.remove(conversationId)
            prefs[archivedKey] = encodeIds(set)
        }
    }

    suspend fun toggleVault(conversationId: Int) {
        context.chatLocalStore().edit { prefs ->
            val set = parseIds(prefs[vaultKey]).toMutableSet()
            if (!set.add(conversationId)) set.remove(conversationId)
            prefs[vaultKey] = encodeIds(set)
        }
    }

    suspend fun setVaultPin(pin: String) {
        val hash = if (pin.isBlank()) "" else ProtoVaultPin.hash(pin)
        context.chatLocalStore().edit { prefs ->
            prefs[vaultPinKey] = hash
            if (hash.isEmpty()) {
                prefs[vaultKey] = ""
                prefs[vaultUnlockedKey] = false
            }
        }
    }

    suspend fun unlockVault(pin: String): Boolean {
        var ok = false
        context.chatLocalStore().edit { prefs ->
            val want = prefs[vaultPinKey] ?: ""
            ok = ProtoVaultPin.matches(want, pin)
            prefs[vaultUnlockedKey] = ok
            if (ok && ProtoVaultPin.isLegacyStored(want, pin)) {
                prefs[vaultPinKey] = ProtoVaultPin.hash(pin)
            }
        }
        return ok
    }

    suspend fun lockVault() {
        context.chatLocalStore().edit { it[vaultUnlockedKey] = false }
    }

    fun starredMessageIds(conversationId: Int): Flow<Set<Long>> =
        context.chatLocalStore().data.map { parseStarred(it[starredKey])[conversationId] ?: emptySet() }

    fun starredConversationIds(): Flow<Set<Int>> =
        context.chatLocalStore().data.map { parseStarred(it[starredKey]).keys }

    fun noteFor(conversationId: Int): Flow<String> =
        context.chatLocalStore().data.map { parseNotes(it[chatNotesKey])[conversationId].orEmpty() }

    val allNotes: Flow<Map<Int, String>> =
        context.chatLocalStore().data.map { parseNotes(it[chatNotesKey]) }

    val noteConversationIds: Flow<Set<Int>> =
        allNotes.map { it.keys }

    fun recentOpenIds(): Flow<List<Int>> =
        context.chatLocalStore().data.map { parseIdsList(it[recentOpenKey]) }

    suspend fun recordRecentOpen(conversationId: Int) {
        if (conversationId <= 0) return
        context.chatLocalStore().edit { prefs ->
            val list = parseIdsList(prefs[recentOpenKey]).toMutableList()
            list.remove(conversationId)
            list.add(0, conversationId)
            while (list.size > MAX_RECENT) list.removeAt(list.lastIndex)
            prefs[recentOpenKey] = list.joinToString(",")
        }
    }

    suspend fun setNote(conversationId: Int, note: String) {
        if (conversationId <= 0) return
        context.chatLocalStore().edit { prefs ->
            val map = parseNotes(prefs[chatNotesKey]).toMutableMap()
            val trimmed = note.trim()
            if (trimmed.isEmpty()) map.remove(conversationId) else map[conversationId] = trimmed
            prefs[chatNotesKey] = encodeNotes(map)
        }
    }

    suspend fun clearStars(conversationId: Int) {
        if (conversationId <= 0) return
        context.chatLocalStore().edit { prefs ->
            val map = parseStarred(prefs[starredKey]).toMutableMap()
            map.remove(conversationId)
            prefs[starredKey] = encodeStarred(map)
        }
    }

    suspend fun toggleStar(conversationId: Int, messageId: Long) {
        if (conversationId <= 0 || messageId <= 0L) return
        context.chatLocalStore().edit { prefs ->
            val map = parseStarred(prefs[starredKey]).toMutableMap()
            val set = map[conversationId]?.toMutableSet() ?: mutableSetOf()
            if (!set.add(messageId)) set.remove(messageId)
            if (set.isEmpty()) map.remove(conversationId) else map[conversationId] = set
            prefs[starredKey] = encodeStarred(map)
        }
    }

    fun hasVaultPinFlow(): Flow<Boolean> =
        context.chatLocalStore().data.map { !(it[vaultPinKey].isNullOrBlank()) }

    private fun parseIds(raw: String?): Set<Int> =
        raw
            ?.split(',')
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?.filter { it > 0 }
            ?.toSet() ?: emptySet()

    private fun encodeIds(ids: Set<Int>): String = ids.sorted().joinToString(",")

    private fun parseIdsList(raw: String?): List<Int> =
        raw
            ?.split(',')
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?.filter { it > 0 }
            ?: emptyList()

    private fun parseAccentMap(raw: String?): Map<Int, Int> {
        if (raw.isNullOrBlank()) return emptyMap()
        return raw.split(';').mapNotNull { part ->
            val kv = part.split(':')
            if (kv.size != 2) return@mapNotNull null
            val cid = kv[0].toIntOrNull() ?: return@mapNotNull null
            val aid = kv[1].toIntOrNull() ?: return@mapNotNull null
            cid to aid
        }.toMap()
    }

    private fun encodeAccentMap(map: Map<Int, Int>): String =
        map.entries.sortedBy { it.key }.joinToString(";") { "${it.key}:${it.value}" }

    private fun parseFolders(raw: String?): List<ChatFolder> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split('|').mapNotNull { chunk ->
            val parts = chunk.split(':')
            if (parts.size < 2) return@mapNotNull null
            val id = parts[0]
            val name = parts[1]
            when {
                parts.size >= 4 -> {
                    val colorId = parts[2].toIntOrNull() ?: 0
                    val ids = parseIds(parts[3])
                    ChatFolder(id, name, ids, colorId)
                }
                parts.size == 3 -> {
                    val third = parts[2]
                    val maybeColor = third.toIntOrNull()
                    if (maybeColor != null && third.length <= 2 && !third.contains(',')) {
                        ChatFolder(id, name, emptySet(), maybeColor.coerceIn(0, 9))
                    } else {
                        ChatFolder(id, name, parseIds(third), 0)
                    }
                }
                else -> ChatFolder(id, name, emptySet(), 0)
            }
        }
    }

    private fun encodeFolders(list: List<ChatFolder>): String =
        list.joinToString("|") { f ->
            val color = f.colorId.coerceIn(0, 9)
            "${f.id}:${f.name.replace(':', ' ')}:$color:${encodeIds(f.conversationIds)}"
        }

    private fun parseStarred(raw: String?): Map<Int, Set<Long>> {
        if (raw.isNullOrBlank()) return emptyMap()
        return raw.split('|').mapNotNull { chunk ->
            val parts = chunk.split(':')
            if (parts.size != 2) return@mapNotNull null
            val cid = parts[0].toIntOrNull() ?: return@mapNotNull null
            val mids =
                parts[1]
                    .split(',')
                    .mapNotNull { it.trim().toLongOrNull() }
                    .filter { it > 0L }
                    .toSet()
            if (mids.isEmpty()) return@mapNotNull null
            cid to mids
        }.toMap()
    }

    private fun encodeStarred(map: Map<Int, Set<Long>>): String =
        map.entries
            .sortedBy { it.key }
            .joinToString("|") { (cid, mids) -> "$cid:${mids.sorted().joinToString(",")}" }

    private fun parseNotes(raw: String?): Map<Int, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return raw.split('|').mapNotNull { chunk ->
            val sep = chunk.indexOf(':')
            if (sep <= 0) return@mapNotNull null
            val cid = chunk.substring(0, sep).toIntOrNull() ?: return@mapNotNull null
            val text = chunk.substring(sep + 1).trim()
            if (text.isEmpty()) return@mapNotNull null
            cid to text
        }.toMap()
    }

    private fun encodeNotes(map: Map<Int, String>): String =
        map.entries
            .sortedBy { it.key }
            .joinToString("|") { (cid, text) -> "$cid:${text.replace('|', ' ').replace(':', ' ')}" }

    companion object {
        const val MAX_PINS = 5
        const val MAX_RECENT = 5

        fun encodeFoldersPayload(list: List<ChatFolder>): String =
            list.joinToString("|") { f ->
                val color = f.colorId.coerceIn(0, 9)
                "${f.id}:${f.name.replace(':', ' ')}:$color:${f.conversationIds.sorted().joinToString(",")}"
            }
    }
}
