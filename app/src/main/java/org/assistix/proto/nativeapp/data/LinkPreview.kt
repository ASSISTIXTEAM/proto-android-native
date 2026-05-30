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

    fun peek(url: String): LinkPreview? = cache[normalizeUrl(url)]

    suspend fun load(url: String, token: String?, api: ProtoApi): LinkPreview? {
        val key = normalizeUrl(url)
        cache[key]?.let { return it }
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
}
