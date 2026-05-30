package org.assistix.proto.nativeapp.ui

import org.assistix.proto.nativeapp.data.MsgItem
import org.assistix.proto.nativeapp.data.SmartSavedFilter

enum class ChatSearchFilter {
    All,
    Text,
    Media,
    Links,
    Voice,
}

object ChatMessageSearch {
    fun messageMatches(
        msg: MsgItem,
        query: String,
        filter: ChatSearchFilter,
        voiceTranscript: String? = null,
    ): Boolean {
        val q = query.trim()
        val typeOk =
            when (filter) {
                ChatSearchFilter.All -> true
                ChatSearchFilter.Text -> msg.messageType == "text" || msg.bodyRaw.isNotBlank() || msg.body.isNotBlank()
                ChatSearchFilter.Media -> SmartSavedFilter.matches(msg, SmartSavedFilter.Media)
                ChatSearchFilter.Links -> SmartSavedFilter.matches(msg, SmartSavedFilter.Links)
                ChatSearchFilter.Voice -> SmartSavedFilter.matches(msg, SmartSavedFilter.Voice)
            }
        if (!typeOk) return false
        if (q.isEmpty()) return typeOk
        val raw = msg.bodyRaw.ifBlank { msg.body }
        val mediaName = msg.mediaName.orEmpty()
        val transcript = voiceTranscript.orEmpty()
        return raw.contains(q, ignoreCase = true) ||
            msg.body.contains(q, ignoreCase = true) ||
            mediaName.contains(q, ignoreCase = true) ||
            transcript.contains(q, ignoreCase = true)
    }

}
