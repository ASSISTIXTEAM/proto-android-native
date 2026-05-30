package org.assistix.proto.nativeapp.data

/**
 * While true, local notifications for replayed SSE events are suppressed
 * (first seconds after login / reconnect).
 */
object ProtoRealtimeCatchUp {
    @Volatile
    var active: Boolean = false
}
