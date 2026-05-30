package org.assistix.proto.nativeapp.ui

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset

object ProtoMotion {
    fun fade(reduceMotion: Boolean, durationMs: Int = 320): FiniteAnimationSpec<Float> =
        if (reduceMotion) {
            tween(durationMillis = 1)
        } else {
            tween(durationMillis = durationMs)
        }

    fun slide(reduceMotion: Boolean, durationMs: Int = 420): FiniteAnimationSpec<IntOffset> =
        if (reduceMotion) {
            tween(durationMillis = 1)
        } else {
            tween(durationMillis = durationMs)
        }

    fun gentleColorSpring(reduceMotion: Boolean): AnimationSpec<Color> =
        if (reduceMotion) {
            tween(1)
        } else {
            spring(
                dampingRatio = 0.86f,
                stiffness = Spring.StiffnessLow,
            )
        }

    fun gentleSpring(reduceMotion: Boolean): FiniteAnimationSpec<Float> =
        if (reduceMotion) {
            tween(1)
        } else {
            spring(
                dampingRatio = 0.86f,
                stiffness = Spring.StiffnessLow,
            )
        }

    fun softSpring(reduceMotion: Boolean): FiniteAnimationSpec<Float> =
        if (reduceMotion) {
            tween(1)
        } else {
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessVeryLow,
            )
        }

    fun staggerDelay(index: Int, reduceMotion: Boolean): Int =
        if (reduceMotion) 0 else (index * 55).coerceAtMost(320)

    fun slideOffset(index: Int): Int = 28 + index * 4
}
