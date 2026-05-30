package org.assistix.proto.nativeapp.data

import okhttp3.OkHttpClient
import okhttp3.Request
import org.assistix.proto.nativeapp.BuildConfig
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class ProtoRealtime(
    private val onEvent: (JSONObject) -> Unit,
    private val initialSinceProvider: () -> Long = { 0L },
    private val onSinceAdvanced: ((Long) -> Unit)? = null,
) {
    @Volatile
    private var running = false
    private var thread: Thread? = null
    private var since = 0L

    private val client =
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()

    fun start(token: String) {
        stop()
        since = initialSinceProvider().coerceAtLeast(0L)
        running = true
        thread =
            Thread {
                while (running) {
                    try {
                        val req =
                            Request.Builder()
                                .url(ProtoApiOrigin.url("/api/stream.php?since=$since"))
                                .header("Authorization", "Bearer $token")
                                .header("X-Proto-Session", token)
                                .header("Accept", "text/event-stream")
                                .get()
                                .build()
                        client.newCall(req).execute().use { res ->
                            if (!res.isSuccessful) {
                                Thread.sleep(2500)
                                return@use
                            }
                            val reader = BufferedReader(InputStreamReader(res.body?.byteStream() ?: return@use))
                            var line: String?
                            val data = StringBuilder()
                            while (running) {
                                line = reader.readLine() ?: break
                                if (line.startsWith("id:")) {
                                    val next = line.removePrefix("id:").trim().toLongOrNull() ?: since
                                    if (next > since) {
                                        since = next
                                        onSinceAdvanced?.invoke(since)
                                    }
                                } else if (line.startsWith("data:")) {
                                    data.append(line.removePrefix("data:").trim())
                                } else if (line.isEmpty() && data.isNotEmpty()) {
                                    try {
                                        onEvent(JSONObject(data.toString()))
                                    } catch (_: Exception) {
                                    }
                                    data.clear()
                                }
                            }
                        }
                    } catch (_: Exception) {
                        Thread.sleep(2000)
                    }
                }
            }.apply { isDaemon = true; start() }
    }

    fun stop() {
        running = false
        thread?.interrupt()
        thread = null
    }
}
