package org.assistix.proto.nativeapp.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import org.assistix.proto.nativeapp.data.ProtoThemeMode

/** Brand orange — avatars, FAB, accents. */
val ProtoOrange = Color(0xFFE85D04)

private val ProtoAccent = ProtoOrange
private val ProtoAccentSoft = Color(0xFFFF8C38)

private val DarkColors =
    darkColorScheme(
        primary = ProtoAccent,
        onPrimary = Color.White,
        secondary = ProtoAccentSoft,
        background = Color(0xFF121212),
        surface = Color(0xFF1B1B1D),
        surfaceVariant = Color(0xFF2C2C2E),
        onBackground = Color(0xFFF5F5F5),
        onSurface = Color(0xFFF5F5F5),
        onSurfaceVariant = Color(0xFFAEAEB2),
        outline = Color(0xFF3A3A3C),
    )

private val LightColors =
    lightColorScheme(
        primary = ProtoAccent,
        onPrimary = Color.White,
        secondary = ProtoAccentSoft,
        background = Color(0xFFFAF7F5),
        surface = Color.White,
        surfaceVariant = Color(0xFFF0EBE8),
        onBackground = Color(0xFF1A120E),
        onSurface = Color(0xFF1A120E),
        onSurfaceVariant = Color(0xFF6B5E56),
        outline = Color(0xFFE0D6CF),
    )

@Composable
fun ProtoTheme(mode: ProtoThemeMode, content: @Composable () -> Unit) {
    val dark =
        when (mode) {
            ProtoThemeMode.DARK -> true
            ProtoThemeMode.LIGHT -> false
            ProtoThemeMode.SYSTEM -> isSystemInDarkTheme()
        }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = protoTypography(),
        content = content,
    )
}
