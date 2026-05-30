package org.assistix.proto.nativeapp.ui

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Composable
fun protoTypography(): Typography {
    val savona = rememberSavonaFamily()
    return Typography(
        displayLarge = Typography().displayLarge.copy(fontFamily = savona),
        headlineLarge = Typography().headlineLarge.copy(fontFamily = savona, fontWeight = FontWeight.Bold),
        titleLarge = Typography().titleLarge.copy(fontFamily = savona, fontWeight = FontWeight.SemiBold),
        titleMedium = Typography().titleMedium.copy(fontFamily = savona, fontWeight = FontWeight.Medium),
        bodyLarge = Typography().bodyLarge.copy(fontFamily = savona, fontSize = 16.sp),
        bodyMedium = Typography().bodyMedium.copy(fontFamily = savona, fontSize = 14.sp),
        labelLarge = Typography().labelLarge.copy(fontFamily = savona),
        labelMedium = Typography().labelMedium.copy(fontFamily = savona, fontStyle = FontStyle.Normal),
    )
}
