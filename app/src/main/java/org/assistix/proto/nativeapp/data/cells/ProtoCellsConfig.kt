package org.assistix.proto.nativeapp.data.cells

object ProtoCellsConfig {
    const val SHARD_COUNT = 7
    const val REPLICATION = 3
    /** Distribute media from 8 KB — small files still help the mesh. */
    const val MIN_BLOB_BYTES = 8 * 1024
    const val DEFAULT_QUOTA_BYTES = 768L * 1024 * 1024
    const val COMPRESS_SHARDS = true
    const val AES_KEY_BYTES = 32
    const val GCM_IV_BYTES = 12
    const val GCM_TAG_BITS = 128
}
