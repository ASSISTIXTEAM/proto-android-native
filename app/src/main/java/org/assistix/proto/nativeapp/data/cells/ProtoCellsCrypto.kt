package org.assistix.proto.nativeapp.data.cells

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
object ProtoCellsCrypto {
    private val random = SecureRandom()

    data class EncryptedBlob(
        val cipher: ByteArray,
        val key: ByteArray,
        val cipherHash: String,
    )

    fun encrypt(plain: ByteArray): EncryptedBlob {
        val key = ByteArray(ProtoCellsConfig.AES_KEY_BYTES).also { random.nextBytes(it) }
        val iv = ByteArray(ProtoCellsConfig.GCM_IV_BYTES).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(ProtoCellsConfig.GCM_TAG_BITS, iv))
        val encrypted = cipher.doFinal(plain)
        val out = iv + encrypted
        return EncryptedBlob(out, key, sha256Hex(out))
    }

    fun decrypt(cipherPayload: ByteArray, key: ByteArray): ByteArray {
        require(cipherPayload.size > ProtoCellsConfig.GCM_IV_BYTES) { "cipher too short" }
        val iv = cipherPayload.copyOfRange(0, ProtoCellsConfig.GCM_IV_BYTES)
        val body = cipherPayload.copyOfRange(ProtoCellsConfig.GCM_IV_BYTES, cipherPayload.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(ProtoCellsConfig.GCM_TAG_BITS, iv))
        return cipher.doFinal(body)
    }

    fun keyToB64(key: ByteArray): String = Base64.encode(key)

    fun keyFromB64(b64: String): ByteArray = Base64.decode(b64.trim())

    fun sha256Hex(data: ByteArray): String {
        val d = MessageDigest.getInstance("SHA-256").digest(data)
        return d.joinToString("") { "%02x".format(it) }
    }

    fun shardMac(shard: ByteArray, blobId: String, index: Int): String =
        sha256Hex(blobId.toByteArray(Charsets.UTF_8) + byteArrayOf(index.toByte()) + shard)
}
