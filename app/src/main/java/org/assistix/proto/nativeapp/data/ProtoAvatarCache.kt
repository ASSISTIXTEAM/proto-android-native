package org.assistix.proto.nativeapp.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

object ProtoAvatarCache {
    private val client = OkHttpClient.Builder().build()
    private val inFlight = ConcurrentHashMap<String, Mutex>()

    suspend fun localFile(
        cache: ProtoCacheManager,
        api: ProtoApi,
        token: String,
        uploadId: String?,
    ): File? {
        val id = uploadId?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val dest = cache.avatarFile(id)
        if (dest.exists() && dest.length() > 0L) return dest
        val mutex = inFlight.getOrPut(id) { Mutex() }
        return mutex.withLock {
            if (dest.exists() && dest.length() > 0L) return@withLock dest
            val ok = withContext(Dispatchers.IO) { downloadCompressed(api, token, id, dest) }
            if (ok) dest else null
        }
    }

    private fun downloadCompressed(api: ProtoApi, token: String, uploadId: String, dest: File): Boolean {
        val req =
            Request.Builder()
                .url(api.mediaUrl(uploadId))
                .apply { api.authHeaders(token).forEach { (k, v) -> addHeader(k, v) } }
                .get()
                .build()
        return try {
            client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return false
                val bytes = res.body?.bytes() ?: return false
                val src = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return false
                val side = minOf(src.width, src.height, 256)
                val scaled =
                    if (src.width > side || src.height > side) {
                        Bitmap.createScaledBitmap(src, side, side, true)
                    } else {
                        src
                    }
                dest.parentFile?.mkdirs()
                FileOutputStream(dest).use { out ->
                    scaled.compress(Bitmap.CompressFormat.WEBP, 82, out)
                }
                if (scaled !== src) scaled.recycle()
                src.recycle()
                true
            }
        } catch (_: Exception) {
            false
        }
    }
}
