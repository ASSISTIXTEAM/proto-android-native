package org.assistix.proto.nativeapp.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme

object ChatAccent {
    @Composable
    fun backgroundBrush(accentId: Int): Brush? =
        when (accentId) {
            1 ->
                Brush.verticalGradient(
                    listOf(
                        ProtoOrange.copy(0.08f),
                        Color.Transparent,
                    ),
                )
            2 ->
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.secondary.copy(0.1f),
                        Color.Transparent,
                    ),
                )
            3 ->
                Brush.radialGradient(
                    listOf(
                        ProtoOrange.copy(0.12f),
                        Color.Transparent,
                    ),
                )
            else -> null
        }
}
