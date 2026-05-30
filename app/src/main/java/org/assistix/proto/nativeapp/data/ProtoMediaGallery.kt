package org.assistix.proto.nativeapp.data

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File

object ProtoMediaGallery {
    fun saveToGallery(context: Context, file: File, displayName: String, mime: String): Boolean {
        if (!file.exists() || file.length() <= 0L) return false
        val cleanMime = mime.ifBlank { "application/octet-stream" }
        return try {
            val resolver = context.contentResolver
            val isVideo = cleanMime.startsWith("video/")
            val collection =
                if (isVideo) {
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                } else {
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }
            val values =
                ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                    put(MediaStore.MediaColumns.MIME_TYPE, cleanMime)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(
                            MediaStore.MediaColumns.RELATIVE_PATH,
                            if (isVideo) "${Environment.DIRECTORY_MOVIES}/PROTO" else "${Environment.DIRECTORY_PICTURES}/PROTO",
                        )
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                }
            val uri = resolver.insert(collection, values) ?: return false
            resolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { input -> input.copyTo(out) }
            } ?: return false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun shareFile(context: Context, file: File, mime: String): Boolean {
        if (!file.exists()) return false
        return try {
            val uri = fileProviderUri(context, file)
            val intent =
                Intent(Intent.ACTION_SEND).apply {
                    type = mime.ifBlank { "*/*" }
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            context.startActivity(Intent.createChooser(intent, null))
            true
        } catch (_: Exception) {
            false
        }
    }

    fun fileProviderUri(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
