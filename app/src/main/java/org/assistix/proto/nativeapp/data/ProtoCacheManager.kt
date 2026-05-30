package org.assistix.proto.nativeapp.data

import android.content.Context
import coil.disk.DiskCache
import coil.imageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

enum class ProtoCacheCategory {
    CHATS,
    PHOTOS,
    VIDEOS,
    AUDIO,
    FILES,
    OTHER,
}

data class ProtoCacheBreakdown(
    val totalBytes: Long,
    val chatsBytes: Long,
    val photosBytes: Long,
    val videosBytes: Long,
    val audioBytes: Long,
    val filesBytes: Long,
    val otherBytes: Long,
) {
    fun percentOf(category: ProtoCacheCategory): Int {
        if (totalBytes <= 0L) return 0
        val part =
            when (category) {
                ProtoCacheCategory.CHATS -> chatsBytes
                ProtoCacheCategory.PHOTOS -> photosBytes
                ProtoCacheCategory.VIDEOS -> videosBytes
                ProtoCacheCategory.AUDIO -> audioBytes
                ProtoCacheCategory.FILES -> filesBytes
                ProtoCacheCategory.OTHER -> otherBytes
            }
        return ((part * 100.0) / totalBytes).roundToInt().coerceIn(0, 100)
    }
}

/** Local media + chat DB cache. Avatars live under [avatarDir] and are not cleared here. */
class ProtoCacheManager(context: Context) {
    private val appCtx = context.applicationContext

    val rootDir: File = File(ProtoPersistentStorage.cacheDir(appCtx), "proto_cache").apply { mkdirs() }
    val photosDir: File = File(rootDir, "photos").apply { mkdirs() }
    val videosDir: File = File(rootDir, "videos").apply { mkdirs() }
    val audioDir: File = File(rootDir, "audio").apply { mkdirs() }
    val filesDir: File = File(rootDir, "files").apply { mkdirs() }
    val avatarDir: File = ProtoPersistentStorage.avatarsDir(appCtx)

    private val legacyCache = appCtx.cacheDir

    fun photoFile(uploadId: String): File {
        val safe = sanitizeId(uploadId)
        return File(photosDir, "$safe.dat")
    }

    fun videoFile(uploadId: String): File {
        val safe = sanitizeId(uploadId)
        return File(videosDir, "$safe.mp4")
    }

    fun audioFile(uploadId: String, ext: String): File {
        val safe = sanitizeId(uploadId)
        return File(audioDir, "$safe.$ext")
    }

    fun genericFile(uploadId: String, name: String?): File {
        val safe = sanitizeId(uploadId)
        val ext = name?.substringAfterLast('.', "")?.takeIf { it.length in 1..8 } ?: "bin"
        return File(filesDir, "${safe}.$ext")
    }

    fun avatarFile(uploadId: String): File = File(avatarDir, "${sanitizeId(uploadId)}.webp")

    suspend fun scan(dbBytes: Long): ProtoCacheBreakdown =
        withContext(Dispatchers.IO) {
            var photos = dirSize(photosDir)
            var videos = dirSize(videosDir)
            var audio = dirSize(audioDir)
            var files = dirSize(filesDir)

            legacyCache.listFiles()?.forEach { f ->
                if (!f.isFile) return@forEach
                when {
                    f.name.startsWith("proto_vid_") -> videos += f.length()
                    f.name.startsWith("proto_aud_") -> audio += f.length()
                    f.name.startsWith("proto_img_") -> photos += f.length()
                }
            }

            photos += coilDiskBytes()

            val chats = dbBytes.coerceAtLeast(0L)
            var other = 0L
            legacyCache.listFiles()?.forEach { f ->
                if (f.isFile && f.name.startsWith("proto_pending_")) other += f.length()
            }
            val updatesDir = File(legacyCache, "updates")
            if (updatesDir.exists()) other += dirSize(updatesDir)
            val total = chats + photos + videos + audio + files + other
            ProtoCacheBreakdown(
                totalBytes = total,
                chatsBytes = chats,
                photosBytes = photos,
                videosBytes = videos,
                audioBytes = audio,
                filesBytes = files,
                otherBytes = other.coerceAtLeast(0L),
            )
        }

    suspend fun clear(category: ProtoCacheCategory) =
        withContext(Dispatchers.IO) {
            when (category) {
                ProtoCacheCategory.CHATS -> { /* cleared via Room from UI */ }
                ProtoCacheCategory.PHOTOS -> {
                    deleteDirContents(photosDir)
                    clearCoilDisk()
                    legacyCache.listFiles()?.filter { it.name.startsWith("proto_img_") }?.forEach { it.delete() }
                }
                ProtoCacheCategory.VIDEOS -> {
                    deleteDirContents(videosDir)
                    legacyCache.listFiles()?.filter { it.name.startsWith("proto_vid_") }?.forEach { it.delete() }
                }
                ProtoCacheCategory.AUDIO -> {
                    deleteDirContents(audioDir)
                    legacyCache.listFiles()?.filter { it.name.startsWith("proto_aud_") }?.forEach { it.delete() }
                }
                ProtoCacheCategory.FILES -> deleteDirContents(filesDir)
                ProtoCacheCategory.OTHER -> {
                    legacyCache.listFiles()?.filter { it.name.startsWith("proto_pending_") }?.forEach { it.delete() }
                }
            }
        }

    suspend fun clearAllMedia() =
        withContext(Dispatchers.IO) {
            ProtoCacheCategory.entries.filter { it != ProtoCacheCategory.CHATS }.forEach { clear(it) }
        }

    private fun deleteDirContents(dir: File) {
        if (ProtoPersistentStorage.isVaultPath(appCtx, dir)) return
        dir.listFiles()?.forEach { child ->
            if (ProtoPersistentStorage.isVaultPath(appCtx, child)) return@forEach
            child.deleteRecursively()
        }
    }

    companion object {
        fun formatBytes(bytes: Long): String {
            if (bytes < 1024) return "$bytes B"
            val kb = bytes / 1024.0
            if (kb < 1024) return "${kb.roundToInt()} KB"
            val mb = kb / 1024.0
            if (mb < 1024) return String.format("%.1f MB", mb)
            return String.format("%.2f GB", mb / 1024.0)
        }

        private fun sanitizeId(raw: String): String = raw.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(120)

        private fun dirSize(dir: File): Long =
            dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    private fun coilDiskBytes(): Long {
        val loader = appCtx.imageLoader
        val disk = loader.diskCache ?: return 0L
        val dir = disk.directory.toFile()
        return if (dir.exists()) dirSize(dir) else 0L
    }

    private fun clearCoilDisk() {
        runCatching { appCtx.imageLoader.diskCache?.clear() }
    }
}
