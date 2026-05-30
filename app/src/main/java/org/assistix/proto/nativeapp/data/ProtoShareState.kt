package org.assistix.proto.nativeapp.data

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

data class ProtoSharePayload(
    val text: String? = null,
    val imageUris: List<Uri> = emptyList(),
) {
    val isEmpty: Boolean
        get() = text.isNullOrBlank() && imageUris.isEmpty()
}

object ProtoShareState {
    var active by mutableStateOf(false)
    var payload by mutableStateOf<ProtoSharePayload?>(null)
    var selectedChatIds by mutableStateOf(setOf<Int>())

    @Volatile
    private var directTarget: Pair<Int, ProtoSharePayload>? = null

    fun start(data: ProtoSharePayload) {
        if (data.isEmpty) return
        payload = data
        active = true
        selectedChatIds = emptySet()
    }

    fun queueDirect(conversationId: Int, data: ProtoSharePayload) {
        if (conversationId <= 0 || data.isEmpty) return
        directTarget = conversationId to data
    }

    fun consumeDirect(): Pair<Int, ProtoSharePayload>? {
        val v = directTarget ?: return null
        directTarget = null
        return v
    }

    fun toggleTarget(conversationId: Int) {
        if (conversationId <= 0) return
        val cur = selectedChatIds
        selectedChatIds =
            if (conversationId in cur) cur - conversationId else cur + conversationId
    }

    fun clear() {
        active = false
        payload = null
        selectedChatIds = emptySet()
        directTarget = null
    }
}
