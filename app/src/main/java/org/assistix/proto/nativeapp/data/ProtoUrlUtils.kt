package org.assistix.proto.nativeapp.data

private val URL_PATTERN =
    Regex(
        """(?i)\b((?:https?://|www\.)[^\s<>"'`,)\]]+)""",
    )

fun extractUrls(text: String): List<String> {
    if (text.isBlank()) return emptyList()
    return URL_PATTERN.findAll(text).map { normalizeUrl(it.groupValues[1]) }.distinct().toList()
}

fun firstUrlIn(text: String): String? = extractUrls(text).firstOrNull()

fun normalizeUrl(raw: String): String {
    val t = raw.trim().trimEnd('.', ',', ';', '!', '?', ')')
    return if (t.startsWith("http://", true) || t.startsWith("https://", true)) {
        t
    } else {
        "https://$t"
    }
}
