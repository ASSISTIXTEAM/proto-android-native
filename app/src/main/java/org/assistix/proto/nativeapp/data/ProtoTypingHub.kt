package org.assistix.proto.nativeapp.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Who is typing in the active conversation (updated from SSE + poll). */
object ProtoTypingHub {
    var conversationId by mutableIntStateOf(0)
    var userIds by mutableStateOf<Set<Int>>(emptySet())
    var recordingUserIds by mutableStateOf<Set<Int>>(emptySet())

    fun update(cid: Int, ids: Set<Int>, recording: Set<Int> = emptySet()) {
        conversationId = cid
        userIds = ids
        recordingUserIds = recording
    }

    fun clear(cid: Int) {
        if (conversationId == cid) {
            userIds = emptySet()
            recordingUserIds = emptySet()
        }
    }
}
