package org.assistix.proto.nativeapp.data

import org.json.JSONObject

data class ForwardMeta(
    val fromLabel: String,
    val bodySnippet: String,
    val fromUserId: Int = 0,
    val originalMessageId: Long = 0,
)

data class CallMeta(
    val direction: String,
    val status: String,
    val video: Boolean,
    val durationSec: Int,
    val peerLabel: String,
) {
    fun displayText(minePerspective: Boolean): String {
        val dirLabel =
            when {
                direction == "in" && !minePerspective -> "Incoming call"
                direction == "out" && minePerspective -> "Outgoing call"
                direction == "in" -> "Incoming call"
                else -> "Outgoing call"
            }
        val kind = if (video) "video" else "audio"
        val statusLabel =
            when (status) {
                "answered" -> {
                    val m = durationSec / 60
                    val s = durationSec % 60
                    val dur = if (m > 0) "${m}m ${s}s" else "${s}s"
                    "answered · $dur"
                }
                "missed" -> "missed"
                "declined" -> "declined"
                "cancelled" -> "cancelled"
                else -> status
            }
        return "$dirLabel ($kind) — $statusLabel"
    }

    companion object {
        fun fromJson(raw: String): CallMeta? {
            return try {
                val root = JSONObject(raw)
                val o = root.optJSONObject("proto_call") ?: return null
                CallMeta(
                    direction = o.optString("direction", "out"),
                    status = o.optString("status", "missed"),
                    video = o.optBoolean("video", false),
                    durationSec = o.optInt("duration_sec", 0),
                    peerLabel = o.optString("peer_label", ""),
                )
            } catch (_: Exception) {
                null
            }
        }

        fun toJson(meta: CallMeta): String =
            JSONObject()
                .put(
                    "proto_call",
                    JSONObject()
                        .put("direction", meta.direction)
                        .put("status", meta.status)
                        .put("video", meta.video)
                        .put("duration_sec", meta.durationSec)
                        .put("peer_label", meta.peerLabel),
                )
                .toString()
    }
}
