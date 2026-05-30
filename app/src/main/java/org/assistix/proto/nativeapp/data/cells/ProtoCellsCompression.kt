package org.assistix.proto.nativeapp.data.cells

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** Gzip shard payloads — saves device + relay space (~30–60% on encrypted data). */
object ProtoCellsCompression {
    private val MAGIC = byteArrayOf('P'.code.toByte(), 'C'.code.toByte(), 'G'.code.toByte(), 'Z'.code.toByte())

    fun compress(raw: ByteArray): ByteArray {
        if (raw.isEmpty()) return MAGIC.copyOf()
        val buf = ByteArrayOutputStream(raw.size / 2)
        GZIPOutputStream(buf).use { it.write(raw) }
        return MAGIC + buf.toByteArray()
    }

    fun decompress(payload: ByteArray): ByteArray {
        if (payload.size >= 4 && payload.copyOfRange(0, 4).contentEquals(MAGIC)) {
            val body = payload.copyOfRange(4, payload.size)
            if (body.isEmpty()) return body
            return GZIPInputStream(ByteArrayInputStream(body)).use { it.readBytes() }
        }
        return payload
    }

    fun isCompressed(payload: ByteArray): Boolean =
        payload.size >= 4 && payload.copyOfRange(0, 4).contentEquals(MAGIC)
}
