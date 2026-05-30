package org.assistix.proto.nativeapp.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

@Composable
fun rememberSavonaFamily(): FontFamily {
    val ctx = LocalContext.current
    return remember(ctx) {
        fun f(file: String, weight: FontWeight, style: FontStyle = FontStyle.Normal): Font? =
            try {
                ctx.assets.open("fonts/savona/$file").close()
                Font("fonts/savona/$file", ctx.assets, weight, style)
            } catch (_: Exception) {
                null
            }
        val fonts =
            listOfNotNull(
                f("Savona-Regular.ttf", FontWeight.Normal),
                f("Savona-Medium.ttf", FontWeight.Medium),
                f("Savona-Semibold.ttf", FontWeight.SemiBold),
                f("Savona-Bold.ttf", FontWeight.Bold),
                f("Savona-Light.ttf", FontWeight.Light),
            )
        if (fonts.isNotEmpty()) FontFamily(*fonts.toTypedArray()) else FontFamily.SansSerif
    }
}
