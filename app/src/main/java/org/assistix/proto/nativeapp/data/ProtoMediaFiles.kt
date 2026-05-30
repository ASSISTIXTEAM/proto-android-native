package org.assistix.proto.nativeapp.data

import android.content.Context
import android.net.Uri
import java.io.File

object ProtoMediaFiles {
    fun copyToCache(context: Context, uri: Uri): Pair<File, String>? {
        val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
        val ext =
            when {
                mime.startsWith("image/") -> ".jpg"
                mime.startsWith("video/") -> ".mp4"
                mime.startsWith("audio/") -> ".m4a"
                else -> ""
            }
        return try {
            val tmp = File.createTempFile("proto_pending_", ext, context.cacheDir)
            context.contentResolver.openInputStream(uri)?.use { inp ->
                tmp.outputStream().use { out -> inp.copyTo(out) }
            } ?: return null
            if (!tmp.exists() || tmp.length() <= 0L) {
                tmp.delete()
                return null
            }
            tmp to mime
        } catch (_: Exception) {
            null
        }
    }

    fun displayName(mime: String, fallback: String = "file"): String =
        when {
            mime.startsWith("image/") -> "photo.jpg"
            mime.startsWith("video/") -> "video.mp4"
            mime.startsWith("audio/") -> "voice.m4a"
            else -> fallback
        }
}
