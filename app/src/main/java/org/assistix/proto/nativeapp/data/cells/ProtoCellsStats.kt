package org.assistix.proto.nativeapp.data.cells

import org.json.JSONObject

data class ProtoCellsStats(
    val holdsTotal: Int = 0,
    val holdsAcked: Int = 0,
    val holdsPending: Int = 0,
    val usedBytes: Long = 0L,
    val quotaBytes: Long = ProtoCellsConfig.DEFAULT_QUOTA_BYTES,
    val conversationsHelped: Int = 0,
    val publishedBlobs: Int = 0,
    val tier: String = "member",
    val localBytes: Long = 0L,
    val localShards: Int = 0,
    val lastSyncAt: Long = 0L,
    val repairsActive: Int = 0,
) {
    val tierLabelKey: String
        get() =
            when (tier) {
                "node" -> "cellsTierNode"
                "active" -> "cellsTierActive"
                else -> "cellsTierMember"
            }

    companion object {
        fun fromJson(o: JSONObject, localBytes: Long, localShards: Int, lastSync: Long, repairs: Int): ProtoCellsStats =
            ProtoCellsStats(
                holdsTotal = o.optInt("holds_total", 0),
                holdsAcked = o.optInt("holds_acked", 0),
                holdsPending = o.optInt("holds_pending", 0),
                usedBytes = o.optLong("used_bytes", 0L),
                quotaBytes = o.optLong("quota_bytes", ProtoCellsConfig.DEFAULT_QUOTA_BYTES),
                conversationsHelped = o.optInt("conversations_helped", 0),
                publishedBlobs = o.optInt("published_blobs", 0),
                tier = o.optString("tier", "member"),
                localBytes = localBytes,
                localShards = localShards,
                lastSyncAt = lastSync,
                repairsActive = repairs,
            )
    }
}
