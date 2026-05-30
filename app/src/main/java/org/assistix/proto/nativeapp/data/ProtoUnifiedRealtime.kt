package org.assistix.proto.nativeapp.data

import org.json.JSONObject

/**
 * Единый realtime-слой: WebSocket (`/ws`) с fallback на SSE (`/api/stream.php`).
 */
object ProtoUnifiedRealtime {
    @Volatile
    var realtimeConnected: Boolean = false

    @Deprecated("Use realtimeConnected", ReplaceWith("realtimeConnected"))
    var sseConnected: Boolean
        get() = realtimeConnected
        set(value) {
            realtimeConnected = value
        }

    data class Handlers(
        val onChatEvent: () -> Unit = {},
        val onTyping: (conversationId: Int, userId: Int) -> Unit = { _, _ -> },
        val onPeerRead: (conversationId: Int, through: Long, readAt: Long, readerId: Int) -> Unit = { _, _, _, _ -> },
        val onWebrtc: (conversationId: Int) -> Unit = {},
        val onPin: (conversationId: Int, messageId: Long) -> Unit = { _, _ -> },
    )

    fun dispatch(raw: JSONObject, handlers: Handlers) {
        val type = raw.optString("type", "")
        val data = raw.optJSONObject("data") ?: JSONObject()
        when (type) {
            "message", "member_joined", "poll_vote", "message_deleted", "message_edited" -> handlers.onChatEvent()
            "typing" -> {
                handlers.onChatEvent()
                val cid = data.optInt("conversation_id", 0)
                val uid = data.optInt("user_id", 0)
                if (cid > 0 && uid > 0) handlers.onTyping(cid, uid)
            }
            "read" -> {
                handlers.onChatEvent()
                val cid = data.optInt("conversation_id", 0)
                val through = data.optLong("through_message_id", 0)
                val readAt = data.optLong("read_at", 0)
                val readerId = data.optInt("user_id", 0)
                if (cid > 0 && through > 0 && readerId > 0) {
                    handlers.onPeerRead(cid, through, readAt, readerId)
                }
            }
            "webrtc" -> {
                val cid = data.optInt("conversation_id", 0)
                if (cid > 0) handlers.onWebrtc(cid)
            }
            "pin" -> {
                handlers.onChatEvent()
                val cid = data.optInt("conversation_id", 0)
                val mid = data.optLong("message_id", 0)
                if (cid > 0) handlers.onPin(cid, mid)
            }
        }
    }
}
