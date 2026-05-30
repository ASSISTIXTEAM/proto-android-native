package org.assistix.proto.nativeapp.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

/** Resize/compress images before upload to save bandwidth. */
object ProtoMediaCompressor {
    const val MAX_UPLOAD_BYTES: Long = 2L * 1024 * 1024 * 1024

    fun prepareUploadFile(context: Context, source: File, mime: String): Pair<File, String> {
        if (!mime.startsWith("image/") || mime.contains("gif", ignoreCase = true)) {
            return source to mime
        }
        val out = File(context.cacheDir, "proto_upload_${System.currentTimeMillis()}.jpg")
        return try {
            val bmp = BitmapFactory.decodeFile(source.absolutePath) ?: return source to mime
            val maxSide = 2048
            val w = bmp.width
            val h = bmp.height
            val scale = min(1f, maxSide.toFloat() / max(w, h).toFloat())
            val scaled =
                if (scale < 1f) {
                    Bitmap.createScaledBitmap(bmp, (w * scale).toInt(), (h * scale).toInt(), true)
                } else {
                    bmp
                }
            FileOutputStream(out).use { fos ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 85, fos)
            }
            if (scaled !== bmp) scaled.recycle()
            bmp.recycle()
            out to "image/jpeg"
        } catch (_: Exception) {
            source to mime
        }
    }

    fun prepareUploadUri(context: Context, uri: Uri, mime: String, name: String): Pair<File, String>? {
        val tmp = File(context.cacheDir, "proto_pick_${System.currentTimeMillis()}_${name.ifBlank { "file" }}")
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                tmp.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            if (tmp.length() > MAX_UPLOAD_BYTES) return null
            prepareUploadFile(context, tmp, mime)
        } catch (_: Exception) {
            null
        }
    }
}
