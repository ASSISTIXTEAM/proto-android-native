package org.assistix.proto.nativeapp.data



import org.json.JSONObject



data class ChannelPostMeta(
    val text: String,
    val imageUploadId: String? = null,
    val imageUrl: String? = null,
) {
    fun toJsonBody(): String {
        val post = JSONObject()
        if (text.isNotBlank()) post.put("text", text.trim())
        imageUploadId?.takeIf { it.isNotBlank() }?.let { post.put("image_upload_id", it) }
        imageUrl?.takeIf { it.isNotBlank() }?.let { post.put("image_url", it) }
        return JSONObject().put("proto_post", post).toString()
    }

    companion object {

        fun fromJson(raw: String): ChannelPostMeta? {

            return try {

                val j = JSONObject(raw.trim())

                when {

                    j.has("proto_post") -> fromProtoPost(j.opt("proto_post"))

                    j.optString("type", "") == "poll" -> null

                    j.has("text") -> {

                        val text = jsonText(j, "text")

                        if (text.isBlank()) null else ChannelPostMeta(text = text)

                    }

                    else -> null

                }

            } catch (_: Exception) {

                val plain = raw.trim()

                if (plain.isNotBlank() && !plain.startsWith("{")) ChannelPostMeta(text = plain) else null

            }

        }



        fun fromDisplayText(displayText: String, raw: String): ChannelPostMeta? {

            val fromJson = fromJson(raw)

            if (fromJson != null) return fromJson

            val t = displayText.trim()

            if (t.isNotBlank() && !t.equals("null", ignoreCase = true)) return ChannelPostMeta(text = t)

            return null

        }



        private fun fromProtoPost(node: Any?): ChannelPostMeta? {

            when (node) {

                is String -> {

                    val text = node.trim()

                    if (text.isBlank() || text.equals("null", ignoreCase = true)) return null

                    return ChannelPostMeta(text = text)

                }

                is JSONObject -> {

                    val text = jsonText(node, "text")

                    val img = node.optString("image_upload_id", "").trim().ifBlank { null }
                    val url = node.optString("image_url", "").trim().ifBlank { null }

                    if (text.isBlank() && img == null && url == null) return null

                    return ChannelPostMeta(text = text, imageUploadId = img, imageUrl = url)

                }

            }

            return null

        }



        private fun jsonText(j: JSONObject, key: String): String {

            if (j.isNull(key)) return ""

            val s = j.optString(key, "").trim()

            return if (s.equals("null", ignoreCase = true)) "" else s

        }

    }

}


