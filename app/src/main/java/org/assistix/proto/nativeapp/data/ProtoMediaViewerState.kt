package org.assistix.proto.nativeapp.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object ProtoMediaViewerState {
    var active by mutableStateOf(false)
    var items by mutableStateOf<List<MediaViewerItem>>(emptyList())
    var startIndex by mutableIntStateOf(0)
    var fromLabel by mutableStateOf("")
    var sourceMessage by mutableStateOf<MsgItem?>(null)

    fun open(
        gallery: List<MediaViewerItem>,
        index: Int,
        label: String = "",
        message: MsgItem? = null,
    ) {
        if (gallery.isEmpty()) return
        items = gallery
        startIndex = index.coerceIn(0, gallery.lastIndex)
        fromLabel = label
        sourceMessage = message
        active = true
    }

    fun close() {
        active = false
        items = emptyList()
        startIndex = 0
        fromLabel = ""
        sourceMessage = null
    }
}
