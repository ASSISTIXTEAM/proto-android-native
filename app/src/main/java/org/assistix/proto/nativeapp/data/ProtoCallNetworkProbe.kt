package org.assistix.proto.nativeapp.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

data class CallNetworkProbeResult(
    val rttMs: Int,
    val qualityBars: Int,
    val label: String,
    val ok: Boolean,
) {
    companion object {
        val Unknown =
            CallNetworkProbeResult(
                rttMs = -1,
                qualityBars = 0,
                label = "",
                ok = false,
            )
    }
}

/**
 * Быстрая проверка сети перед звонком: RTT до любого доступного API (не только proto.su).
 */
object ProtoCallNetworkProbe {
    private const val TAG = "ProtoCallProbe"

    suspend fun run(): CallNetworkProbeResult =
        withContext(Dispatchers.IO) {
            val urls =
                buildList {
                    for (origin in ProtoApiOrigin.orderedOrigins()) {
                        add(ProtoApiOrigin.url("/api/health.php", origin))
                    }
                    val host = org.assistix.proto.nativeapp.BuildConfig.TURN_HOST.trim()
                    if (host.isNotBlank()) {
                        add("http://$host/api/health.php")
                    }
                }.distinct()
            val samples = mutableListOf<Int>()
            for (url in urls) {
                repeat(2) { attempt ->
                    val rtt = measureRtt(url)
                    if (rtt >= 0) samples.add(rtt)
                    if (attempt < 1) Thread.sleep(60)
                }
                if (samples.size >= 3) break
            }
            if (samples.isEmpty()) {
                Log.w(TAG, "probe failed on all origins")
                return@withContext CallNetworkProbeResult(-1, 2, "", true)
            }
            val avg = samples.average().roundToInt()
            val bars =
                when {
                    avg < 120 -> 4
                    avg < 250 -> 3
                    avg < 500 -> 2
                    avg < 900 -> 1
                    else -> 1
                }
            val ok = avg < 2_500
            Log.d(TAG, "probe rtt=$avg bars=$bars ok=$ok samples=${samples.size}")
            CallNetworkProbeResult(avg, bars, "", ok)
        }

    private suspend fun measureRtt(url: String): Int =
        withTimeoutOrNull(5_500L) {
            val start = System.nanoTime()
            var conn: HttpURLConnection? = null
            try {
                conn =
                    (URL(url).openConnection() as HttpURLConnection).apply {
                        requestMethod = "HEAD"
                        connectTimeout = 4_000
                        readTimeout = 4_000
                        instanceFollowRedirects = true
                    }
                conn.responseCode
                ((System.nanoTime() - start) / 1_000_000L).toInt()
            } catch (e: Exception) {
                Log.w(TAG, "rtt $url", e)
                -1
            } finally {
                conn?.disconnect()
            }
        } ?: -1
}
