package org.assistix.proto.nativeapp.data

/** Classify attachment display (voice bubble vs generic file). */
fun mediaKindFromMime(mime: String?, name: String? = null): String? {
    val m = mime?.trim()?.lowercase().orEmpty()
    val n = name?.trim()?.lowercase().orEmpty()
    return when {
        m.startsWith("audio/") || n.endsWith(".m4a") || n.endsWith(".mp3") || n.endsWith(".ogg") || n.endsWith(".webm") && n.contains("voice") -> "voice"
        m.startsWith("image/") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp") || n.endsWith(".gif") -> "image"
        m.startsWith("video/") || n.endsWith(".mp4") || n.endsWith(".webm") -> "video"
        m.isNotEmpty() || n.isNotEmpty() -> "file"
        else -> null
    }
}

fun isVoiceCaption(body: String): Boolean {
    val b = body.trim()
    if (b.isEmpty()) return true
    return b == "🎤 Голосовое" ||
        b.equals("Voice message", ignoreCase = true) ||
        b.equals("Голосовое", ignoreCase = true) ||
        b.equals("Messaggio vocale", ignoreCase = true) ||
        b.startsWith("🎤")
}

fun MsgItem.isVoiceMedia(): Boolean =
    mediaKind == "voice" || (hasMediaAttachment() && mediaKindFromMime(mediaMime, mediaName) == "voice")

fun MsgItem.isImageMedia(): Boolean =
    mediaKind == "image" || (hasMediaAttachment() && mediaKindFromMime(mediaMime, mediaName) == "image")

fun MsgItem.isVideoMedia(): Boolean =
    mediaKind == "video" || (hasMediaAttachment() && mediaKindFromMime(mediaMime, mediaName) == "video")

fun MsgItem.isGalleryMedia(): Boolean = isImageMedia() || isVideoMedia()

fun guessMimeFromName(name: String?): String {
    val n = name?.lowercase().orEmpty()
    return when {
        n.endsWith(".mp4") || n.endsWith(".webm") -> "video/mp4"
        n.endsWith(".png") -> "image/png"
        n.endsWith(".webp") -> "image/webp"
        n.endsWith(".gif") -> "image/gif"
        n.endsWith(".jpg") || n.endsWith(".jpeg") -> "image/jpeg"
        else -> "image/jpeg"
    }
}

fun MsgItem.shouldShowMediaCaption(): Boolean {
    if (bodyRaw.isBlank()) return false
    if (isVoiceMedia() && isVoiceCaption(bodyRaw)) return false
    if (isImageMedia() && bodyRaw == mediaName.orEmpty()) return false
    return true
}

fun cacheExtForMime(mime: String?): String =
    when {
        mime?.contains("webm", ignoreCase = true) == true -> "webm"
        mime?.contains("ogg", ignoreCase = true) == true -> "ogg"
        mime?.contains("mpeg", ignoreCase = true) == true || mime?.contains("mp3", ignoreCase = true) == true -> "mp3"
        mime?.startsWith("video/", ignoreCase = true) == true -> "mp4"
        else -> "m4a"
    }
