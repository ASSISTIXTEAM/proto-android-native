package org.assistix.proto.nativeapp.widget

import org.assistix.proto.nativeapp.data.ConvItem
import org.assistix.proto.nativeapp.ui.formatStoredMessagePreview
import org.json.JSONArray
import org.json.JSONObject

data class WidgetChatEntry(
    val id: Int,
    val title: String,
    val preview: String,
    val unreadCount: Int,
    val kind: String,
    val peerUserId: Int,
    val updatedAt: Long,
) {
    fun displayTitle(): String = title.ifBlank { "Chat #$id" }

    fun previewShort(max: Int = 72): String {
        val p = preview.replace('\n', ' ').trim()
        return if (p.length <= max) p else p.take(max - 1) + "…"
    }

    fun timeAgoLabel(): String {
        if (updatedAt <= 0L) return ""
        val diff = (System.currentTimeMillis() / 1000) - updatedAt
        return when {
            diff < 55 -> "сейчас"
            diff < 3600 -> "${diff / 60} мин"
            diff < 86_400 -> "${diff / 3600} ч"
            diff < 604_800 -> "${diff / 86_400} д"
            else -> "давно"
        }
    }
}

data class WidgetSnapshot(
    val loggedIn: Boolean,
    val userNick: String,
    val totalUnread: Int,
    val updatedAt: Long,
    val chats: List<WidgetChatEntry>,
    val aiBrief: String = "",
    val aiBriefAt: Long = 0L,
) {
    companion object {
        fun empty(): WidgetSnapshot =
            WidgetSnapshot(
                loggedIn = false,
                userNick = "",
                totalUnread = 0,
                updatedAt = 0L,
                chats = emptyList(),
            )

        fun fromConversations(nick: String?, items: List<ConvItem>): WidgetSnapshot {
            val sorted =
                items
                    .filter { it.kind != "saved" }
                    .sortedWith(
                        compareByDescending<ConvItem> { it.unreadCount > 0 }
                            .thenByDescending { it.updatedAt },
                    )
            val chats =
                sorted.map {
                    WidgetChatEntry(
                        id = it.id,
                        title = it.title.ifBlank { it.peerDisplayName }.ifBlank { "Chat" },
                        preview = formatStoredMessagePreview(it.preview, it.lastSenderId, 0),
                        unreadCount = it.unreadCount,
                        kind = it.kind,
                        peerUserId = it.peerUserId,
                        updatedAt = it.updatedAt,
                    )
                }
            return WidgetSnapshot(
                loggedIn = true,
                userNick = nick?.trim().orEmpty(),
                totalUnread = chats.sumOf { it.unreadCount },
                updatedAt = System.currentTimeMillis() / 1000,
                chats = chats,
                aiBrief = "",
                aiBriefAt = 0L,
            )
        }

        fun withAiBrief(base: WidgetSnapshot, brief: String): WidgetSnapshot =
            base.copy(
                aiBrief = brief.trim().take(420),
                aiBriefAt = System.currentTimeMillis() / 1000,
            )
    }
}

object WidgetSnapshotCodec {
    private const val KEY_LOGGED = "logged_in"
    private const val KEY_NICK = "nick"
    private const val KEY_UNREAD = "total_unread"
    private const val KEY_UPDATED = "updated_at"
    private const val KEY_CHATS = "chats"
    private const val KEY_AI_BRIEF = "ai_brief"
    private const val KEY_AI_AT = "ai_brief_at"

    fun encode(s: WidgetSnapshot): String {
        val root = JSONObject()
        root.put(KEY_LOGGED, s.loggedIn)
        root.put(KEY_NICK, s.userNick)
        root.put(KEY_UNREAD, s.totalUnread)
        root.put(KEY_UPDATED, s.updatedAt)
        val arr = JSONArray()
        s.chats.forEach { c ->
            arr.put(
                JSONObject()
                    .put("id", c.id)
                    .put("title", c.title)
                    .put("preview", c.preview)
                    .put("unread", c.unreadCount)
                    .put("kind", c.kind)
                    .put("peer", c.peerUserId)
                    .put("at", c.updatedAt),
            )
        }
        root.put(KEY_CHATS, arr)
        root.put(KEY_AI_BRIEF, s.aiBrief)
        root.put(KEY_AI_AT, s.aiBriefAt)
        return root.toString()
    }

    fun decode(raw: String?): WidgetSnapshot {
        if (raw.isNullOrBlank()) return WidgetSnapshot.empty()
        return try {
            val root = JSONObject(raw)
            val arr = root.optJSONArray(KEY_CHATS) ?: JSONArray()
            val chats = mutableListOf<WidgetChatEntry>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                chats +=
                    WidgetChatEntry(
                        id = o.optInt("id"),
                        title = o.optString("title"),
                        preview = o.optString("preview"),
                        unreadCount = o.optInt("unread"),
                        kind = o.optString("kind", "dm"),
                        peerUserId = o.optInt("peer"),
                        updatedAt = o.optLong("at"),
                    )
            }
            WidgetSnapshot(
                loggedIn = root.optBoolean(KEY_LOGGED),
                userNick = root.optString(KEY_NICK),
                totalUnread = root.optInt(KEY_UNREAD),
                updatedAt = root.optLong(KEY_UPDATED),
                chats = chats,
                aiBrief = root.optString(KEY_AI_BRIEF),
                aiBriefAt = root.optLong(KEY_AI_AT),
            )
        } catch (_: Exception) {
            WidgetSnapshot.empty()
        }
    }
}
