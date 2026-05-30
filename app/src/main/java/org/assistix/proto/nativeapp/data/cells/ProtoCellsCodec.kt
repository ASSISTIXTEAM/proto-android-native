package org.assistix.proto.nativeapp.data.cells

object ProtoCellsCodec {
    fun split(cipher: ByteArray, shardCount: Int = ProtoCellsConfig.SHARD_COUNT): List<ByteArray> {
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

    fun join(shards: List<ByteArray>, cipherSize: Int): ByteArray {
        require(shards.isNotEmpty()) { "no shards" }
        val merged = shards.fold(byteArrayOf()) { acc, s -> acc + s }
        return merged.copyOf(cipherSize.coerceIn(0, merged.size))
    }
}
