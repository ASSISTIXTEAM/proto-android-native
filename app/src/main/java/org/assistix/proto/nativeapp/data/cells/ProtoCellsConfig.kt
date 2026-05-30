package org.assistix.proto.nativeapp.data.cells

object ProtoCellsConfig {
    /** 7 data stripes + 1 XOR parity (Cells-P). Legacy blobs may use 7 mirror-only. */
    const val DATA_SHARD_COUNT = 7
    const val PARITY_INDEX = 7
    const val SHARD_COUNT = 8
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

    fun isXorParityBlob(shardCount: Int): Boolean = shardCount == SHARD_COUNT

    fun dataShardCount(shardCount: Int): Int =
        if (isXorParityBlob(shardCount)) DATA_SHARD_COUNT else shardCount
}
