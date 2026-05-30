package org.assistix.proto.nativeapp.data

import android.net.Uri

sealed class ProtoQrPayload {
    data class DeviceLink(val pairId: String, val secret: String) : ProtoQrPayload()

    data class PublicLink(val code: String) : ProtoQrPayload()

    data class ProfileNick(val nick: String) : ProtoQrPayload()
}

object ProtoQrResolver {
    fun parse(text: String): ProtoQrPayload? {
        val raw = text.trim()
        if (raw.isEmpty()) return null
        val uri =
            try {
                Uri.parse(if (raw.contains("://") || raw.startsWith("/")) raw else "${ProtoHosts.SITE_ORIGIN}/$raw")
            } catch (_: Exception) {
                return null
            }
        val pairId = uri.getQueryParameter("p")?.trim().orEmpty()
        val secret = uri.getQueryParameter("s")?.trim().orEmpty()
        if (pairId.isNotEmpty() && secret.isNotEmpty()) {
            return ProtoQrPayload.DeviceLink(pairId, secret)
        }
        val path = uri.path?.trim('/') ?: ""
        val segments = path.split('/').filter { it.isNotBlank() }
        when {
            segments.size >= 2 && segments[0] == "l" -> {
                val code = segments[1]
                if (code.isNotBlank()) return ProtoQrPayload.PublicLink(code)
            }
            segments.size >= 2 && segments[0] == "u" -> {
                val nick = segments[1]
                if (nick.isNotBlank()) return ProtoQrPayload.ProfileNick(nick)
            }
        }
        if (uri.host?.contains("proto") == true && segments.size == 1 && segments[0].length >= 8) {
            return ProtoQrPayload.PublicLink(segments[0])
        }
        return null
    }
}
