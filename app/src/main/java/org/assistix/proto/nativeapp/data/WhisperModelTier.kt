package org.assistix.proto.nativeapp.data

/** Ordered on-device Whisper models — lightest (0) to heaviest. */
enum class WhisperModelTier(
    val level: Int,
    val id: String,
    val fileName: String,
    val minBytes: Long,
    val approxDownloadBytes: Long,
    val maxThreads: Int,
) {
    TINY_FAST(0, "tiny_fast", "ggml-tiny-q5_1.bin", 19_000_000L, 32_000_000L, 2),
    TINY_PLUS(1, "tiny_plus", "ggml-tiny-q8_0.bin", 22_000_000L, 34_000_000L, 2),
    BASE(2, "base", "ggml-base-q5_1.bin", 52_000_000L, 60_000_000L, 2),
    SMALL_Q5(3, "small_q5", "ggml-small-q5_1.bin", 175_000_000L, 205_000_000L, 3),
    SMALL(4, "small", "ggml-small.bin", 400_000_000L, 488_000_000L, 4),
    MEDIUM_Q5(5, "medium_q5", "ggml-medium-q5_0.bin", 480_000_000L, 525_000_000L, 4),
    ;

    companion object {
        val LADDER: List<WhisperModelTier> = entries.sortedBy { it.level }
        const val MIN_LEVEL = 0
        val MAX_LEVEL: Int = LADDER.maxOf { it.level }
        val DEFAULT: WhisperModelTier = TINY_FAST

        fun fromLevel(level: Int): WhisperModelTier =
            LADDER.firstOrNull { it.level == level.coerceIn(MIN_LEVEL, MAX_LEVEL) } ?: DEFAULT

        fun fromId(id: String?): WhisperModelTier {
            when (id?.lowercase()) {
                "lite" -> return TINY_FAST
                "medium" -> return SMALL_Q5
                "powerful" -> return SMALL
            }
            return entries.firstOrNull { it.id == id?.lowercase() } ?: DEFAULT
        }
    }
}
