package org.assistix.proto.nativeapp.data

import org.json.JSONObject

data class ChannelCardMeta(
    val nick: String,
    val title: String,
    val description: String,
    val verified: Boolean,
    val publicUrl: String,
    val conversationId: Int = 0,
    val avatarUploadId: String? = null,
    val subscribed: Boolean = false,
) {
    fun toMessageBody(): String {
        val ch =
            JSONObject()
                .put("nick", nick)
                .put("title", title)
                .put("description", description)
                .put("verified", verified)
                .put("public_url", publicUrl)
                .put("conversation_id", conversationId)
                .put("subscribed", subscribed)
        avatarUploadId?.let { ch.put("avatar_upload_id", it) }
        return JSONObject().put("proto_channel", ch).toString()
    }

    companion object {
        fun fromChannel(hit: ChannelHit): ChannelCardMeta =
            ChannelCardMeta(
                nick = hit.nick,
                title = hit.title,
                description = hit.description,
                verified = hit.verified,
                publicUrl = hit.publicUrl.ifBlank { "https://proto.su/c/@${hit.nick}" },
                conversationId = hit.conversationId,
                avatarUploadId = hit.avatarUploadId,
                subscribed = hit.subscribed,
            )

        fun fromJson(raw: String): ChannelCardMeta? {
            return try {
                val j = JSONObject(raw.trim())
                val ch = j.optJSONObject("proto_channel") ?: return null
                val nick = ch.optString("nick", "").trim()
                if (nick.isBlank()) return null
                ChannelCardMeta(
                    nick = nick,
                    title = ch.optString("title", nick).trim(),
                    description = ch.optString("description", "").trim(),
                    verified = ch.optBoolean("verified", false),
                    publicUrl = ch.optString("public_url", "").trim().ifBlank { "https://proto.su/c/@$nick" },
                    conversationId = ch.optInt("conversation_id", 0),
                    avatarUploadId = ch.optString("avatar_upload_id", "").trim().ifBlank { null },
                    subscribed = ch.optBoolean("subscribed", false),
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}
