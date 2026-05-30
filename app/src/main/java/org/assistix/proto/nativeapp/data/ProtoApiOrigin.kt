package org.assistix.proto.nativeapp.data

import android.content.Context
import org.assistix.proto.nativeapp.BuildConfig

/**
 * API base URL with fallback when api.proto.su vhost is misconfigured (HTML 404).
 * Working origin is cached after the first successful JSON response.
 */
object ProtoApiOrigin {
    private const val PREFS = "proto_api_origin"
    private const val KEY = "preferred_origin"

    @Volatile
    private var preferred: String? = null

    fun init(context: Context) {
        preferred =
            context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY, null)
                ?.trim()
                ?.trimEnd('/')
                ?.takeIf { it.startsWith("http") }
    }

    fun orderedOrigins(): List<String> {
        val built =
            listOfNotNull(
                preferred,
                ProtoHosts.SITE_ORIGIN,
                BuildConfig.API_ORIGIN.trim().trimEnd('/').takeIf { it.startsWith("http") },
                preferred?.takeUnless { it.contains("api.proto.su", ignoreCase = true) },
                ProtoHosts.API_ORIGIN,
            )
        return built.distinct()
    }

    fun clearPreferredIfBroken(context: Context) {
        val p = preferred ?: return
        if (p.contains("api.proto.su", ignoreCase = true)) {
            preferred = null
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
        }
    }

    fun primary(): String = orderedOrigins().first()

    fun url(path: String, origin: String = primary()): String = origin.trimEnd('/') + path

    fun rememberWorking(origin: String) {
        val o = origin.trim().trimEnd('/')
        if (!o.startsWith("http")) return
        preferred = o
    }

    fun persist(context: Context, origin: String) {
        rememberWorking(origin)
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, origin.trim().trimEnd('/'))
            .apply()
    }

    fun looksLikeHtml(body: String): Boolean {
        val s = body.trimStart().lowercase()
        return s.startsWith("<!") || s.startsWith("<html") || s.contains("<!doctype")
    }
}
