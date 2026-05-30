package org.assistix.proto.nativeapp.data

import android.content.Context
import java.io.File
import org.assistix.proto.nativeapp.data.local.MediaLocalEntity
import org.assistix.proto.nativeapp.data.local.ProtoDao

/**
 * Canonical media vault under Documents/PROTO/media/.
 * Server keeps a short relay copy only; this store is the long-term archive on device.
 */
class ProtoLocalMediaStore(
    private val context: Context,
    private val dao: ProtoDao,
    private val cache: ProtoCacheManager,
) {
    private val root: File
        get() = File(ProtoPersistentStorage.rootDir(context.applicationContext), "media").apply { mkdirs() }

    suspend fun persist(
        uploadId: String,
        source: File,
        mime: String?,
        name: String?,
    ): File? {
        val id = normalizeUploadId(uploadId) ?: return null
        if (!source.exists() || source.length() <= 0L) return null
        val kind = mediaKindFromMime(mime, name) ?: "file"
        val dest = vaultFile(id, mime, name, kind)
        dest.parentFile?.mkdirs()
        runCatching {
            if (source.absolutePath != dest.absolutePath) {
                source.copyTo(dest, overwrite = true)
            }
        }.getOrElse { return null }
        syncCacheCopy(id, dest, kind, mime, name)
        dao.upsertMediaLocal(
            MediaLocalEntity(
                uploadId = id,
                localPath = dest.absolutePath,
                mime = mime.orEmpty(),
                fileName = name.orEmpty(),
                sizeBytes = dest.length(),
            ),
        )
        return dest
    }

    suspend fun resolve(uploadId: String, mime: String?, name: String?): File? {
        val id = normalizeUploadId(uploadId) ?: return null
        dao.mediaLocal(id)?.let { row ->
            val f = File(row.localPath)
            if (f.exists() && f.length() > 0L) return f
        }
        val kind = mediaKindFromMime(mime, name) ?: "file"
        cacheFile(id, kind, mime, name)?.takeIf { it.exists() && it.length() > 0L }?.let { cached ->
            persist(id, cached, mime, name)
            return cached
        }
        return null
    }

    suspend fun hasLocal(uploadId: String): Boolean {
        val id = normalizeUploadId(uploadId) ?: return false
        dao.mediaLocal(id)?.let {
            if (File(it.localPath).exists() && File(it.localPath).length() > 0L) return true
        }
        return false
    }

    private fun vaultFile(uploadId: String, mime: String?, name: String?, kind: String): File {
        val safe = uploadId.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(120)
        val ext =
            when (kind) {
                "image" -> "jpg"
                "video" -> "mp4"
                "voice", "audio" -> cacheExtForMime(mime ?: "")
                else -> name?.substringAfterLast('.', "")?.takeIf { it.length in 1..8 } ?: "bin"
            }
        return File(root, "$safe.$ext")
    }

    private fun cacheFile(uploadId: String, kind: String, mime: String?, name: String?): File? =
        when (kind) {
            "image" -> cache.photoFile(uploadId)
            "video" -> cache.videoFile(uploadId)
            "voice", "audio" -> cache.audioFile(uploadId, cacheExtForMime(mime ?: ""))
            else -> cache.genericFile(uploadId, name)
        }

    private fun syncCacheCopy(uploadId: String, vault: File, kind: String, mime: String?, name: String?) {
        val target = cacheFile(uploadId, kind, mime, name) ?: return
        if (target.absolutePath == vault.absolutePath) return
        runCatching {
            target.parentFile?.mkdirs()
            if (!target.exists() || target.length() < vault.length()) {
                vault.copyTo(target, overwrite = true)
            }
        }
    }
}

enum class MediaFetchState {
    LOCAL,
    DOWNLOADED,
    EXPIRED,
    MISSING,
}

data class MediaFetchResult(
    val file: File?,
    val state: MediaFetchState,
)

class ProtoMediaResolver(
    private val context: Context,
    private val dao: ProtoDao,
    private val cache: ProtoCacheManager,
    private val api: ProtoApi,
    private val cells: org.assistix.proto.nativeapp.data.cells.ProtoCellsManager? = null,
) {
    private val store = ProtoLocalMediaStore(context, dao, cache)

    suspend fun persistOutgoing(uploadId: String, source: File, mime: String?, name: String?): File? =
        store.persist(uploadId, source, mime, name)

    suspend fun fetch(
        token: String,
        uploadId: String,
        mime: String?,
        name: String?,
        conversationId: Int = 0,
    ): MediaFetchResult {
        store.resolve(uploadId, mime, name)?.let { return MediaFetchResult(it, MediaFetchState.LOCAL) }
        val id = normalizeUploadId(uploadId) ?: return MediaFetchResult(null, MediaFetchState.MISSING)
        val kind = mediaKindFromMime(mime, name) ?: "file"
        val dest =
            when (kind) {
                "image" -> cache.photoFile(id)
                "video" -> cache.videoFile(id)
                "voice", "audio" -> cache.audioFile(id, cacheExtForMime(mime ?: ""))
                else -> cache.genericFile(id, name)
            }
        when (val dl = api.downloadMediaResult(token, id, dest)) {
            MediaDownloadResult.Ok -> {
                store.persist(id, dest, mime, name)
                api.ackMediaRelay(token, id)
                return MediaFetchResult(dest, MediaFetchState.DOWNLOADED)
            }
            MediaDownloadResult.ExpiredRelay -> {
                val id = normalizeUploadId(uploadId) ?: return MediaFetchResult(null, MediaFetchState.EXPIRED)
                if (conversationId > 0 && cells != null) {
                    val kind = mediaKindFromMime(mime, name) ?: "file"
                    val tmp =
                        when (kind) {
                            "image" -> cache.photoFile(id)
                            "video" -> cache.videoFile(id)
                            "voice", "audio" -> cache.audioFile(id, cacheExtForMime(mime ?: ""))
                            else -> cache.genericFile(id, name)
                        }
                    val assembled =
                        cells.assembleToFile(token, id, conversationId, tmp, mime.orEmpty())
                    if (assembled != null && assembled.exists()) {
                        store.persist(id, assembled, mime, name)
                        return MediaFetchResult(assembled, MediaFetchState.DOWNLOADED)
                    }
                }
                if (conversationId > 0) {
                    api.requestMediaRelay(token, id, conversationId)
                }
                return MediaFetchResult(null, MediaFetchState.EXPIRED)
            }
            MediaDownloadResult.Failed -> {
                if (conversationId > 0) {
                    api.requestMediaRelay(token, id, conversationId)
                }
                return MediaFetchResult(null, MediaFetchState.MISSING)
            }
        }
    }

    suspend fun relayIfLocal(token: String, uploadId: String, conversationId: Int): Boolean {
        val id = normalizeUploadId(uploadId) ?: return false
        val row = dao.mediaLocal(id)
        val file =
            row?.let { File(it.localPath) }?.takeIf { it.exists() && it.length() > 0L }
                ?: return false
        if (api.refreshMediaRelay(token, id, conversationId, file, row.mime.ifBlank { "application/octet-stream" })) {
            return true
        }
        cells?.repairFromLocal(
            token,
            id,
            conversationId,
            file,
            row.mime.ifBlank { "application/octet-stream" },
            (0 until org.assistix.proto.nativeapp.data.cells.ProtoCellsConfig.SHARD_COUNT).toList(),
        )
        return true
    }
}
