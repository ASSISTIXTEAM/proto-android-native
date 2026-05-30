package org.assistix.proto.nativeapp.ui



import androidx.compose.animation.animateColorAsState

import androidx.compose.animation.core.RepeatMode

import androidx.compose.animation.core.animateFloat

import androidx.compose.animation.core.infiniteRepeatable

import androidx.compose.animation.core.rememberInfiniteTransition

import androidx.compose.animation.core.tween

import androidx.compose.foundation.Canvas

import androidx.compose.foundation.layout.Box

import androidx.compose.foundation.layout.Column

import androidx.compose.foundation.layout.Spacer

import androidx.compose.foundation.layout.fillMaxSize

import androidx.compose.foundation.layout.fillMaxWidth

import androidx.compose.foundation.layout.navigationBarsPadding

import androidx.compose.foundation.layout.padding

import androidx.compose.foundation.layout.size

import androidx.compose.foundation.layout.statusBarsPadding

import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.filled.Close

import androidx.compose.material.icons.filled.FlashOff

import androidx.compose.material.icons.filled.FlashOn

import androidx.compose.material3.Icon

import androidx.compose.material3.IconButton

import androidx.compose.material3.MaterialTheme

import androidx.compose.material3.Text

import androidx.compose.runtime.Composable

import androidx.compose.runtime.getValue

import androidx.compose.ui.Alignment

import androidx.compose.ui.Modifier

import androidx.compose.ui.geometry.Offset

import androidx.compose.ui.graphics.Color

import androidx.compose.ui.graphics.Path

import androidx.compose.ui.graphics.StrokeCap

import androidx.compose.ui.graphics.StrokeJoin

import androidx.compose.ui.graphics.drawscope.DrawScope

import androidx.compose.ui.graphics.drawscope.Stroke

import androidx.compose.ui.graphics.drawscope.scale

import androidx.compose.ui.platform.LocalDensity

import androidx.compose.ui.text.style.TextAlign

import androidx.compose.ui.unit.dp



private val QrSuccessGreen = Color(0xFF4CAF50)



@Composable

fun ProtoQrScannerLayout(

    onClose: () -> Unit,

    instruction: String,

    detected: Boolean,

    torchEnabled: Boolean,

    onTorchToggle: () -> Unit,

    modifier: Modifier = Modifier,

) {

    val pulseTransition = rememberInfiniteTransition(label = "qrPulse")

    val pulseScale by pulseTransition.animateFloat(

        initialValue = 0.97f,

        targetValue = 1.03f,

        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),

        label = "qrPulseScale",

    )

    val cornerColor by animateColorAsState(

        targetValue = when {

            detected -> QrSuccessGreen

            else -> Color.White.copy(alpha = 0.95f)

        },

        animationSpec = tween(180),

        label = "qrCorner",

    )



    Box(modifier.fillMaxSize()) {

        Column(

            Modifier

                .fillMaxSize()

                .statusBarsPadding()

                .navigationBarsPadding(),

        ) {

            Box(

                Modifier

                    .fillMaxWidth()

                    .padding(horizontal = 4.dp, vertical = 8.dp),

            ) {

                IconButton(onClick = onClose, modifier = Modifier.align(Alignment.CenterStart)) {

                    Icon(Icons.Default.Close, contentDescription = UiStrings.close, tint = Color.White)

                }

                Text(

                    UiStrings.scanQrTitle,

                    modifier =

                        Modifier

                            .align(Alignment.Center)

                            .fillMaxWidth()

                            .padding(horizontal = 52.dp),

                    style = MaterialTheme.typography.titleMedium,

                    color = Color.White,

                    textAlign = TextAlign.Center,

                )

            }



            Spacer(Modifier.weight(0.38f))



            Box(

                Modifier

                    .fillMaxWidth()

                    .padding(horizontal = 52.dp),

                contentAlignment = Alignment.Center,

            ) {

                val frameSize = 256.dp

                val strokeW = with(LocalDensity.current) { 9.dp.toPx() }

                Box(Modifier.size(frameSize)) {

                    Canvas(Modifier.fillMaxSize()) {

                        val arm = size.minDimension * 0.20f

                        val bend = size.minDimension * 0.11f

                        val stroke =

                            Stroke(

                                width = strokeW,

                                cap = StrokeCap.Round,

                                join = StrokeJoin.Round,

                            )

                        val w = size.width

                        val h = size.height

                        val frameScale = if (detected) 1f else pulseScale
                        scale(frameScale, pivot = Offset(w / 2f, h / 2f)) {

                            drawRoundedQrCorner(0f, 0f, hx = 1f, hy = 1f, arm, bend, cornerColor, stroke)

                            drawRoundedQrCorner(w, 0f, hx = -1f, hy = 1f, arm, bend, cornerColor, stroke)

                            drawRoundedQrCorner(0f, h, hx = 1f, hy = -1f, arm, bend, cornerColor, stroke)

                            drawRoundedQrCorner(w, h, hx = -1f, hy = -1f, arm, bend, cornerColor, stroke)

                        }

                    }

                }

            }



            Text(

                instruction,

                modifier =

                    Modifier

                        .fillMaxWidth()

                        .padding(horizontal = 36.dp, vertical = 28.dp),

                style = MaterialTheme.typography.bodyLarge,

                color = if (detected) QrSuccessGreen else Color.White.copy(0.92f),

                textAlign = TextAlign.Center,

                lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.25f,

            )



            Spacer(Modifier.weight(0.62f))

        }



        IconButton(

            onClick = onTorchToggle,

            modifier =

                Modifier

                    .align(Alignment.BottomCenter)

                    .navigationBarsPadding()

                    .padding(bottom = 28.dp)

                    .size(52.dp),

        ) {

            Icon(

                if (torchEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,

                contentDescription = UiStrings.scanQrFlashlight,

                tint = Color.White,

                modifier = Modifier.size(28.dp),

            )

        }

    }

}



private fun DrawScope.drawRoundedQrCorner(

    ox: Float,

    oy: Float,

    hx: Float,

    hy: Float,

    arm: Float,

    bend: Float,

    color: Color,

    stroke: Stroke,

) {

    val path =

        Path().apply {

            moveTo(ox + hx * arm, oy)

            lineTo(ox + hx * bend, oy)

            quadraticTo(ox, oy, ox, oy + hy * bend)

            lineTo(ox, oy + hy * arm)

        }

    drawPath(path, color, style = stroke)

}


