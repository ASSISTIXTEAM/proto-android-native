package org.assistix.proto.nativeapp.data

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Build
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Decode media file to 16 kHz mono PCM for offline STT. */
object ProtoAudioDecoder {
    fun decodeTo16kMono(file: File): ShortArray? {
        if (!file.exists() || file.length() < 32L) return null
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            var track = -1
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    track = i
                    break
                }
            }
            if (track < 0) return null
            extractor.selectTrack(track)
            var inFormat = extractor.getTrackFormat(track)
            val mime = inFormat.getString(MediaFormat.KEY_MIME) ?: return null
            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(inFormat, null, null, 0)
            codec.start()
            val pcmChunks = ArrayList<Short>(16000 * 120)
            val bufferInfo = MediaCodec.BufferInfo()
            var inputDone = false
            var outputRate = inFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE, 44100).coerceIn(8000, 48000)
            while (true) {
                if (!inputDone) {
                    val inIx = codec.dequeueInputBuffer(10_000)
                    if (inIx >= 0) {
                        val inBuf = codec.getInputBuffer(inIx) ?: break
                        val sampleSize = extractor.readSampleData(inBuf, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inIx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIx, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIx = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                when {
                    outIx == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        if (inputDone) break
                    }
                    outIx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        inFormat = codec.outputFormat
                        outputRate =
                            inFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE, outputRate).coerceIn(8000, 48000)
                    }
                    outIx >= 0 -> {
                        val outBuf = codec.getOutputBuffer(outIx) ?: break
                        appendPcm(outBuf, bufferInfo.size, inFormat, pcmChunks)
                        codec.releaseOutputBuffer(outIx, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    }
                }
            }
            codec.stop()
            codec.release()
            if (pcmChunks.isEmpty()) return null
            val raw = ShortArray(pcmChunks.size)
            for (i in pcmChunks.indices) raw[i] = pcmChunks[i]
            return resampleTo16k(raw, outputRate)
        } catch (_: Exception) {
            return null
        } finally {
            try {
                extractor.release()
            } catch (_: Exception) {
            }
        }
    }

    fun decodeTo16kMonoFloat(file: File): FloatArray? {
        val pcm = decodeTo16kMono(file) ?: return null
        return FloatArray(pcm.size) { i -> pcm[i] / 32768.0f }
    }

    private fun appendPcm(
        outBuf: ByteBuffer,
        size: Int,
        format: MediaFormat,
        out: ArrayList<Short>,
    ) {
        outBuf.order(ByteOrder.LITTLE_ENDIAN)
        val encoding =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                format.getInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            } else {
                AudioFormat.ENCODING_PCM_16BIT
            }
        val limit = outBuf.position() + size.coerceAtMost(outBuf.remaining())
        outBuf.limit(limit)
        when (encoding) {
            AudioFormat.ENCODING_PCM_FLOAT -> {
                val fb = outBuf.asFloatBuffer()
                while (fb.hasRemaining()) {
                    val f = fb.get().coerceIn(-1f, 1f)
                    out.add((f * 32767f).toInt().coerceIn(-32768, 32767).toShort())
                }
            }
            else -> {
                val sb = outBuf.asShortBuffer()
                while (sb.hasRemaining()) out.add(sb.get())
            }
        }
    }

    private fun resampleTo16k(input: ShortArray, inRate: Int): ShortArray {
        if (inRate == 16000) return input
        val outLen = (input.size.toLong() * 16000 / inRate).toInt().coerceAtLeast(1)
        val out = ShortArray(outLen)
        val ratio = inRate.toDouble() / 16000.0
        for (i in 0 until outLen) {
            val src = (i * ratio).toInt().coerceIn(0, input.size - 1)
            out[i] = input[src]
        }
        return out
    }
}
