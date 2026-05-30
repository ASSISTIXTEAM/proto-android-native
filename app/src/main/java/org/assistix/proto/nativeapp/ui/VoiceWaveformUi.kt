package org.assistix.proto.nativeapp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

/** Static waveform; [progress] 0…1 paints bars left→right. Optional [speedLabel] badge. */
@Composable
fun VoiceStaticWaveform(
    samples: FloatArray,
    progress: Float,
    activeColor: Color,
    inactiveColor: Color,
    modifier: Modifier = Modifier,
    onSeek: ((Float) -> Unit)? = null,
    speedLabel: String? = null,
    onSpeedTap: (() -> Unit)? = null,
) {
    if (samples.isEmpty()) return
    val prog = progress.coerceIn(0f, 1f)
    Box(modifier.fillMaxWidth()) {
        Canvas(
            Modifier
                .height(36.dp)
                .fillMaxWidth()
                .then(
                    if (onSeek != null) {
                        Modifier.pointerInput(samples.size, onSeek) {
                            detectTapGestures { offset ->
                                onSeek((offset.x / size.width).coerceIn(0f, 1f))
                            }
                        }
                    } else {
                        Modifier
                    },
                ),
        ) {
            drawWaveBars(samples, prog, activeColor, inactiveColor, size.width, size.height)
        }
        speedLabel?.let { label ->
            Surface(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .then(
                        if (onSpeedTap != null) {
                            Modifier.clickable(onClick = onSpeedTap)
                        } else {
                            Modifier
                        },
                    ),
                shape = ProtoShapes.field,
                color = activeColor.copy(0.92f),
            ) {
                Text(
                    label,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                )
            }
        }
    }
}

/** Waveform with draggable trim range + playback progress inside selection. */
@Composable
fun VoiceTrimWaveform(
    samples: FloatArray,
    playProgress: Float,
    trimStart: Float,
    trimEnd: Float,
    onTrimChange: (Float, Float) -> Unit,
    activeColor: Color = ProtoOrange,
    inactiveColor: Color = MaterialTheme.colorScheme.onSurface.copy(0.28f),
    modifier: Modifier = Modifier,
) {
    if (samples.isEmpty()) return
    val start = trimStart.coerceIn(0f, 0.98f)
    val end = trimEnd.coerceIn(start + 0.02f, 1f)
    val prog = playProgress.coerceIn(start, end)
    val selProg = start + if (end > start) (prog - start) / (end - start) * (end - start) else 0f

    Canvas(
        modifier
            .height(44.dp)
            .fillMaxWidth()
            .pointerInput(samples.size, trimStart, trimEnd) {
                var dragStart = false
                detectDragGestures(
                    onDragStart = { offset ->
                        val f = (offset.x / size.width).coerceIn(0f, 1f)
                        dragStart = kotlin.math.abs(f - trimStart) < kotlin.math.abs(f - trimEnd)
                    },
                    onDrag = { change, _ ->
                        val f = (change.position.x / size.width).coerceIn(0f, 1f)
                        if (dragStart) {
                            onTrimChange(f.coerceIn(0f, trimEnd - 0.02f), trimEnd)
                        } else {
                            onTrimChange(trimStart, f.coerceIn(trimStart + 0.02f, 1f))
                        }
                    },
                )
            },
    ) {
        val w = size.width
        val h = size.height
        drawRect(inactiveColor.copy(0.12f), topLeft = Offset.Zero, size = Size(w * start, h))
        drawRect(inactiveColor.copy(0.12f), topLeft = Offset(w * end, 0f), size = Size(w * (1f - end), h))
        drawWaveBars(samples, selProg.coerceIn(0f, 1f), activeColor, inactiveColor, w, h)
        val handleW = 3.dp.toPx()
        drawRect(activeColor, topLeft = Offset(w * start - handleW / 2f, 0f), size = Size(handleW, h))
        drawRect(activeColor, topLeft = Offset(w * end - handleW / 2f, 0f), size = Size(handleW, h))
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWaveBars(
    samples: FloatArray,
    prog: Float,
    activeColor: Color,
    inactiveColor: Color,
    width: Float,
    height: Float,
) {
    val n = samples.size
    val slot = width / n
    val gap = slot * 0.18f
    val barW = (slot - gap).coerceAtLeast(1.5f)
    for (i in 0 until n) {
        val amp = samples[i].coerceIn(0.1f, 1f)
        val barH = height * amp
        val x = i * slot + gap / 2f
        val y = (height - barH) / 2f
        val center = (i + 0.5f) / n
        val color = if (center <= prog) activeColor else inactiveColor
        drawRoundRect(
            color = color,
            topLeft = Offset(x, y),
            size = Size(barW, barH),
            cornerRadius = CornerRadius(barW / 2f, barW / 2f),
        )
    }
}

/** Live levels while recording — newest bars on the right. */
@Composable
fun VoiceLiveWaveform(
    levels: List<Float>,
    tint: Color,
    modifier: Modifier = Modifier,
    barCount: Int = 44,
) {
    Canvas(modifier.height(40.dp).fillMaxWidth()) {
        val n = barCount
        val slot = size.width / n
        val gap = slot * 0.14f
        val barW = (slot - gap).coerceAtLeast(1.5f)
        val tail = if (levels.size <= n) levels else levels.takeLast(n)
        val pad = n - tail.size
        for (i in 0 until n) {
            val amp =
                if (i < pad) {
                    0.14f
                } else {
                    tail[i - pad].coerceIn(0.12f, 1f)
                }
            val barH = size.height * amp
            val x = i * slot + gap / 2f
            val y = (size.height - barH) / 2f
            val alpha = if (i < pad) 0.35f else 0.55f + amp * 0.45f
            drawRoundRect(
                color = tint.copy(alpha = alpha),
                topLeft = Offset(x, y),
                size = Size(barW, barH),
                cornerRadius = CornerRadius(barW / 2f, barW / 2f),
            )
        }
    }
}
