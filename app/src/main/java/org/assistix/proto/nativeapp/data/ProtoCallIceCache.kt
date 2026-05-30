package org.assistix.proto.nativeapp.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Last known-good ICE/TURN list — used when rtc-config or proto.su is unreachable. */
object ProtoCallIceCache {
    private const val PREFS = "proto_call_ice_cache"
    private const val KEY_JSON = "ice_servers_v1"

    fun save(context: Context, servers: List<RtcIceServer>) {
        if (servers.isEmpty()) return
        val arr = JSONArray()
        servers.forEach { srv ->
            val urls = JSONArray()
            srv.urls.forEach { urls.put(it) }
            arr.put(
                JSONObject()
                    .put("urls", urls)
                    .put("username", srv.username.orEmpty())
                    .put("credential", srv.credential.orEmpty()),
            )
        }
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_JSON, arr.toString())
            .apply()
    }

    fun load(context: Context): List<RtcIceServer>? {
        val raw =
            context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_JSON, null)
                ?: return null
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val urlsArr = o.optJSONArray("urls") ?: return@mapNotNull null
                val urls =
                    (0 until urlsArr.length()).mapNotNull { u ->
                        urlsArr.optString(u).trim().takeIf { it.isNotBlank() }
                    }
                if (urls.isEmpty()) return@mapNotNull null
                RtcIceServer(
                    urls,
                    o.optString("username").trim().takeIf { it.isNotBlank() },
                    o.optString("credential").trim().takeIf { it.isNotBlank() },
                )
            }.takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }
}
