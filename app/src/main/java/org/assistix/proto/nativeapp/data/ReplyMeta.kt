package org.assistix.proto.nativeapp.data

import org.json.JSONObject

data class ReplyMeta(
    val messageId: Long,
    val preview: String,
    val senderId: Int = 0,
)

fun replyToJson(r: ReplyMeta): String =
    JSONObject()
        .put("message_id", r.messageId)
        .put("preview", r.preview)
        .put("sender_id", r.senderId)
        .toString()

fun parseReplyJson(raw: String?): ReplyMeta? {
    if (raw.isNullOrBlank()) return null
    return try {
        val o = JSONObject(raw)
        val id = o.optLong("message_id", 0)
        val preview = o.optString("preview", "").trim()
        if (id <= 0 || preview.isEmpty()) return null
        ReplyMeta(id, preview, o.optInt("sender_id", 0))
    } catch (_: Exception) {
        null
    }
}
