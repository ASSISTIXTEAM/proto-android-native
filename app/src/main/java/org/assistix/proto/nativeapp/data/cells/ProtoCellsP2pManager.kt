package org.assistix.proto.nativeapp.data.cells

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.assistix.proto.nativeapp.data.ProtoApi
import org.assistix.proto.nativeapp.data.ProtoCallConfig
import org.assistix.proto.nativeapp.data.ProtoCallGateway
import org.assistix.proto.nativeapp.data.ProtoNetworkMonitor
import org.assistix.proto.nativeapp.data.ProtoTransferProgressHub

/**
 * WebRTC DataChannel shard transfer between chat members.
 * Signaling via webrtc.php kind=signal JSON envelope — relay HTTP is fallback only.
 */
class ProtoCellsP2pManager(
    private val context: Context,
    private val api: ProtoApi,
    private val network: ProtoNetworkMonitor,
    private val calls: () -> ProtoCallGateway?,
) {
    private val appCtx = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val sessionMutex = Mutex()
    private val sinceByConversation = ConcurrentHashMap<Int, Long>()
    private val factoryReady = AtomicBoolean(false)
    @Volatile
    private var factory: PeerConnectionFactory? = null

    suspend fun fetchShard(
        token: String,
        myUserId: Int,
        conversationId: Int,
        blobId: String,
        shardIndex: Int,
        expectedHash: String,
        holderUserIds: List<Int>,
    ): ByteArray? =
        withContext(Dispatchers.IO) {
            if (!network.checkOnline() || calls()?.state?.value?.active == true) return@withContext null
            if (holderUserIds.isEmpty()) return@withContext null
            val targets = holderUserIds.filter { it > 0 && it != myUserId }.distinct()
            if (targets.isEmpty()) return@withContext null
            val jobId = "cells-p2p-dl-$blobId-$shardIndex"
            ProtoTransferProgressHub.begin(jobId, "PROTO Cells · P2P")
            try {
                for (holderId in targets) {
                    val bytes =
                        withTimeoutOrNull(P2P_TIMEOUT_MS) {
                            requestShard(token, myUserId, conversationId, blobId, shardIndex, expectedHash, holderId)
                        }
                    if (bytes != null) {
                        ProtoTransferProgressHub.update(jobId, 1f)
                        return@withContext bytes
                    }
                }
                null
            } finally {
                ProtoTransferProgressHub.end(jobId)
            }
        }

    /** Poll conversation for incoming cell P2P signals and serve shards we hold. */
    suspend fun pollServeConversation(token: String, myUserId: Int, conversationId: Int, store: ProtoCellsStore) =
        withContext(Dispatchers.IO) {
            if (!network.checkOnline() || calls()?.state?.value?.active == true) return@withContext
            val since = sinceByConversation[conversationId] ?: api.webrtcCursor(token, conversationId)
            val signals = api.webrtcPoll(token, conversationId, since)
            var maxId = since
            for (sig in signals) {
                if (sig.id > maxId) maxId = sig.id
                if (sig.kind != "signal") continue
                val body = parseEnvelope(sig.payload) ?: continue
                if (body.optString("phase") == "req" && body.optInt("target_user_id", 0) == myUserId) {
                    handleRequest(token, myUserId, conversationId, store, sig.senderId, body)
                }
            }
            sinceByConversation[conversationId] = maxId
        }

    private suspend fun requestShard(
        token: String,
        myUserId: Int,
        conversationId: Int,
        blobId: String,
        shardIndex: Int,
        expectedHash: String,
        holderId: Int,
    ): ByteArray? =
        sessionMutex.withLock {
            val sid = sessionId(blobId, shardIndex, myUserId)
            val result = CompletableDeferred<ByteArray?>()
            var pc: PeerConnection? = null
            main.post {
                runCatching {
                    ensureFactory()
                    val f = factory ?: return@post
                    pc = createPeer(f, token, conversationId, sid, asHolder = false) { channel ->
                        wireReceiver(channel, expectedHash, blobId, shardIndex, result)
                    }
                    postSignal(
                        token,
                        conversationId,
                        JSONObject()
                            .put("cell_p2p", true)
                            .put("phase", "req")
                            .put("sid", sid)
                            .put("blob_id", blobId)
                            .put("shard_index", shardIndex)
                            .put("shard_hash", expectedHash)
                            .put("target_user_id", holderId)
                            .put("requester_id", myUserId),
                    )
                }.onFailure {
                    Log.w(TAG, "p2p req setup", it)
                    result.complete(null)
                }
            }
            val deadline = System.currentTimeMillis() + P2P_TIMEOUT_MS
            while (System.currentTimeMillis() < deadline && !result.isCompleted) {
                val since = sinceByConversation[conversationId] ?: 0L
                val signals = api.webrtcPoll(token, conversationId, since)
                var maxId = since
                for (sig in signals) {
                    if (sig.id > maxId) maxId = sig.id
                    if (sig.senderId != holderId) continue
                    val body = parseEnvelope(sig.payload) ?: continue
                    if (body.optString("sid") != sid) continue
                    when (body.optString("phase")) {
                        "offer" -> {
                            val conn = pc ?: continue
                            val sdp = body.optString("sdp", "")
                            val type = body.optString("sdp_type", "offer")
                            if (sdp.isNotBlank()) {
                                main.post {
                                    conn.setRemoteDescription(
                                        sdpObserver {
                                            val constraints =
                                                MediaConstraints().apply {
                                                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"))
                                                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
                                                }
                                            conn.createAnswer(
                                                object : SdpObserver {
                                                    override fun onCreateSuccess(desc: SessionDescription?) {
                                                        desc ?: return
                                                        conn.setLocalDescription(sdpObserver {}, desc)
                                                        postSignal(
                                                            token,
                                                            conversationId,
                                                            JSONObject()
                                                                .put("cell_p2p", true)
                                                                .put("phase", "answer")
                                                                .put("sid", sid)
                                                                .put("sdp", desc.description)
                                                                .put("sdp_type", desc.type.canonicalForm()),
                                                        )
                                                    }

                                                    override fun onSetSuccess() {}

                                                    override fun onCreateFailure(p0: String?) {}

                                                    override fun onSetFailure(p0: String?) {}
                                                },
                                                constraints,
                                            )
                                        },
                                        SessionDescription(SessionDescription.Type.fromCanonicalForm(type), sdp),
                                    )
                                }
                            }
                        }
                        "ice" -> handleRemoteIce(body, pc)
                    }
                }
                sinceByConversation[conversationId] = maxId
                delay(180)
            }
            val out = withTimeoutOrNull(2_000) { result.await() }
            main.post { pc?.close(); pc?.dispose() }
            out
        }

    private suspend fun handleRequest(
        token: String,
        myUserId: Int,
        conversationId: Int,
        store: ProtoCellsStore,
        requesterId: Int,
        body: JSONObject,
    ) {
        if (body.optInt("target_user_id", 0) != myUserId) return
        val blobId = body.optString("blob_id", "")
        val shardIndex = body.optInt("shard_index", -1)
        val expectedHash = body.optString("shard_hash", "")
        val sid = body.optString("sid", "")
        if (blobId.isBlank() || shardIndex < 0 || sid.isBlank()) return
        val payload = store.readStoredPayload(blobId, shardIndex) ?: return
        val raw = store.readShard(blobId, shardIndex) ?: return
        if (expectedHash.isNotBlank() &&
            !ProtoCellsCrypto.shardMac(raw, blobId, shardIndex).equals(expectedHash, ignoreCase = true)
        ) {
            return
        }
        sessionMutex.withLock {
            var pc: PeerConnection? = null
            val sent = CompletableDeferred<Boolean>()
            main.post {
                runCatching {
                    ensureFactory()
                    val f = factory ?: return@post
                    val init = DataChannel.Init().apply { ordered = true; id = 1 }
                    pc = createPeer(f, token, conversationId, sid, asHolder = true, onChannel = null)
                    val dc = pc!!.createDataChannel("cells", init)
                    wireSender(dc, payload) { ok -> sent.complete(ok) }
                    createOffer(token, conversationId, pc!!, sid)
                }.onFailure {
                    Log.w(TAG, "p2p serve setup", it)
                    sent.complete(false)
                }
            }
            val deadline = System.currentTimeMillis() + P2P_TIMEOUT_MS
            while (System.currentTimeMillis() < deadline && !sent.isCompleted) {
                val since = sinceByConversation[conversationId] ?: 0L
                val signals = api.webrtcPoll(token, conversationId, since)
                var maxId = since
                for (sig in signals) {
                    if (sig.id > maxId) maxId = sig.id
                    if (sig.senderId != requesterId) continue
                    val msg = parseEnvelope(sig.payload) ?: continue
                    if (msg.optString("sid") != sid) continue
                    when (msg.optString("phase")) {
                        "answer" -> {
                            val conn = pc ?: continue
                            val sdp = msg.optString("sdp", "")
                            val type = msg.optString("sdp_type", "answer")
                            if (sdp.isNotBlank()) {
                                main.post {
                                    conn.setRemoteDescription(
                                        sdpObserver {},
                                        SessionDescription(SessionDescription.Type.fromCanonicalForm(type), sdp),
                                    )
                                }
                            }
                        }
                        "ice" -> handleRemoteIce(msg, pc)
                    }
                }
                sinceByConversation[conversationId] = maxId
                delay(160)
            }
            withTimeoutOrNull(3_000) { sent.await() }
            main.post { pc?.close(); pc?.dispose() }
        }
    }

    private fun handleRemoteIce(body: JSONObject, pc: PeerConnection? = null) {
        val candidate = body.optString("candidate", "")
        if (candidate.isBlank()) return
        val ice =
            IceCandidate(
                body.optString("sdp_mid", ""),
                body.optInt("sdp_mline", 0),
                candidate,
            )
        main.post { pc?.addIceCandidate(ice) }
    }

    private fun createOffer(token: String, conversationId: Int, pc: PeerConnection, sid: String) {
        val constraints =
            MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"))
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
            }
        pc.createOffer(
            object : SdpObserver {
                override fun onCreateSuccess(desc: SessionDescription?) {
                    desc ?: return
                    pc.setLocalDescription(
                        sdpObserver {},
                        desc,
                    )
                    postSignal(
                        token,
                        conversationId,
                        JSONObject()
                            .put("cell_p2p", true)
                            .put("phase", "offer")
                            .put("sid", sid)
                            .put("sdp", desc.description)
                            .put("sdp_type", desc.type.canonicalForm()),
                    )
                }

                override fun onSetSuccess() {}

                override fun onCreateFailure(p0: String?) {
                    Log.w(TAG, "offer fail $p0")
                }

                override fun onSetFailure(p0: String?) {}
            },
            constraints,
        )
    }

    private fun createPeer(
        f: PeerConnectionFactory,
        token: String,
        conversationId: Int,
        sid: String,
        asHolder: Boolean,
        onChannel: ((DataChannel) -> Unit)?,
    ): PeerConnection {
        val ice = api.rtcConfig(token)
        val cfg = PeerConnection.RTCConfiguration(ProtoCallConfig.toPeerIceServers(ice))
        ProtoCallConfig.applyRtcTuning(cfg, relayOnly = false)
        return f.createPeerConnection(
            cfg,
            object : PeerConnection.Observer {
                override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}

                override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {}

                override fun onIceConnectionReceivingChange(p0: Boolean) {}

                override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}

                override fun onIceCandidate(candidate: IceCandidate?) {
                    candidate ?: return
                    postSignal(
                        token,
                        conversationId,
                        JSONObject()
                            .put("cell_p2p", true)
                            .put("phase", "ice")
                            .put("sid", sid)
                            .put("candidate", candidate.sdp)
                            .put("sdp_mid", candidate.sdpMid)
                            .put("sdp_mline", candidate.sdpMLineIndex),
                    )
                }

                override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}

                override fun onAddStream(p0: org.webrtc.MediaStream?) {}

                override fun onRemoveStream(p0: org.webrtc.MediaStream?) {}

                override fun onDataChannel(channel: DataChannel?) {
                    channel ?: return
                    onChannel?.invoke(channel)
                }

                override fun onRenegotiationNeeded() {}

                override fun onAddTrack(p0: org.webrtc.RtpReceiver?, p1: Array<out org.webrtc.MediaStream>?) {}
            },
        ) ?: error("no PeerConnection")
    }

    private fun wireSender(dc: DataChannel, payload: ByteArray, onDone: (Boolean) -> Unit) {
        val buf = ByteBuffer.allocate(4 + payload.size)
        buf.putInt(payload.size)
        buf.put(payload)
        buf.flip()
        dc.registerObserver(
            object : DataChannel.Observer {
                override fun onBufferedAmountChange(p0: Long) {}

                override fun onStateChange() {
                    if (dc.state() == DataChannel.State.OPEN) {
                        dc.send(DataChannel.Buffer(buf, true))
                    }
                    if (dc.state() == DataChannel.State.CLOSED) {
                        onDone(true)
                    }
                }

                override fun onMessage(p0: DataChannel.Buffer?) {}
            },
        )
    }

    private fun wireReceiver(
        dc: DataChannel,
        expectedHash: String,
        blobId: String,
        shardIndex: Int,
        result: CompletableDeferred<ByteArray?>,
    ) {
        dc.registerObserver(
            object : DataChannel.Observer {
                override fun onBufferedAmountChange(p0: Long) {}

                override fun onStateChange() {}

                override fun onMessage(buffer: DataChannel.Buffer?) {
                    buffer ?: return
                    val data = ByteArray(buffer.data.remaining())
                    buffer.data.get(data)
                    if (buffer.binary) {
                        if (data.size >= 4) {
                            val len = ByteBuffer.wrap(data, 0, 4).int
                            if (len > 0 && data.size >= 4 + len) {
                                val payload = data.copyOfRange(4, 4 + len)
                                val raw =
                                    if (ProtoCellsConfig.COMPRESS_SHARDS && ProtoCellsCompression.isCompressed(payload)) {
                                        ProtoCellsCompression.decompress(payload)
                                    } else {
                                        payload
                                    }
                                if (expectedHash.isBlank() ||
                                    ProtoCellsCrypto.shardMac(raw, blobId, shardIndex).equals(expectedHash, ignoreCase = true)
                                ) {
                                    result.complete(raw)
                                } else {
                                    result.complete(null)
                                }
                            }
                        }
                    }
                }
            },
        )
    }

    private fun ensureFactory() {
        if (factoryReady.get()) return
        synchronized(this) {
            if (factoryReady.get()) return
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(appCtx).createInitializationOptions(),
            )
            factory = PeerConnectionFactory.builder().createPeerConnectionFactory()
            factoryReady.set(true)
        }
    }

    private fun postSignal(token: String, conversationId: Int, body: JSONObject) {
        api.webrtcPost(token, conversationId, "signal", body.toString())
    }

    private fun parseEnvelope(payload: String): JSONObject? {
        if (payload.isBlank()) return null
        return runCatching {
            val j = JSONObject(payload)
            if (j.optBoolean("cell_p2p", false)) j else null
        }.getOrNull()
    }

    private fun sessionId(blobId: String, shardIndex: Int, requesterId: Int): String = "$blobId:$shardIndex:$requesterId"

    private fun sdpObserver(onSuccess: () -> Unit): SdpObserver =
        object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}

            override fun onSetSuccess() {
                onSuccess()
            }

            override fun onCreateFailure(p0: String?) {}

            override fun onSetFailure(p0: String?) {}
        }

    companion object {
        private const val TAG = "ProtoCellsP2p"
        private const val P2P_TIMEOUT_MS = 14_000L

        fun holderIdsForShard(holders: org.json.JSONArray, shardIndex: Int, myUserId: Int): List<Int> {
            val acked = mutableListOf<Int>()
            val pending = mutableListOf<Int>()
            for (i in 0 until holders.length()) {
                val o = holders.optJSONObject(i) ?: continue
                if (o.optInt("shard_index") != shardIndex) continue
                val uid = o.optInt("holder_user_id", 0)
                if (uid <= 0 || uid == myUserId) continue
                if (o.optInt("ack_at", 0) > 0) acked.add(uid) else pending.add(uid)
            }
            return (acked + pending).distinct()
        }
    }
}
