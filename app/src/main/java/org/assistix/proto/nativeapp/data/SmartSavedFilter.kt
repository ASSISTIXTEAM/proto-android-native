package org.assistix.proto.nativeapp.data

enum class SmartSavedFilter {
    All,
    Links,
    Media,
    Voice,
    ;

    companion object {
        fun matches(msg: MsgItem, filter: SmartSavedFilter): Boolean {
            if (filter == All) return true
            val raw = msg.bodyRaw.ifBlank { msg.body }
            return when (filter) {
                Links -> raw.contains("http://", true) || raw.contains("https://", true) || raw.contains("www.", true)
                Media -> msg.albumMeta != null || msg.isImageMedia() || msg.mediaKind == "video"
                Voice -> msg.mediaKind == "voice" || msg.mediaMime?.startsWith("audio/") == true
                All -> true
            }
        }
    }
}
