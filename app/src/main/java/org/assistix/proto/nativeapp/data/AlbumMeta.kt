package org.assistix.proto.nativeapp.data

import org.json.JSONArray
import org.json.JSONObject

data class AlbumItem(
    val uploadId: String,
    val mime: String? = null,
    val name: String? = null,
)

data class AlbumMeta(val items: List<AlbumItem>) {
    fun toJsonBody(caption: String = ""): String {
        val root = JSONObject()
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(
                JSONObject()
                    .put("upload_id", item.uploadId)
                    .put("mime", item.mime ?: "")
                    .put("name", item.name ?: ""),
            )
        }
        root.put("proto_album", arr)
        if (caption.isNotBlank()) {
            root.put("caption", caption)
        }
        return root.toString()
    }

    companion object {
        fun fromJson(body: String): AlbumMeta? {
            val trim = body.trim()
            if (!trim.startsWith("{")) return null
            return try {
                val root = JSONObject(trim)
                val arr = root.optJSONArray("proto_album") ?: return null
                val items = mutableListOf<AlbumItem>()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val id = normalizeUploadId(o.optString("upload_id", "")) ?: continue
                    items.add(
                        AlbumItem(
                            uploadId = id,
                            mime = o.optCleanString("mime"),
                            name = o.optCleanString("name"),
                        ),
                    )
                }
                if (items.isEmpty()) null else AlbumMeta(items)
            } catch (_: Exception) {
                null
            }
        }

        fun captionFromJson(body: String): String {
            val trim = body.trim()
            if (!trim.startsWith("{")) return ""
            return try {
                JSONObject(trim).optString("caption", "").trim()
            } catch (_: Exception) {
                ""
            }
        }
    }
}
