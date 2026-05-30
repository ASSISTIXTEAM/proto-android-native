package org.assistix.proto.nativeapp.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private data class DustParticle(
    val ox: Float,
    val oy: Float,
    val angle: Float,
    val speed: Float,
    val size: Float,
    val phase: Float,
)

/**
 * Сообщение «рассыпается» и уносится ветром перед удалением из списка.
 */
@Composable
fun MessageDissolveContainer(
    dissolving: Boolean,
    reduceMotion: Boolean,
    onDissolved: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (!dissolving) {
        Box(modifier.fillMaxWidth()) { content() }
        return
    }

    if (reduceMotion) {
        LaunchedEffect(Unit) { onDissolved() }
        return
    }

    val progress = remember { Animatable(0f) }
    val particles =
        remember {
            List(28) {
                DustParticle(
                    ox = Random.nextFloat() * 2f - 1f,
                    oy = Random.nextFloat() * 2f - 1f,
                    angle = Random.nextFloat() * 6.28f,
                    speed = 0.55f + Random.nextFloat() * 0.9f,
                    size = 2.5f + Random.nextFloat() * 5f,
                    phase = Random.nextFloat(),
                )
            }
        }
    val density = LocalDensity.current
    val windPx = with(density) { 36.dp.toPx() }
    val liftPx = with(density) { 28.dp.toPx() }

    LaunchedEffect(dissolving) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis = 520, easing = FastOutSlowInEasing))
        onDissolved()
    }

    val p = progress.value
    val fade = 1f - p
    val windEase = FastOutSlowInEasing.transform(p)

    Box(
        modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = fade.coerceIn(0f, 1f)
                scaleX = 1f - p * 0.22f
                scaleY = 1f - p * 0.18f
                translationX = windEase * windPx
                translationY = -windEase * liftPx * 0.65f
            },
    ) {
        Box(Modifier.fillMaxWidth()) { content() }
        Canvas(Modifier.matchParentSize()) {
            val cx = size.width * 0.5f
            val cy = size.height * 0.5f
            val spread = size.width.coerceAtLeast(size.height) * (0.35f + p * 0.85f)
            particles.forEach { part ->
                val t = (p * part.speed + part.phase * 0.12f).coerceIn(0f, 1f)
                val px = cx + part.ox * spread * 0.35f + cos(part.angle) * spread * t + windEase * windPx * 0.35f
                val py = cy + part.oy * spread * 0.25f + sin(part.angle) * spread * t * 0.6f - liftPx * t
                val alpha = ((1f - t) * fade * 0.85f).coerceIn(0f, 1f)
                if (alpha > 0.02f) {
                    drawCircle(
                        color = ProtoOrange.copy(alpha = alpha * 0.55f),
                        radius = part.size * (1f + t * 0.4f),
                        center = Offset(px, py),
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = alpha * 0.35f),
                        radius = part.size * 0.55f,
                        center = Offset(px + 1f, py - 1f),
                    )
                }
            }
        }
    }
}
