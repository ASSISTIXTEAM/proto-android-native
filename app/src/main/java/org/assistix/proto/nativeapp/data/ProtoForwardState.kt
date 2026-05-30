package org.assistix.proto.nativeapp.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object ProtoForwardState {
    var active by mutableStateOf(false)
    var messages by mutableStateOf<List<MsgItem>>(emptyList())
    val message: MsgItem? get() = messages.firstOrNull()
    var fromLabel by mutableStateOf("")
    var selectedChatIds by mutableStateOf(setOf<Int>())

    fun start(msg: MsgItem, label: String) {
        start(listOf(msg), label)
    }

    fun start(msgs: List<MsgItem>, label: String) {
        messages = msgs.filter { it.id != 0L || it.body.isNotBlank() || it.bodyRaw.isNotBlank() }
        fromLabel = label
        active = messages.isNotEmpty()
        selectedChatIds = emptySet()
    }

    fun toggleTarget(conversationId: Int) {
        if (conversationId <= 0) return
        val cur = selectedChatIds
        selectedChatIds =
            if (conversationId in cur) {
                cur - conversationId
            } else {
                cur + conversationId
            }
    }

    fun clear() {
        active = false
        messages = emptyList()
        fromLabel = ""
        selectedChatIds = emptySet()
    }
}

object ProtoChatSelectionState {
    var active by mutableStateOf(false)
}

object ProtoActiveChat {
    var conversationId: Int = 0
}
