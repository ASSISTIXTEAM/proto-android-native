package org.assistix.proto.nativeapp.data

import android.util.Log
import org.webrtc.CameraVideoCapturer
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.RTCStatsReport
import org.webrtc.RtpParameters
import org.webrtc.RtpSender
import org.webrtc.SessionDescription
import java.util.regex.Pattern

/**
 * Адаптивное видео для звонков: H.264 в SDP, битрейт 360p–720p, приоритет плавности (как NoLACE / MAINTAIN_FRAMERATE).
 */
object ProtoCallVideo {
    private const val TAG = "ProtoCallVideo"

    enum class Tier(
        val width: Int,
        val height: Int,
        val fps: Int,
        val maxBitrateBps: Int,
        val minBitrateBps: Int,
        val scaleResolutionDownBy: Double,
    ) {
        HD(1280, 720, 30, 1_800_000, 380_000, 1.0),
        SD(960, 540, 24, 1_050_000, 260_000, 1.0),
        LOW(640, 360, 20, 580_000, 140_000, 1.0),
        MIN(480, 360, 15, 300_000, 75_000, 1.15),
    }

    @Volatile
    var currentTier: Tier = Tier.SD
        private set

    fun reset() {
        currentTier = Tier.SD
    }

    /** Голос (Opus FEC) + H.264 в приоритете для видео. */
    fun tuneSdp(sdp: String): String {
        var out = ProtoCallAudio.tuneSdp(sdp)
        out = stripVideoPayloadTypes(out, setOf("VP9", "AV1"))
        out = preferCodec(out, "H264", audio = false)
        return out
    }

    fun tunedDescription(type: SessionDescription.Type, sdp: String): SessionDescription {
        val tuned =
            runCatching { tuneSdp(sdp) }.getOrElse { e ->
                Log.w(TAG, "tuneSdp failed, using raw SDP", e)
                sdp
            }
        return SessionDescription(type, tuned)
    }

    fun applyOutboundVideo(pc: PeerConnection?, tier: Tier = currentTier) {
        if (pc == null) return
        val sender =
            pc.senders.firstOrNull { it.track()?.kind() == MediaStreamTrack.VIDEO_TRACK_KIND }
                ?: return
        applySenderTier(sender, tier)
    }

    fun applySenderTier(sender: RtpSender, tier: Tier) {
        currentTier = tier
        val params = sender.parameters
        val encodings = params.encodings.toMutableList()
        if (encodings.isEmpty()) {
            encodings.add(RtpParameters.Encoding(null, true, null))
        }
        val enc = encodings[0]
        enc.active = true
        enc.maxBitrateBps = tier.maxBitrateBps
        enc.minBitrateBps = tier.minBitrateBps
        enc.maxFramerate = tier.fps
        enc.scaleResolutionDownBy = tier.scaleResolutionDownBy
        params.encodings = encodings
        params.degradationPreference = RtpParameters.DegradationPreference.MAINTAIN_FRAMERATE
        if (!sender.setParameters(params)) {
            Log.w(TAG, "setParameters failed tier=$tier")
        }
        Log.d(TAG, "encoder tier=$tier max=${tier.maxBitrateBps}")
    }

    fun applyCaptureTier(capturer: CameraVideoCapturer?, tier: Tier) {
        if (capturer == null) return
        currentTier = tier
        runCatching {
            capturer.changeCaptureFormat(tier.width, tier.height, tier.fps)
            Log.d(TAG, "capture ${tier.width}x${tier.height}@${tier.fps}")
        }.onFailure { Log.w(TAG, "changeCaptureFormat", it) }
    }

    data class LinkStats(
        val outboundBitrateBps: Long = 0,
        val availableOutgoingBitrateBps: Long = 0,
        val framesEncoded: Long = 0,
        val rttMs: Double = 0.0,
    )

    fun parseStats(report: RTCStatsReport): LinkStats {
        var outbound = 0L
        var available = 0L
        var frames = 0L
        var rtt = 0.0
        for (stat in report.statsMap.values) {
            when (stat.type) {
                "outbound-rtp" -> {
                    if (stat.members["kind"]?.toString() == "video") {
                        outbound = stat.members["bytesSent"]?.toString()?.toLongOrNull() ?: outbound
                        frames = stat.members["framesEncoded"]?.toString()?.toLongOrNull() ?: frames
                    }
                }
                "candidate-pair" -> {
                    if (stat.members["nominated"]?.toString() == "true") {
                        val b =
                            stat.members["availableOutgoingBitrate"]?.toString()?.toDoubleOrNull()
                                ?: 0.0
                        if (b > available) available = b.toLong()
                        val r = stat.members["currentRoundTripTime"]?.toString()?.toDoubleOrNull()
                        if (r != null && r > 0) rtt = r * 1000.0
                    }
                }
            }
        }
        return LinkStats(
            outboundBitrateBps = outbound,
            availableOutgoingBitrateBps = available,
            framesEncoded = frames,
            rttMs = rtt,
        )
    }

    fun pickTier(
        stats: LinkStats,
        bytesPerSec: Long,
        connectionBars: Int,
        current: Tier,
    ): Tier {
        val bps =
            when {
                stats.availableOutgoingBitrateBps > 0 -> stats.availableOutgoingBitrateBps
                bytesPerSec > 0 -> bytesPerSec * 8
                else -> 0L
            }
        val candidate =
            when {
                bps >= 1_350_000 && connectionBars >= 3 -> Tier.HD
                bps >= 750_000 || connectionBars >= 3 -> Tier.SD
                bps >= 380_000 || connectionBars >= 2 -> Tier.LOW
                bps > 0 || connectionBars >= 1 -> Tier.MIN
                else -> current
            }
        return stabilizeTier(current, candidate)
    }

    /** Не дёргать качество слишком часто — гистерезис на один шаг. */
    private fun stabilizeTier(current: Tier, candidate: Tier): Tier {
        if (candidate == current) return current
        val curOrd = current.ordinal
        val candOrd = candidate.ordinal
        return when {
            candOrd > curOrd + 1 -> Tier.entries[curOrd + 1]
            candOrd < curOrd - 1 -> Tier.entries[curOrd - 1]
            else -> candidate
        }
    }

    private fun stripVideoPayloadTypes(sdp: String, codecNames: Set<String>): String {
        if (codecNames.isEmpty()) return sdp
        val lines = sdp.replace("\r\n", "\n").split("\n").toMutableList()
        val removePts = mutableSetOf<String>()
        val rtxPts = mutableSetOf<String>()
        for (line in lines) {
            val rtpmap = lineRtpmap(line) ?: continue
            val (pt, codec) = rtpmap
            if (!codecNames.any { codec.equals(it, ignoreCase = true) }) continue
            removePts.add(pt)
        }
        for (line in lines) {
            if (!line.startsWith("a=fmtp:")) continue
            val pt = line.substringAfter("a=fmtp:").substringBefore(" ")
            if (line.contains("apt=", ignoreCase = true) && removePts.any { line.contains("apt=$it") }) {
                rtxPts.add(pt)
            }
        }
        removePts.addAll(rtxPts)
        if (removePts.isEmpty()) return sdp
        val mIdx = lines.indexOfFirst { it.startsWith("m=video") }
        if (mIdx >= 0) {
            val parts = lines[mIdx].split(" ").toMutableList()
            if (parts.size > 3) {
                val head = parts.take(3)
                val payloads = parts.drop(3).filter { it !in removePts }
                lines[mIdx] = (head + payloads).joinToString(" ")
            }
        }
        return lines.filter { line ->
            val pt = linePayloadType(line) ?: return@filter true
            pt !in removePts
        }.joinToString("\r\n") + "\r\n"
    }

    private fun preferCodec(sdp: String, codec: String, audio: Boolean): String {
        val lines = sdp.replace("\r\n", "\n").split("\n").toMutableList()
        val mIdx =
            lines.indexOfFirst { line ->
                if (audio) line.startsWith("m=audio") else line.startsWith("m=video")
            }
        if (mIdx < 0) return sdp
        val parts = lines[mIdx].split(" ").toMutableList()
        if (parts.size <= 3) return sdp
        val header = parts.take(3)
        val payloads = parts.drop(3).toMutableList()
        val preferred = mutableListOf<String>()
        val codecPattern = Pattern.compile("a=rtpmap:(\\d+) $codec.*", Pattern.CASE_INSENSITIVE)
        for (line in lines) {
            val m = codecPattern.matcher(line)
            if (m.matches()) {
                val pt = m.group(1) ?: continue
                if (!preferred.contains(pt)) preferred.add(pt)
                val rtx = findRtxPayload(lines, pt)
                if (rtx != null && !preferred.contains(rtx)) preferred.add(rtx)
            }
        }
        if (preferred.isEmpty()) return sdp
        val rest = payloads.filter { it !in preferred }
        lines[mIdx] = (header + preferred + rest).joinToString(" ")
        return lines.joinToString("\r\n") + "\r\n"
    }

    private fun findRtxPayload(lines: List<String>, aptPt: String): String? {
        for (line in lines) {
            if (!line.startsWith("a=fmtp:$aptPt ")) continue
        }
        for (line in lines) {
            if (!line.startsWith("a=fmtp:")) continue
            if (line.contains("apt=$aptPt")) {
                return line.substringAfter("a=fmtp:").substringBefore(" ")
            }
        }
        return null
    }

    private fun lineRtpmap(line: String): Pair<String, String>? {
        if (!line.startsWith("a=rtpmap:")) return null
        val body = line.removePrefix("a=rtpmap:")
        val pt = body.substringBefore(" ").trim()
        val codec = body.substringAfter(" ").substringBefore("/").trim()
        if (pt.isEmpty() || codec.isEmpty()) return null
        return pt to codec
    }

    private fun linePayloadType(line: String): String? {
        if (line.startsWith("a=rtpmap:")) return line.substringAfter("a=rtpmap:").substringBefore(" ")
        if (line.startsWith("a=fmtp:")) return line.substringAfter("a=fmtp:").substringBefore(" ")
        if (line.startsWith("a=rtcp-fb:")) return line.substringAfter("a=rtcp-fb:").substringBefore(" ")
        return null
    }
}
