package org.assistix.proto.nativeapp.data.cells

import android.content.Context
import java.io.File
import org.assistix.proto.nativeapp.data.ProtoPersistentStorage

class ProtoCellsStore(context: Context) {
    private val appCtx = context.applicationContext
    private val root: File
        get() = File(ProtoPersistentStorage.rootDir(appCtx), "cells").apply { mkdirs() }

    fun shardFile(blobId: String, index: Int): File {
        val safe = blobId.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(120)
        return File(File(root, safe), "s$index.shard").apply { parentFile?.mkdirs() }
    }

    /** Writes shard on disk (gzip when enabled). [rawData] is the uncompressed shard. */
    fun writeShard(blobId: String, index: Int, rawData: ByteArray, expectedHash: String): Boolean {
        val computed = ProtoCellsCrypto.shardMac(rawData, blobId, index)
        if (!computed.equals(expectedHash, ignoreCase = true)) return false
        val payload =
            if (ProtoCellsConfig.COMPRESS_SHARDS) {
                ProtoCellsCompression.compress(rawData)
            } else {
                rawData
            }
        val f = shardFile(blobId, index)
        f.writeBytes(payload)
        return f.exists() && f.length() > 0L
    }

    /** Returns uncompressed shard bytes for assembly / MAC checks. */
    fun readShard(blobId: String, index: Int): ByteArray? {
        val payload = readStoredPayload(blobId, index) ?: return null
        return if (ProtoCellsConfig.COMPRESS_SHARDS && ProtoCellsCompression.isCompressed(payload)) {
            ProtoCellsCompression.decompress(payload)
        } else {
            payload
        }
    }

    /** Bytes as stored (compressed) — for network upload. */
    fun readStoredPayload(blobId: String, index: Int): ByteArray? {
        val f = shardFile(blobId, index)
        if (!f.exists() || f.length() <= 0L) return null
        return runCatching { f.readBytes() }.getOrNull()
    }

    fun hasAllShards(blobId: String, shardCount: Int, hashes: List<String>): Boolean {
        if (hashes.size < shardCount) return false
        for (i in 0 until shardCount) {
            val data = readShard(blobId, i) ?: return false
            val hash = ProtoCellsCrypto.shardMac(data, blobId, i)
            if (!hash.equals(hashes.getOrNull(i).orEmpty(), ignoreCase = true)) return false
        }
        return true
    }

    fun localShardIndices(blobId: String, shardCount: Int): List<Int> {
        val out = mutableListOf<Int>()
        for (i in 0 until shardCount) {
            val f = shardFile(blobId, i)
            if (f.exists() && f.length() > 0L) out.add(i)
        }
        return out
    }

    data class LocalUsage(val bytes: Long, val shards: Int)

    fun localUsage(): LocalUsage {
        var bytes = 0L
        var shards = 0
        if (!root.exists()) return LocalUsage(0, 0)
        root.walkTopDown().maxDepth(3).forEach { f ->
            if (f.isFile && f.name.endsWith(".shard")) {
                bytes += f.length()
                shards++
            }
        }
        return LocalUsage(bytes, shards)
    }
}
