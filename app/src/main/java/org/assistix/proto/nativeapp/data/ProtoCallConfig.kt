package org.assistix.proto.nativeapp.data

import org.assistix.proto.nativeapp.BuildConfig
import org.webrtc.PeerConnection

/** Public STUN/TURN pack — works without own coturn; merged with API list on device. */
object ProtoCallConfig {
    private const val OPEN_RELAY_USER = "openrelayproject"
    private const val OPEN_RELAY_PASS = "openrelayproject"

    private fun protoVpsTurn(): RtcIceServer? {
        val host = BuildConfig.TURN_HOST.trim()
        val user = BuildConfig.TURN_USER.trim()
        val cred = BuildConfig.TURN_CRED.trim()
        if (host.isBlank() || user.isBlank() || cred.isBlank()) return null
        return RtcIceServer(
            listOf(
                "stun:$host:3478",
                "turn:$host:3478",
                "turn:$host:3478?transport=udp",
                "turn:$host:3478?transport=tcp",
                "turn:turn.proto.su:3478",
                "turn:turn.proto.su:3478?transport=udp",
                "turn:turn.proto.su:3478?transport=tcp",
            ),
            user,
            cred,
        )
    }

    private fun meteredOpenRelay(): RtcIceServer =
        RtcIceServer(
            listOf(
                "stun:stun.relay.metered.ca:80",
                "turn:openrelay.metered.ca:80",
                "turn:openrelay.metered.ca:443",
                "turn:openrelay.metered.ca:443?transport=tcp",
                "turns:openrelay.metered.ca:443?transport=tcp",
                "turn:openrelay.metered.ca:80?transport=tcp",
                "turn:global.relay.metered.ca:80",
                "turn:global.relay.metered.ca:80?transport=tcp",
                "turn:global.relay.metered.ca:443",
                "turns:global.relay.metered.ca:443?transport=tcp",
            ),
            OPEN_RELAY_USER,
            OPEN_RELAY_PASS,
        )

    fun fallbackIceServers(): List<RtcIceServer> =
        listOfNotNull(
            protoVpsTurn(),
            meteredOpenRelay(),
            RtcIceServer(listOf("stun:stun.l.google.com:19302"), null, null),
            RtcIceServer(listOf("stun:stun1.l.google.com:19302"), null, null),
            RtcIceServer(listOf("stun:stun2.l.google.com:19302"), null, null),
            RtcIceServer(listOf("stun:stun3.l.google.com:19302"), null, null),
            RtcIceServer(listOf("stun:stun4.l.google.com:19302"), null, null),
            RtcIceServer(listOf("stun:stun.cloudflare.com:3478"), null, null),
            RtcIceServer(listOf("stun:global.stun.twilio.com:3478"), null, null),
            RtcIceServer(listOf("stun:stun.nextcloud.com:443"), null, null),
            RtcIceServer(listOf("stun:stun.stunprotocol.org:3478"), null, null),
            RtcIceServer(listOf("stun:stun.voip.blackberry.com:3478"), null, null),
            RtcIceServer(listOf("stun:stun.communication.microsoft.com:3478"), null, null),
            RtcIceServer(listOf("stun:stun.numlex.ru"), null, null),
            RtcIceServer(listOf("stun:stun.lds.net.ua"), null, null),
            RtcIceServer(listOf("stun:stun.voipstunt.com"), null, null),
        )

    fun mergeIceServers(primary: List<RtcIceServer>): List<RtcIceServer> {
        if (primary.isEmpty()) return fallbackIceServers()
        val seen = mutableSetOf<String>()
        primary.forEach { srv -> srv.urls.forEach { seen.add(it) } }
        val out = primary.toMutableList()
        for (fb in fallbackIceServers()) {
            if (fb.urls.any { it !in seen }) {
                out.add(fb)
                fb.urls.forEach { seen.add(it) }
            }
        }
        return out
    }

    fun toPeerIceServers(ice: List<RtcIceServer>): List<PeerConnection.IceServer> =
        ice.mapNotNull { srv ->
            val urls = srv.urls.filter { it.isNotBlank() }
            if (urls.isEmpty()) return@mapNotNull null
            PeerConnection.IceServer.builder(urls)
                .apply {
                    srv.username?.let { setUsername(it) }
                    srv.credential?.let { setPassword(it) }
                }
                .createIceServer()
        }

    fun applyRtcTuning(cfg: PeerConnection.RTCConfiguration, relayOnly: Boolean) {
        cfg.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        cfg.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        cfg.iceTransportsType =
            if (relayOnly) PeerConnection.IceTransportsType.RELAY else PeerConnection.IceTransportsType.ALL
        cfg.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED
        cfg.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
        cfg.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
        cfg.iceCandidatePoolSize = 16
        cfg.enableCpuOveruseDetection = false
        cfg.suspendBelowMinBitrate = false
        cfg.presumeWritableWhenFullyRelayed = true
        cfg.iceConnectionReceivingTimeout = 60_000
    }
}
