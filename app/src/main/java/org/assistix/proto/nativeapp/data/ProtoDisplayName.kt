package org.assistix.proto.nativeapp.data

/** Resolves visible name; falls back to nick without leading @. */
fun resolveDisplayName(displayName: String, nick: String): String {
    val d = displayName.trim()
    if (d.isNotEmpty()) return d.take(80)
    return nick.trim().removePrefix("@").take(80)
}
