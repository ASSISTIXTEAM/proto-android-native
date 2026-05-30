package org.assistix.proto.nativeapp.data

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Amplitude samples (0.12…1) for static voice waveforms. */
object ProtoAudioWaveform {
    private const val DEFAULT_BARS = 56
    private const val DISK_VERSION = 1
    private val memoryCache = ConcurrentHashMap<String, FloatArray>()

    suspend fun load(file: File, bars: Int = DEFAULT_BARS, cacheDir: File? = null): FloatArray =
        withContext(Dispatchers.IO) {
            if (!file.exists() || file.length() <= 0L) return@withContext placeholder(bars)
            val key = "${file.absolutePath}|${file.length()}|${file.lastModified()}|$bars"
            memoryCache[key]?.let { return@withContext it }
            cacheDir?.let { dir ->
                readDisk(dir, file, bars)?.let { cached ->
                    memoryCache[key] = cached
                    return@withContext cached
                }
            }
            val data = runCatching { decode(file, bars) }.getOrNull() ?: placeholder(bars)
            memoryCache[key] = data
            cacheDir?.let { writeDisk(it, file, bars, data) }
            trimMemoryCache()
            data
        }

    suspend fun load(context: Context, file: File, bars: Int = DEFAULT_BARS): FloatArray =
        load(file, bars, File(context.cacheDir, "proto_waveforms"))

    fun invalidate(file: File, cacheDir: File? = null) {
        val prefix = "${file.absolutePath}|"
        memoryCache.keys.filter { it.startsWith(prefix) }.forEach { memoryCache.remove(it) }
        cacheDir?.let { dir ->
            val id = diskId(file, DEFAULT_BARS)
            File(dir, "$id.wfm").delete()
        }
    }

    private fun trimMemoryCache() {
        if (memoryCache.size > 160) {
            memoryCache.keys.take(40).forEach { memoryCache.remove(it) }
        }
    }

    private fun diskId(file: File, bars: Int): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(file.absolutePath.toByteArray())
        digest.update(file.length().toString().toByteArray())
        digest.update(file.lastModified().toString().toByteArray())
        digest.update(bars.toString().toByteArray())
        return digest.digest().take(16).joinToString("") { "%02x".format(it) }
    }

    private fun readDisk(dir: File, file: File, bars: Int): FloatArray? {
        if (!dir.exists()) dir.mkdirs()
        val f = File(dir, "${diskId(file, bars)}.wfm")
        if (!f.isFile) return null
        return runCatching {
            DataInputStream(f.inputStream()).use { input ->
                if (input.readInt() != DISK_VERSION) return null
                val n = input.readInt()
                if (n != bars) return null
                FloatArray(n) { input.readFloat() }
            }
        }.getOrNull()
    }

    private fun writeDisk(dir: File, file: File, bars: Int, data: FloatArray) {
        if (!dir.exists()) dir.mkdirs()
        val f = File(dir, "${diskId(file, bars)}.wfm")
        runCatching {
            DataOutputStream(f.outputStream()).use { out ->
                out.writeInt(DISK_VERSION)
                out.writeInt(data.size)
                data.forEach { out.writeFloat(it) }
            }
        }
    }

    private fun decode(file: File, bars: Int): FloatArray? {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            var track = -1
            for (i in 0 until extractor.trackCount) {
                val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("audio/")) {
                    track = i
                    break
                }
            }
            if (track < 0) return null
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()
            val pcm = ArrayList<Short>(4096)
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            while (true) {
                if (!inputDone) {
                    val inIx = codec.dequeueInputBuffer(8_000)
                    if (inIx >= 0) {
                        val buf = codec.getInputBuffer(inIx) ?: break
                        val size = extractor.readSampleData(buf, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inIx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIx, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                when (val outIx = codec.dequeueOutputBuffer(info, 8_000)) {
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Unit
                    in 0..Int.MAX_VALUE -> {
                        if (info.size > 0) {
                            val out = codec.getOutputBuffer(outIx) ?: break
                            readPcm16Le(out, info.offset, info.size, pcm)
                        }
                        codec.releaseOutputBuffer(outIx, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            codec.stop()
                            codec.release()
                            return bucketPeaks(pcm, bars)
                        }
                    }
                    else -> break
                }
            }
            runCatching {
                codec.stop()
                codec.release()
            }
            return if (pcm.isEmpty()) null else bucketPeaks(pcm, bars)
        } finally {
            runCatching { extractor.release() }
        }
    }

    private fun readPcm16Le(buffer: ByteBuffer, offset: Int, size: Int, out: MutableList<Short>) {
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        buffer.position(offset)
        buffer.limit(offset + size)
        while (buffer.remaining() >= 2) {
            out.add(buffer.short)
        }
    }

    private fun bucketPeaks(pcm: List<Short>, bars: Int): FloatArray {
        if (pcm.isEmpty()) return placeholder(bars)
        val result = FloatArray(bars)
        val per = (pcm.size / bars).coerceAtLeast(1)
        for (b in 0 until bars) {
            val from = b * per
            val to = minOf(from + per, pcm.size)
            var peak = 0f
            for (i in from until to) {
                peak = maxOf(peak, abs(pcm[i].toInt()) / 32768f)
            }
            result[b] = peak.coerceIn(0.1f, 1f)
        }
        return result
    }

    fun placeholder(bars: Int = DEFAULT_BARS): FloatArray =
        FloatArray(bars) { i ->
            val t = i.toFloat() / (bars - 1).coerceAtLeast(1)
            val wave = abs(kotlin.math.sin(t * Math.PI * 5).toFloat())
            (0.18f + wave * 0.72f)
        }

    fun normalizeRecorderLevel(maxAmplitude: Int): Float {
        if (maxAmplitude <= 0) return 0.12f
        val linear = (maxAmplitude / 32767f).coerceIn(0f, 1f)
        return (0.12f + sqrt(linear) * 0.88f).coerceIn(0.12f, 1f)
    }
}
