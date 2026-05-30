package org.assistix.proto.nativeapp.data

import org.assistix.proto.nativeapp.data.resolveDisplayName

/** Rich context for Pulse / ask_chat inside an open conversation. */
data class PulseChatContext(
    val headerLines: List<String>,
    val messageLines: List<String>,
) {
    fun previewLines(dialogTail: List<String> = emptyList()): List<String> =
        buildList {
            addAll(headerLines)
            addAll(messageLines.takeLast(40))
            dialogTail.forEach { add(it) }
        }
}

object PulseChatContextBuilder {
    fun build(
        chatTitle: String,
        chatKind: String,
        isGroup: Boolean,
        isChannel: Boolean,
        isSaved: Boolean,
        peerUserId: Int,
        peer: UserProfile?,
        chatNote: String,
        msgs: List<MsgItem>,
        myUserId: Int,
    ): PulseChatContext {
        val header = buildList {
            add("Conversation: ${chatTitle.trim().ifBlank { "Chat" }}")
            add("Type: ${when {
                isSaved -> "saved messages"
                isChannel -> "channel"
                isGroup -> "group"
                else -> "direct message"
            }}")
            if (peer != null) {
                val name = resolveDisplayName(peer.displayName, peer.nick)
                add("Contact: $name (@${peer.nick})")
                if (peer.bio.isNotBlank()) add("Bio: ${peer.bio.take(400)}")
                if (peer.statusText.isNotBlank()) {
                    add("Status: ${peer.statusEmoji} ${peer.statusText}".trim())
                }
                if (peer.lastSeenSec > 0) add("Last seen (unix): ${peer.lastSeenSec}")
            } else if (peerUserId > 0) {
                add("Contact user id: $peerUserId")
            }
            if (chatNote.isNotBlank()) add("Your private note: ${chatNote.take(300)}")
        }
        val lines =
            msgs.takeLast(35).map { m ->
                val who =
                    when {
                        m.mine -> "You"
                        m.senderName.isNotBlank() -> m.senderName
                        m.senderId == peerUserId && peer != null -> peer.nick
                        m.senderId > 0 -> "user${m.senderId}"
                        else -> "Them"
                    }
                val body = m.bodyRaw.ifBlank { m.body }.replace('\n', ' ').trim().take(220)
                val kind = if (m.messageType != "text") " [${m.messageType}]" else ""
                "$who$kind: $body"
            }
        return PulseChatContext(header, lines)
    }
}
