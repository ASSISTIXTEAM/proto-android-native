package org.assistix.proto.nativeapp.data.cells

import android.content.Context
import android.util.Log
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assistix.proto.nativeapp.data.ProtoApi
import org.assistix.proto.nativeapp.data.ProtoNetworkMonitor
import org.assistix.proto.nativeapp.data.normalizeUploadId
import org.json.JSONArray
import org.json.JSONObject

/**
 * PROTO Cells — mandatory encrypted shard mesh across chat members and volunteers.
 * Server stores catalog + brief shard relay only.
 */
class ProtoCellsManager(
    private val context: Context,
    private val api: ProtoApi,
    private val network: ProtoNetworkMonitor,
) {
    private val store = ProtoCellsStore(context)

    /** Auto-enroll every signed-in user — Cells is mandatory for all PROTO accounts. */
    suspend fun enrollMandatory(token: String): Boolean =
        withContext(Dispatchers.IO) {
            api.cellsVolunteer(token, enabled = true, quotaBytes = ProtoCellsConfig.DEFAULT_QUOTA_BYTES)
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
            enrollMandatory(token)
            runCatching {
                val plain = plainFile.readBytes()
                val enc = ProtoCellsCrypto.encrypt(plain)
                val shards = ProtoCellsCodec.split(enc.cipher, ProtoCellsConfig.SHARD_COUNT)
                val shardHashes = shards.mapIndexed { i, s -> ProtoCellsCrypto.shardMac(s, id, i) }
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
                }
                true
            }.getOrElse {
                Log.w(TAG, "publish failed", it)
                false
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
            val manifest = api.cellsManifest(token, id) ?: return@withContext null
            val blob = manifest.optJSONObject("blob") ?: return@withContext null
            val shardCount = blob.optInt("shard_count", ProtoCellsConfig.SHARD_COUNT)
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

            val missing = (0 until shardCount).filter { shardBytes[it] == null }
            if (missing.isNotEmpty()) {
                api.cellsRepairRequest(token, id, conversationId, missing)
                return@withContext null
            }

            val joined = ProtoCellsCodec.join(shardBytes.map { it!! }, cipherSize)
            if (!ProtoCellsCrypto.sha256Hex(joined).equals(cipherHash, ignoreCase = true)) {
                Log.w(TAG, "cipher hash mismatch")
                return@withContext null
            }
            val key = ProtoCellsCrypto.keyFromB64(keyB64)
            val plain = ProtoCellsCrypto.decrypt(joined, key)
            val out = plain.copyOf(plainSize.coerceIn(0, plain.size))
            destPlain.parentFile?.mkdirs()
            destPlain.writeBytes(out)
            destPlain
        }

    suspend fun syncMyHolds(token: String) =
        withContext(Dispatchers.IO) {
            enrollMandatory(token)
            val holds = api.cellsMyHolds(token) ?: return@withContext
            val acks = mutableListOf<Pair<String, Int>>()
            for (i in 0 until holds.length()) {
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
            runCatching {
                val enc = ProtoCellsCrypto.encrypt(plainFile.readBytes())
                val shards = ProtoCellsCodec.split(enc.cipher, ProtoCellsConfig.SHARD_COUNT)
                missingIndices.distinct().forEach { idx ->
                    if (idx !in shards.indices) return@forEach
                    val hash = ProtoCellsCrypto.shardMac(shards[idx], id, idx)
                    store.writeShard(id, idx, shards[idx], hash)
                    val payload = store.readStoredPayload(id, idx) ?: shards[idx]
                    api.cellsPushShard(token, id, idx, hash, payload)
                    api.cellsAckShard(token, id, idx)
                }
                true
            }.getOrDefault(false)
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
