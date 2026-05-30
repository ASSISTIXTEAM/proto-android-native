package org.assistix.proto.nativeapp.data.cells

object ProtoCellsCodec {
    fun split(cipher: ByteArray, shardCount: Int = ProtoCellsConfig.DATA_SHARD_COUNT): List<ByteArray> {
        require(shardCount >= 2) { "need >= 2 shards" }
        val paddedLen = ((cipher.size + shardCount - 1) / shardCount) * shardCount
        val padded =
            if (paddedLen == cipher.size) {
                cipher
            } else {
                cipher + ByteArray(paddedLen - cipher.size)
            }
        val chunk = paddedLen / shardCount
        return (0 until shardCount).map { i ->
            padded.copyOfRange(i * chunk, (i + 1) * chunk)
        }
    }

    /** Split cipher with XOR parity using adaptive plan (3+1 or 7+1). */
    fun splitWithParity(cipher: ByteArray): Pair<List<ByteArray>, ProtoCellsConfig.ShardPlan> {
        val plan = ProtoCellsConfig.planForCipher(cipher.size)
        val data = split(cipher, plan.dataShardCount)
        return (data + xorAll(data)) to plan
    }

    fun xorAll(shards: List<ByteArray>): ByteArray {
        require(shards.isNotEmpty())
        val len = shards.maxOf { it.size }
        val out = ByteArray(len)
        for (s in shards) {
            for (i in s.indices) {
                out[i] = (out[i].toInt() xor s[i].toInt()).toByte()
            }
        }
        return out
    }

    fun recoverOneDataShard(
        data: Array<ByteArray?>,
        parity: ByteArray?,
        dataN: Int = ProtoCellsConfig.DATA_SHARD_COUNT,
    ): Boolean {
        if (parity == null) return false
        val missing = (0 until dataN).filter { data[it] == null }
        if (missing.size != 1) return false
        val miss = missing[0]
        var acc = parity.copyOf()
        for (i in 0 until dataN) {
            if (i == miss) continue
            val s = data[i] ?: return false
            for (b in s.indices) {
                acc[b] = (acc[b].toInt() xor s[b].toInt()).toByte()
            }
        }
        data[miss] = acc
        return true
    }

    fun join(shards: List<ByteArray>, cipherSize: Int): ByteArray {
        require(shards.isNotEmpty()) { "no shards" }
        val merged = shards.fold(byteArrayOf()) { acc, s -> acc + s }
        return merged.copyOf(cipherSize.coerceIn(0, merged.size))
    }
}
