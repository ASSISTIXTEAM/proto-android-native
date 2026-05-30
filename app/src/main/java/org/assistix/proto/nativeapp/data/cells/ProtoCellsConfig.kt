package org.assistix.proto.nativeapp.data.cells

object ProtoCellsConfig {
    /** Full Cells-P: 7 data stripes + 1 XOR parity. */
    const val DATA_SHARD_COUNT = 7
    const val PARITY_INDEX = 7
    const val SHARD_COUNT = 8

    /** Compact Cells-P for smaller blobs — less overhead on voice notes / thumbnails. */
    const val SMALL_DATA_SHARD_COUNT = 3
    const val SMALL_PARITY_INDEX = 3
    const val SMALL_SHARD_COUNT = 4
    const val SMALL_CIPHER_MAX_BYTES = 48 * 1024

    const val CODING_XOR_PARITY = "xor_parity"

    /** Legacy mirror mode fallback for old blobs. */
    const val REPLICATION = 3
    const val PARITY_REPLICATION = 2

    const val MIN_BLOB_BYTES = 8 * 1024
    const val DEFAULT_QUOTA_BYTES = 768L * 1024 * 1024
    const val COMPRESS_SHARDS = true
    const val AES_KEY_BYTES = 32
    const val GCM_IV_BYTES = 12
    const val GCM_TAG_BITS = 128

    data class ShardPlan(
        val dataShardCount: Int,
        val shardCount: Int,
        val parityIndex: Int,
    )

    fun planForCipher(cipherSize: Int): ShardPlan =
        if (cipherSize in 1 until SMALL_CIPHER_MAX_BYTES) {
            ShardPlan(SMALL_DATA_SHARD_COUNT, SMALL_SHARD_COUNT, SMALL_PARITY_INDEX)
        } else {
            ShardPlan(DATA_SHARD_COUNT, SHARD_COUNT, PARITY_INDEX)
        }

    fun isXorParityBlob(shardCount: Int): Boolean =
        shardCount == SHARD_COUNT || shardCount == SMALL_SHARD_COUNT

    fun dataShardCount(shardCount: Int): Int =
        when (shardCount) {
            SHARD_COUNT -> DATA_SHARD_COUNT
            SMALL_SHARD_COUNT -> SMALL_DATA_SHARD_COUNT
            else -> shardCount
        }

    fun parityIndex(shardCount: Int): Int =
        when (shardCount) {
            SHARD_COUNT -> PARITY_INDEX
            SMALL_SHARD_COUNT -> SMALL_PARITY_INDEX
            else -> -1
        }
}
