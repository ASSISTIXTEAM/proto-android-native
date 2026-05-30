package org.assistix.proto.nativeapp.data

import java.security.MessageDigest

/** PIN hashing for vault (SHA-256 + salt). Supports legacy hashCode pins until re-set. */
object ProtoVaultPin {
    private const val SALT = "proto-vault-v2"

    fun hash(pin: String): String {
        if (pin.isBlank()) return ""
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest("$SALT:$pin".toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun matches(stored: String, pin: String): Boolean {
        if (stored.isBlank() || pin.isBlank()) return false
        if (stored == hash(pin)) return true
        return stored == legacyHash(pin)
    }

    fun isLegacyStored(stored: String, pin: String): Boolean = stored == legacyHash(pin)

    private fun legacyHash(pin: String): String = pin.hashCode().toString(16)
}
