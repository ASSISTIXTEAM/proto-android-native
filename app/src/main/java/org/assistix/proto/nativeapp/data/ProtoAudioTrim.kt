package org.assistix.proto.nativeapp.data

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

object ProtoAudioTrim {
    /** Returns trimmed file, or [source] if trim not needed / failed. */
    fun trimIfNeeded(source: File, startMs: Long, endMs: Long?): File {
        val durationMs = durationMs(source)
        val end = endMs ?: durationMs
        if (startMs <= 0L && end >= durationMs - 50L) return source
        val out = File(source.parentFile, "proto_trim_${System.currentTimeMillis()}.m4a")
        return if (trimM4a(source, startMs, end, out)) out else source
    }

    private fun durationMs(source: File): Long =
        runCatching {
            val p = android.media.MediaPlayer()
            p.setDataSource(source.absolutePath)
            p.prepare()
            val d = p.duration.toLong()
            p.release()
            d.coerceAtLeast(1L)
        }.getOrDefault(1000L)

    private fun trimM4a(source: File, startMs: Long, endMs: Long, output: File): Boolean {
        if (endMs <= startMs) return false
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        return try {
            extractor.setDataSource(source.absolutePath)
            var trackIndex = -1
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    trackIndex = i
                    break
                }
            }
            if (trackIndex < 0) return false
            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)
            muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val outTrack = muxer.addTrack(format)
            muxer.start()
            val startUs = startMs * 1000L
            val endUs = if (endMs == Long.MAX_VALUE) Long.MAX_VALUE else endMs * 1000L
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            val buffer = ByteBuffer.allocate(256 * 1024)
            val info = MediaCodec.BufferInfo()
            while (true) {
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break
                val sampleTime = extractor.sampleTime
                if (sampleTime > endUs) break
                if (sampleTime < startUs) {
                    extractor.advance()
                    continue
                }
                info.offset = 0
                info.size = sampleSize
                info.presentationTimeUs = sampleTime - startUs
                info.flags = extractor.sampleFlags
                muxer.writeSampleData(outTrack, buffer, info)
                extractor.advance()
            }
            true
        } catch (_: Exception) {
            output.delete()
            false
        } finally {
            runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            runCatching { extractor.release() }
        }
    }
}
