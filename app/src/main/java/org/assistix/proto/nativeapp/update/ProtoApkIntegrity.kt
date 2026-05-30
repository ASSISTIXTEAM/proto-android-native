package org.assistix.proto.nativeapp.update

import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

/** Проверка целостности APK перед установкой (не «шифрование» — подпись v1–v3 уже в release). */
object ProtoApkIntegrity {
    private const val MIN_APK_BYTES = 30_000_000L
    private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04)

    fun isValidApk(file: File): Boolean {
        if (!file.exists() || file.length() < MIN_APK_BYTES) return false
        if (!hasZipMagic(file)) return false
        return hasAndroidManifest(file)
    }

    fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            var read: Int
            while (input.read(buf).also { read = it } != -1) {
                digest.update(buf, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun matchesExpected(file: File, expectedSha256: String, expectedSize: Long): Boolean {
        if (!isValidApk(file)) return false
        if (expectedSize > 0) {
            val diff = kotlin.math.abs(file.length() - expectedSize)
            val tolerance = maxOf(8192L, expectedSize / 100)
            if (diff > tolerance) return false
        }
        val expected = expectedSha256.trim()
        if (expected.isBlank()) return true
        return sha256Hex(file).equals(expected, ignoreCase = true)
    }

    private fun hasZipMagic(file: File): Boolean =
        try {
            file.inputStream().use { input ->
                val magic = ByteArray(4)
                input.read(magic) == 4 && magic.contentEquals(ZIP_MAGIC)
            }
        } catch (_: Exception) {
            false
        }

    private fun hasAndroidManifest(file: File): Boolean =
        try {
            ZipFile(file).use { zip ->
                zip.getEntry("AndroidManifest.xml") != null
            }
        } catch (_: Exception) {
            false
        }
}
