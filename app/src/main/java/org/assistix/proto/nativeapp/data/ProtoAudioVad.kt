package org.assistix.proto.nativeapp.data

import kotlin.math.abs
import kotlin.math.sqrt

/** Simple energy gate — trims leading/trailing silence before Whisper. */
object ProtoAudioVad {
    private const val FRAME = 480 // 30 ms @ 16 kHz
    private const val MIN_AMP = 0.008f

    fun trimSilence(samples: FloatArray): FloatArray {
        if (samples.size < FRAME * 4) return samples
        var start = 0
        var end = samples.size
        while (start + FRAME < samples.size && !frameActive(samples, start)) start += FRAME
        while (end - FRAME > start && !frameActive(samples, end - FRAME)) end -= FRAME
        if (end <= start + FRAME) return samples
        return samples.copyOfRange(start, end)
    }

    private fun frameActive(samples: FloatArray, offset: Int): Boolean {
        var sum = 0.0
        val n = minOf(FRAME, samples.size - offset)
        for (i in 0 until n) {
            val v = samples[offset + i].toDouble()
            sum += v * v
        }
        return sqrt(sum / n) > MIN_AMP
    }
}
