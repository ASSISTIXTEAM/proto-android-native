package org.assistix.proto.nativeapp.data

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred

data class LinkPreview(
    val url: String,
    val title: String,
    val description: String,
    val imageUrl: String,
    val siteName: String,
    val aiSummary: String = "",
) {
    val hasCard: Boolean
        get() = title.isNotBlank() || description.isNotBlank() || imageUrl.isNotBlank() || aiSummary.isNotBlank()
}

object ProtoLinkPreviewCache {
    private val cache = ConcurrentHashMap<String, LinkPreview>()
    private val inflight = ConcurrentHashMap<String, CompletableDeferred<LinkPreview?>>()

    fun peek(url: String): LinkPreview? {
        val key = normalizeUrl(url)
        cache[key]?.let { return it }
        return diskLoad(key)
    }

    suspend fun load(url: String, token: String?, api: ProtoApi): LinkPreview? {
        val key = normalizeUrl(url)
        cache[key]?.let { return it }
        diskLoad(key)?.let {
            cache[key] = it
            return it
        }
        val existing = inflight[key]
        if (existing != null) {
            return existing.await()
        }
        val deferred = CompletableDeferred<LinkPreview?>()
        val prior = inflight.putIfAbsent(key, deferred)
        if (prior != null) {
            return prior.await()
        }
        return try {
            val fetched = api.fetchLinkPreview(token, key)
            if (fetched != null) {
                cache[key] = fetched
                diskSave(key, fetched)
            }
            deferred.complete(fetched)
            fetched
        } catch (_: Exception) {
            deferred.complete(null)
            null
        } finally {
            inflight.remove(key)
        }
    }

    @Volatile
    private var storageCtx: android.content.Context? = null

    fun attach(context: android.content.Context) {
        storageCtx = context.applicationContext
    }

    private fun normalizeUrl(url: String): String = url.trim().lowercase()

    private fun diskFile(key: String): java.io.File? {
        val ctx = storageCtx ?: return null
        val dir = java.io.File(ProtoPersistentStorage.rootDir(ctx), "offline/link_previews").apply { mkdirs() }
        val hash = key.hashCode().toUInt().toString(16)
        return java.io.File(dir, "$hash.json")
    }

    private fun diskSave(key: String, p: LinkPreview) {
        val f = diskFile(key) ?: return
        runCatching {
            val o =
                org.json.JSONObject()
                    .put("url", p.url)
                    .put("title", p.title)
                    .put("description", p.description)
                    .put("imageUrl", p.imageUrl)
                    .put("siteName", p.siteName)
                    .put("aiSummary", p.aiSummary)
            f.writeText(o.toString())
        }
    }

    private fun diskLoad(key: String): LinkPreview? {
        val f = diskFile(key) ?: return null
        if (!f.isFile) return null
        return runCatching {
            val o = org.json.JSONObject(f.readText())
            LinkPreview(
                url = o.optString("url", key),
                title = o.optString("title"),
                description = o.optString("description"),
                imageUrl = o.optString("imageUrl"),
                siteName = o.optString("siteName"),
                aiSummary = o.optString("aiSummary"),
            )
        }.getOrNull()
    }
}
