package org.assistix.proto.nativeapp.data

import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.assistix.proto.nativeapp.BuildConfig
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Prefers WebSocket (`wss://…/ws`); falls back to SSE `/api/stream.php` when WS is unavailable.
 */
class ProtoRealtimeHub(
    context: Context,
    private val userIdProvider: () -> Int,
    private val onEvent: (JSONObject) -> Unit,
) {
    private val cursorStore = ProtoEventCursorStore(context.applicationContext)
    private val sse =
        ProtoRealtime(
            onEvent = { raw ->
                val eventId = raw.optLong("id", 0)
                val uid = userIdProvider()
                if (eventId > 0 && uid > 0) {
                    cursorStore.save(uid, eventId)
                }
                onEvent(raw)
            },
            initialSinceProvider = {
                val uid = userIdProvider()
                if (uid > 0) cursorStore.get(uid) else 0L
            },
            onSinceAdvanced = { since ->
                val uid = userIdProvider()
                if (since > 0 && uid > 0) {
                    cursorStore.save(uid, since)
                }
            },
        )
    private var webSocket: ProtoWebSocket? = null
    private val usingWs = AtomicBoolean(false)

    @Volatile
    private var sessionToken: String? = null

    @Volatile
    var activeTransport: String = "none"

    private val configClient =
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()

    suspend fun start(token: String) {
        stop()
        sessionToken = token
        val wsUrl = resolveWsUrl()
        if (!wsUrl.isNullOrBlank()) {
            val opened = CompletableDeferred<Boolean>()
            val ws =
                ProtoWebSocket(
                    wsUrl = wsUrl,
                    token = token,
                    onEvent = onEvent,
                    onOpen = {
                        usingWs.set(true)
                        activeTransport = "websocket"
                        ProtoUnifiedRealtime.realtimeConnected = true
                        sse.stop()
                        opened.complete(true)
                    },
                    onClosed = {
                        val wasWs = usingWs.getAndSet(false)
                        if (!wasWs) return@ProtoWebSocket
                        ProtoUnifiedRealtime.realtimeConnected = false
                        val t = sessionToken
                        if (t != null) {
                            activeTransport = "sse"
                            ProtoUnifiedRealtime.realtimeConnected = true
                            sse.start(t)
                        }
                    },
                )
            webSocket = ws
            ws.start()
            val ok = withTimeoutOrNull(2200) { opened.await() } == true
            if (ok) return
            ws.stop()
            webSocket = null
            usingWs.set(false)
        }
        startSseOnly(token)
    }

    private fun startSseOnly(token: String) {
        activeTransport = "sse"
        ProtoUnifiedRealtime.realtimeConnected = true
        sse.start(token)
    }

    fun stop() {
        sessionToken = null
        usingWs.set(false)
        webSocket?.stop()
        webSocket = null
        sse.stop()
        activeTransport = "none"
        ProtoUnifiedRealtime.realtimeConnected = false
    }

    private suspend fun resolveWsUrl(): String? {
        val cached = cachedWsUrl
        if (cached != null) return cached.ifBlank { null }
        val fromBuild = BuildConfig.WS_ORIGIN.trim()
        if (fromBuild.isNotBlank()) {
            cachedWsUrl = fromBuild
            return fromBuild
        }
        return withContext(Dispatchers.IO) {
            runCatching {
                val req =
                    Request.Builder()
                        .url(ProtoApiOrigin.url("/api/config.php"))
                        .get()
                        .build()
                configClient.newCall(req).execute().use { res ->
                    if (!res.isSuccessful) return@runCatching null
                    val j = JSONObject(res.body?.string() ?: return@runCatching null)
                    j.optString("ws_url").takeIf { it.isNotBlank() }
                }
            }.getOrNull().also { cachedWsUrl = it ?: "" }
        }
    }

    companion object {
        @Volatile
        private var cachedWsUrl: String? = null
    }
}
