package org.assistix.proto.nativeapp.ui

import org.assistix.proto.nativeapp.data.MsgItem

object ChatAssistixPreview {
    fun lines(
        messages: List<MsgItem>,
        allChat: Boolean = true,
        unreadOnly: Boolean = false,
        maxLines: Int = 36,
    ): List<String> {
        val pool =
            messages.filter {
                it.messageType == "text" &&
                    !it.isE2e &&
                    (it.bodyRaw.isNotBlank() || it.body.isNotBlank())
            }
        val filtered =
            when {
                unreadOnly -> pool.filter { !it.mine }
                else -> pool
            }
        return filtered
            .takeLast(maxLines)
            .map { m ->
                val who = if (m.mine) "You" else "Them"
                val body = m.bodyRaw.ifBlank { m.body }.trim().replace('\n', ' ')
                "$who: ${body.take(220)}"
            }
    }

    fun selectionLines(selected: List<MsgItem>): List<String> =
        selected
            .filter { it.messageType == "text" && !it.isE2e }
            .map { m ->
                val who = if (m.mine) "You" else "Them"
                "$who: ${m.bodyRaw.ifBlank { m.body }.trim().replace('\n', ' ').take(400)}"
            }
}
