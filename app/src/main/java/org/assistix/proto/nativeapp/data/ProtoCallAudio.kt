package org.assistix.proto.nativeapp.data

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import org.webrtc.MediaConstraints
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.RTCStatsReport
import org.webrtc.RtpParameters
import org.webrtc.RtpSender
import org.webrtc.audio.JavaAudioDeviceModule
import java.util.regex.Pattern

/**
 * Голос в звонках: шумоподавление, эхоподавление, AGC, Opus FEC (восстановление при потере до ~1 с через NetEQ/PLC).
 */
object ProtoCallAudio {
    private const val TAG = "ProtoCallAudio"

    enum class Tier(
        val maxBitrateBps: Int,
        val opusMaxAverageBitrate: Int,
    ) {
        /** Отличная сеть */
        HIGH(64_000, 64_000),
        /** Обычная LTE / Wi‑Fi */
        NORMAL(40_000, 40_000),
        /** Слабый интернет */
        LOW(28_000, 28_000),
        /** Парковка / 2G–3G */
        MIN(18_000, 18_000),
    }

    @Volatile
    var currentTier: Tier = Tier.NORMAL
        private set

    fun reset() {
        currentTier = Tier.NORMAL
    }

    fun applyProbeBars(bars: Int) {
        currentTier =
            when (bars.coerceIn(0, 4)) {
                4, 3 -> Tier.HIGH
                2 -> Tier.NORMAL
                1 -> Tier.LOW
                else -> Tier.MIN
            }
    }

    /** Field trials WebRTC: APM, меньше ресэмплинга на мобильных. */
    fun fieldTrials(): String =
        buildString {
            append("WebRTC-Audio-MinimizeResamplingOnMobile/Enabled/")
            append("WebRTC-Aec3SetupOptimizations/Enabled/")
            append("WebRTC-Audio-Agc2/digital_adaptive/Enabled/")
        }

    fun createAudioDeviceModule(context: Context): JavaAudioDeviceModule {
        val hwAec = JavaAudioDeviceModule.isBuiltInAcousticEchoCancelerSupported()
        val hwNs = JavaAudioDeviceModule.isBuiltInNoiseSuppressorSupported()
        val voiceAttrs =
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        Log.d(TAG, "ADM hwAec=$hwAec hwNs=$hwNs")
        return JavaAudioDeviceModule.builder(context.applicationContext)
            .setUseHardwareAcousticEchoCanceler(hwAec)
            .setUseHardwareNoiseSuppressor(hwNs)
            .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
            .setUseLowLatency(true)
            .setAudioAttributes(voiceAttrs)
            .setSampleRate(48_000)
            .setEnableVolumeLogger(false)
            .createAudioDeviceModule()
    }

    /** WebRTC APM: эхо, шум, искажения, автогейн — включается на каждом звонке. */
    fun localAudioConstraints(): MediaConstraints {
        val mc = MediaConstraints()
        fun on(key: String) = mc.mandatory.add(MediaConstraints.KeyValuePair(key, "true"))
        fun off(key: String) = mc.mandatory.add(MediaConstraints.KeyValuePair(key, "false"))
        on("googEchoCancellation")
        on("googAutoGainControl")
        on("googNoiseSuppression")
        on("googHighpassFilter")
        on("googTypingNoiseDetection")
        on("googDAEchoCancellation")
        off("googAudioMirroring")
        return mc
    }

    /** Opus в приоритете + in-band FEC для восстановления слогов при обрыве. */
    fun tuneSdp(sdp: String): String {
        var out = preferCodec(sdp, "opus", audio = true)
        out = patchOpusFmtp(out, currentTier.opusMaxAverageBitrate)
        return out
    }

    fun applyOutboundAudio(pc: PeerConnection?, tier: Tier = currentTier) {
        if (pc == null) return
        val sender =
            pc.senders.firstOrNull { it.track()?.kind() == MediaStreamTrack.AUDIO_TRACK_KIND }
                ?: return
        applySenderTier(sender, tier)
    }

    private fun applySenderTier(sender: RtpSender, tier: Tier) {
        currentTier = tier
        val params = sender.parameters
        val encodings = params.encodings.toMutableList()
        if (encodings.isEmpty()) {
            encodings.add(RtpParameters.Encoding(null, true, null))
        }
        val enc = encodings[0]
        enc.active = true
        enc.maxBitrateBps = tier.maxBitrateBps
        enc.minBitrateBps = (tier.maxBitrateBps / 4).coerceAtLeast(12_000)
        params.encodings = encodings
        params.degradationPreference = RtpParameters.DegradationPreference.MAINTAIN_FRAMERATE
        if (!sender.setParameters(params)) {
            Log.w(TAG, "audio setParameters failed tier=$tier")
        } else {
            Log.d(TAG, "audio tier=$tier bps=${tier.maxBitrateBps}")
        }
    }

    data class LinkStats(
        val outboundBytes: Long = 0,
        val packetsLost: Long = 0,
        val jitterMs: Double = 0.0,
        val availableOutgoingBitrateBps: Long = 0,
    )

    fun parseStats(report: RTCStatsReport): LinkStats {
        var bytes = 0L
        var lost = 0L
        var jitter = 0.0
        var available = 0L
        for (stat in report.statsMap.values) {
            when (stat.type) {
                "outbound-rtp" -> {
                    if (stat.members["kind"]?.toString() == "audio") {
                        bytes = stat.members["bytesSent"]?.toString()?.toLongOrNull() ?: bytes
                    }
                }
                "inbound-rtp" -> {
                    if (stat.members["kind"]?.toString() == "audio") {
                        lost = stat.members["packetsLost"]?.toString()?.toLongOrNull() ?: lost
                        jitter =
                            stat.members["jitter"]?.toString()?.toDoubleOrNull()?.times(1000.0) ?: jitter
                    }
                }
                "candidate-pair" -> {
                    if (stat.members["nominated"]?.toString() == "true") {
                        val b =
                            stat.members["availableOutgoingBitrate"]?.toString()?.toDoubleOrNull() ?: 0.0
                        if (b > available) available = b.toLong()
                    }
                }
            }
        }
        return LinkStats(bytes, lost, jitter, available)
    }

    fun pickTier(
        stats: LinkStats,
        bytesPerSec: Long,
        connectionBars: Int,
        packetLossBurst: Boolean,
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
                packetLossBurst || stats.packetsLost > 8 -> Tier.MIN
                bps < 120_000 || connectionBars <= 1 -> Tier.MIN
                bps < 220_000 || connectionBars == 2 -> Tier.LOW
                bps < 450_000 || connectionBars == 3 -> Tier.NORMAL
                else -> Tier.HIGH
            }
        return stabilizeTier(current, candidate)
    }

    private fun stabilizeTier(current: Tier, candidate: Tier): Tier {
        if (candidate == current) return current
        val cur = current.ordinal
        val cand = candidate.ordinal
        return when {
            cand > cur + 1 -> Tier.entries[cur + 1]
            cand < cur - 1 -> Tier.entries[cur - 1]
            else -> candidate
        }
    }

    private fun patchOpusFmtp(sdp: String, maxAverageBitrate: Int): String {
        val opusPt = findOpusPayloadType(sdp) ?: return sdp
        val lines = sdp.replace("\r\n", "\n").split("\n").toMutableList()
        val required =
            linkedMapOf(
                "minptime" to "10",
                "useinbandfec" to "1",
                "stereo" to "0",
                "usedtx" to "0",
                "maxaveragebitrate" to maxAverageBitrate.toString(),
            )
        val fmtpIdx = lines.indexOfFirst { it.startsWith("a=fmtp:$opusPt ") }
        if (fmtpIdx >= 0) {
            val existing = lines[fmtpIdx].substringAfter("a=fmtp:$opusPt ").trim()
            val merged = mergeFmtpParams(existing, required)
            lines[fmtpIdx] = "a=fmtp:$opusPt $merged"
        } else {
            val rtpmapIdx = lines.indexOfFirst { it.startsWith("a=rtpmap:$opusPt ") }
            val insertAt = if (rtpmapIdx >= 0) rtpmapIdx + 1 else fmtpIdx.coerceAtLeast(0)
            val merged = required.entries.joinToString(";") { "${it.key}=${it.value}" }
            lines.add(insertAt, "a=fmtp:$opusPt $merged")
        }
        return lines.joinToString("\r\n") + "\r\n"
    }

    private fun mergeFmtpParams(existing: String, required: Map<String, String>): String {
        val map = linkedMapOf<String, String>()
        if (existing.isNotBlank()) {
            for (part in existing.split(";")) {
                val kv = part.trim().split("=", limit = 2)
                if (kv.size == 2) map[kv[0].trim()] = kv[1].trim()
            }
        }
        map.putAll(required)
        return map.entries.joinToString(";") { "${it.key}=${it.value}" }
    }

    private fun findOpusPayloadType(sdp: String): String? {
        for (line in sdp.split("\r\n", "\n")) {
            if (!line.startsWith("a=rtpmap:")) continue
            if (line.contains("opus/", ignoreCase = true)) {
                return line.substringAfter("a=rtpmap:").substringBefore(" ").trim()
            }
        }
        return null
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
            }
        }
        if (preferred.isEmpty()) return sdp
        val rest = payloads.filter { it !in preferred }
        lines[mIdx] = (header + preferred + rest).joinToString(" ")
        return lines.joinToString("\r\n") + "\r\n"
    }
}
