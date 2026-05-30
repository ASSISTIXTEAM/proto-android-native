package org.assistix.proto.nativeapp.data

/** Default public hostnames; override via secrets.properties / BuildConfig.API_ORIGIN. */
object ProtoHosts {
    const val SITE_ORIGIN = "https://proto.su"
    const val API_ORIGIN = "https://api.proto.su"
    const val WEB_ORIGIN = "https://web.proto.su"

    private val WEB_LINK_HOSTS =
        setOf(
            "proto.su",
            "www.proto.su",
            "api.proto.su",
            "web.proto.su",
        )

    fun isWebLinkHost(host: String?): Boolean = host?.lowercase() in WEB_LINK_HOSTS

    fun profileUrl(nick: String): String {
        val n = nick.trim().removePrefix("@")
        return "$SITE_ORIGIN/u/@$n"
    }
}
