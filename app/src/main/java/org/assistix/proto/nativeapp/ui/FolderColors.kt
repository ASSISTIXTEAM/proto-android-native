package org.assistix.proto.nativeapp.ui

import androidx.compose.ui.graphics.Color

/** Palette for custom chat folder chips (id 0 = default orange styling). */
object FolderColors {
    val palette: List<Color> =
        listOf(
            Color(0xFFFF6B1A), // 1 orange
            Color(0xFF60A5FA), // 2 blue
            Color(0xFF34D399), // 3 green
            Color(0xFFA78BFA), // 4 violet
            Color(0xFFF472B6), // 5 pink
            Color(0xFFFBBF24), // 6 amber
            Color(0xFF2DD4BF), // 7 teal
            Color(0xFFF87171), // 8 red
            Color(0xFF94A3B8), // 9 slate
        )

    fun colorFor(colorId: Int): Color? {
        if (colorId <= 0) return null
        return palette.getOrNull((colorId - 1).coerceIn(0, palette.lastIndex))
    }

    val count: Int get() = palette.size
}
