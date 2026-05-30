package org.assistix.proto.nativeapp.data

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

class ProtoWebSocket(
    private val wsUrl: String,
    private val token: String,
    private val onEvent: (JSONObject) -> Unit,
    private val onOpen: () -> Unit,
    private val onClosed: () -> Unit,
) {
    @Volatile
    private var running = false

    private var socket: WebSocket? = null

    private val client =
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(25, TimeUnit.SECONDS)
            .build()

    fun start() {
        stop()
        running = true
        val enc = URLEncoder.encode(token, StandardCharsets.UTF_8.name())
        val url = wsUrl.trimEnd('/') + "?token=$enc"
        val req =
            Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .header("X-Proto-Session", token)
                .build()
        socket =
            client.newWebSocket(
                req,
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        if (!running) {
                            webSocket.close(1000, null)
                            return
                        }
                        onOpen()
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        if (!running) return
                        try {
                            val raw = JSONObject(text)
                            if (raw.optString("type") == "hello") return
                            onEvent(raw)
                        } catch (_: Exception) {
                        }
                    }

                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        webSocket.close(code, reason)
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        onClosed()
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        onClosed()
                    }
                },
            )
    }

    fun stop() {
        running = false
        socket?.close(1000, null)
        socket = null
    }
}
