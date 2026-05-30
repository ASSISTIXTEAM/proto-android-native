package org.assistix.proto.nativeapp.data.cells

import android.content.Context
import android.util.Log
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.assistix.proto.nativeapp.data.ProtoApi
import org.assistix.proto.nativeapp.data.ProtoNetworkMonitor
import org.assistix.proto.nativeapp.data.ProtoNotifier
import org.assistix.proto.nativeapp.data.ProtoTransferProgressHub
import org.assistix.proto.nativeapp.data.normalizeUploadId
import org.json.JSONArray
import org.json.JSONObject

/**
 * PROTO Cells — mandatory encrypted shard mesh across chat members and volunteers.
 */
class ProtoCellsManager(
    private val context: Context,
    private val api: ProtoApi,
    private val network: ProtoNetworkMonitor,
) {
    private val store = ProtoCellsStore(context)
    private val appCtx = context.applicationContext

    private val _stats = MutableStateFlow(ProtoCellsStats())
    val stats: StateFlow<ProtoCellsStats> = _stats.asStateFlow()

    private val _repairActive = MutableStateFlow(0)
    val repairActive: StateFlow<Int> = _repairActive.asStateFlow()

    private var lastSyncAt = 0L

    /** Auto-enroll every signed-in user — Cells is mandatory for all PROTO accounts. */
    suspend fun enrollMandatory(token: String): Boolean =
        withContext(Dispatchers.IO) {
            api.cellsVolunteer(token, enabled = true, quotaBytes = ProtoCellsConfig.DEFAULT_QUOTA_BYTES)
        }

    suspend fun refreshStats(token: String) =
        withContext(Dispatchers.IO) {
            val local = store.localUsage()
            val remote = api.cellsMyStats(token)
            val repairs = _repairActive.value
            _stats.value =
                if (remote != null) {
                    ProtoCellsStats.fromJson(remote, local.bytes, local.shards, lastSyncAt, repairs)
                } else {
                    _stats.value.copy(localBytes = local.bytes, localShards = local.shards, lastSyncAt = lastSyncAt, repairsActive = repairs)
                }
        }

    fun noteRepairStarted(count: Int = 1) {
        _repairActive.value = (_repairActive.value + count).coerceAtMost(99)
        _stats.value = _stats.value.copy(repairsActive = _repairActive.value)
        runCatching {
            ProtoNotifier(appCtx).notifyCellsRepair(_repairActive.value)
        }
    }

    fun noteRepairFinished(count: Int = 1) {
        _repairActive.value = (_repairActive.value - count).coerceAtLeast(0)
        _stats.value = _stats.value.copy(repairsActive = _repairActive.value)
    }

    suspend fun publish(
        token: String,
        blobId: String,
        conversationId: Int,
        plainFile: File,
        mime: String,
    ): Boolean =
        withContext(Dispatchers.IO) {
            val id = normalizeUploadId(blobId) ?: return@withContext false
            if (!plainFile.exists() || plainFile.length() < ProtoCellsConfig.MIN_BLOB_BYTES) {
                return@withContext false
            }
            val jobId = "cells-pub-$id"
            ProtoTransferProgressHub.begin(jobId, "PROTO Cells · upload")
            enrollMandatory(token)
            runCatching {
                val plain = plainFile.readBytes()
                val enc = ProtoCellsCrypto.encrypt(plain)
                val shards = ProtoCellsCodec.splitWithParity(enc.cipher)
                val shardHashes = shards.mapIndexed { i, s -> ProtoCellsCrypto.shardMac(s, id, i) }
                ProtoTransferProgressHub.update(jobId, 0.15f)
                val ok =
                    api.cellsRegisterBlob(
                        token,
                        id,
                        conversationId,
                        mime,
                        plain.size,
                        enc.cipher.size,
                        ProtoCellsConfig.SHARD_COUNT,
                        enc.cipherHash,
                        ProtoCellsCrypto.keyToB64(enc.key),
                        shardHashes,
                    )
                if (!ok) return@runCatching false
                shards.forEachIndexed { i, bytes ->
                    store.writeShard(id, i, bytes, shardHashes[i])
                    val payload = store.readStoredPayload(id, i) ?: bytes
                    api.cellsPushShard(token, id, i, shardHashes[i], payload)
                    ProtoTransferProgressHub.update(jobId, 0.2f + 0.75f * ((i + 1).toFloat() / shards.size))
                }
                true
            }.getOrElse {
                Log.w(TAG, "publish failed", it)
                false
            }.also {
                ProtoTransferProgressHub.end(jobId)
                if (it) refreshStats(token)
            }
        }

    suspend fun assembleToFile(
        token: String,
        blobId: String,
        conversationId: Int,
        destPlain: File,
        mime: String,
    ): File? =
        withContext(Dispatchers.IO) {
            val id = normalizeUploadId(blobId) ?: return@withContext null
            val jobId = "cells-dl-$id"
            ProtoTransferProgressHub.begin(jobId, "PROTO Cells · download")
            try {
                val manifest = api.cellsManifest(token, id) ?: return@withContext null
                val blob = manifest.optJSONObject("blob") ?: return@withContext null
                val shardCount = blob.optInt("shard_count", ProtoCellsConfig.SHARD_COUNT)
                val dataN = ProtoCellsConfig.dataShardCount(shardCount)
                val isXor = ProtoCellsConfig.isXorParityBlob(shardCount)
                val cipherSize = blob.optInt("cipher_size", 0)
                val plainSize = blob.optInt("plain_size", 0)
                val cipherHash = blob.optString("cipher_hash", "")
                val keyB64 = blob.optString("key_b64", "")
                if (cipherSize <= 0 || keyB64.isBlank()) return@withContext null

                val holders = manifest.optJSONArray("holders") ?: JSONArray()
                val relay = manifest.optJSONArray("relay") ?: JSONArray()
                val hashByIndex = mutableMapOf<Int, String>()
                for (j in 0 until holders.length()) {
                    val o = holders.optJSONObject(j) ?: continue
                    hashByIndex[o.optInt("shard_index")] = o.optString("shard_hash", "")
                }

                val shardBytes = arrayOfNulls<ByteArray>(shardCount)
                for (i in 0 until shardCount) {
                    ProtoTransferProgressHub.update(jobId, (i.toFloat() / shardCount) * 0.85f)
                    val expected = hashByIndex[i].orEmpty()
                    val local = store.readShard(id, i)
                    if (local != null && expected.isNotBlank()) {
                        val mac = ProtoCellsCrypto.shardMac(local, id, i)
                        if (mac.equals(expected, ignoreCase = true)) {
                            shardBytes[i] = local
                            continue
                        }
                    }
                    var fetched: ByteArray? = null
                    var relayAvail = false
                    for (r in 0 until relay.length()) {
                        val ro = relay.optJSONObject(r) ?: continue
                        if (ro.optInt("shard_index") == i && ro.optBoolean("available", false)) {
                            relayAvail = true
                            break
                        }
                    }
                    if (relayAvail) {
                        fetched = api.cellsFetchShard(token, id, i)
                    }
                    if (fetched != null && expected.isNotBlank()) {
                        val raw = normalizeFetchedShard(fetched)
                        if (ProtoCellsCrypto.shardMac(raw, id, i).equals(expected, ignoreCase = true)) {
                            store.writeShard(id, i, raw, expected)
                            shardBytes[i] = raw
                        }
                    }
                }

                if (isXor) {
                    val dataArr =
                        Array<ByteArray?>(ProtoCellsConfig.DATA_SHARD_COUNT) { i ->
                            if (i < dataN) shardBytes[i] else null
                        }
                    val missingBefore = (0 until dataN).filter { shardBytes[it] == null }
                    if (missingBefore.size == 1 && shardBytes[ProtoCellsConfig.PARITY_INDEX] != null) {
                        if (ProtoCellsCodec.recoverOneDataShard(dataArr, shardBytes[ProtoCellsConfig.PARITY_INDEX])) {
                            val idx = missingBefore[0]
                            dataArr[idx]?.let { recovered ->
                                shardBytes[idx] = recovered
                                val expected = hashByIndex[idx].orEmpty()
                                if (expected.isNotBlank()) {
                                    store.writeShard(id, idx, recovered, expected)
                                }
                            }
                        }
                    }
                }

                val missing = (0 until dataN).filter { shardBytes[it] == null }
                if (missing.isNotEmpty()) {
                    noteRepairStarted(missing.size)
                    api.cellsRepairRequest(token, id, conversationId, missing)
                    return@withContext null
                }

                val parts = (0 until dataN).map { shardBytes[it] ?: return@withContext null }
                val joined = ProtoCellsCodec.join(parts, cipherSize)
                if (!ProtoCellsCrypto.sha256Hex(joined).equals(cipherHash, ignoreCase = true)) {
                    Log.w(TAG, "cipher hash mismatch")
                    return@withContext null
                }
                val key = ProtoCellsCrypto.keyFromB64(keyB64)
                val plain = ProtoCellsCrypto.decrypt(joined, key)
                val out = plain.copyOf(plainSize.coerceIn(0, plain.size))
                destPlain.parentFile?.mkdirs()
                destPlain.writeBytes(out)
                noteRepairFinished(1)
                ProtoTransferProgressHub.update(jobId, 1f)
                destPlain
            } finally {
                ProtoTransferProgressHub.end(jobId)
            }
        }

    suspend fun syncMyHolds(token: String) =
        withContext(Dispatchers.IO) {
            val jobId = "cells-sync"
            ProtoTransferProgressHub.begin(jobId, "PROTO Cells · sync")
            try {
                enrollMandatory(token)
                val holds = api.cellsMyHolds(token) ?: return@withContext
                val acks = mutableListOf<Pair<String, Int>>()
                val total = holds.length().coerceAtLeast(1)
                for (i in 0 until holds.length()) {
                    ProtoTransferProgressHub.update(jobId, i.toFloat() / total)
                    val o = holds.optJSONObject(i) ?: continue
                    val blobId = o.optString("blob_id", "")
                    val idx = o.optInt("shard_index", -1)
                    if (blobId.isBlank() || idx < 0) continue
                    if (store.readShard(blobId, idx) != null) {
                        acks.add(blobId to idx)
                        continue
                    }
                    val hash = o.optString("shard_hash", "")
                    val fetched = api.cellsFetchShard(token, blobId, idx) ?: continue
                    val raw = normalizeFetchedShard(fetched)
                    if (hash.isNotBlank() && !ProtoCellsCrypto.shardMac(raw, blobId, idx).equals(hash, ignoreCase = true)) {
                        continue
                    }
                    if (store.writeShard(blobId, idx, raw, hash.ifBlank { ProtoCellsCrypto.shardMac(raw, blobId, idx) })) {
                        acks.add(blobId to idx)
                    }
                }
                acks.forEach { (bid, idx) -> api.cellsAckShard(token, bid, idx) }
                if (acks.isNotEmpty()) {
                    val arr = JSONArray()
                    acks.forEach { (bid, idx) ->
                        arr.put(JSONObject().put("blob_id", bid).put("shard_index", idx))
                    }
                    api.cellsHeartbeat(token, arr)
                }
                lastSyncAt = System.currentTimeMillis()
                refreshStats(token)
            } finally {
                ProtoTransferProgressHub.end(jobId)
            }
        }

    suspend fun repairFromLocal(
        token: String,
        blobId: String,
        conversationId: Int,
        plainFile: File,
        mime: String,
        missingIndices: List<Int>,
    ): Boolean =
        withContext(Dispatchers.IO) {
            if (!plainFile.exists()) return@withContext false
            val id = normalizeUploadId(blobId) ?: return@withContext false
            noteRepairStarted(missingIndices.size)
            val jobId = "cells-repair-$id"
            ProtoTransferProgressHub.begin(jobId, "PROTO Cells · repair")
            runCatching {
                val enc = ProtoCellsCrypto.encrypt(plainFile.readBytes())
                val shards = ProtoCellsCodec.splitWithParity(enc.cipher)
                val distinct = missingIndices.distinct()
                distinct.forEachIndexed { n, idx ->
                    if (idx !in shards.indices) return@forEachIndexed
                    val hash = ProtoCellsCrypto.shardMac(shards[idx], id, idx)
                    store.writeShard(id, idx, shards[idx], hash)
                    val payload = store.readStoredPayload(id, idx) ?: shards[idx]
                    api.cellsPushShard(token, id, idx, hash, payload)
                    api.cellsAckShard(token, id, idx)
                    ProtoTransferProgressHub.update(jobId, (n + 1).toFloat() / distinct.size)
                }
                noteRepairFinished(missingIndices.size)
                refreshStats(token)
                true
            }.getOrDefault(false).also {
                ProtoTransferProgressHub.end(jobId)
            }
        }

    suspend fun runMaintenance(token: String) {
        if (!network.checkOnline()) return
        enrollMandatory(token)
        syncMyHolds(token)
    }

    private fun normalizeFetchedShard(payload: ByteArray): ByteArray =
        if (ProtoCellsConfig.COMPRESS_SHARDS && ProtoCellsCompression.isCompressed(payload)) {
            ProtoCellsCompression.decompress(payload)
        } else {
            payload
        }

    companion object {
        private const val TAG = "ProtoCells"
    }
}
