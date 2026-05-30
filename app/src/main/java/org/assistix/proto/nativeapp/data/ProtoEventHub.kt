package org.assistix.proto.nativeapp.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue

/** Глобальный счётчик для обновления списка чатов по SSE. */
object ProtoEventHub {
    var tick by mutableIntStateOf(0)
    var lastRealtimeAt by mutableLongStateOf(0L)

    fun bump() {
        tick++
        lastRealtimeAt = System.currentTimeMillis()
    }
}
