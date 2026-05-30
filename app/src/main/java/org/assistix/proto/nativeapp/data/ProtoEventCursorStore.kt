package org.assistix.proto.nativeapp.data

import android.content.Context

/** Persists last SSE/WS proto_events id per user so reconnect does not replay the full backlog. */
class ProtoEventCursorStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun get(userId: Int): Long {
        if (userId <= 0) return 0L
        return prefs.getLong(key(userId), 0L)
    }

    fun save(userId: Int, eventId: Long) {
        if (userId <= 0 || eventId <= 0L) return
        val k = key(userId)
        val prev = prefs.getLong(k, 0L)
        if (eventId <= prev) return
        prefs.edit().putLong(k, eventId).apply()
    }

    fun clear(userId: Int = 0) {
        if (userId > 0) {
            prefs.edit().remove(key(userId)).apply()
            return
        }
        prefs.edit().clear().apply()
    }

    private fun key(userId: Int) = "sse_since_$userId"

    companion object {
        private const val PREFS = "proto_event_cursor"
    }
}
