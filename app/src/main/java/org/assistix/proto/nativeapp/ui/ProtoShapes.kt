package org.assistix.proto.nativeapp.ui

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Shared corner radii — pill/circle where it should read fully round. */
object ProtoShapes {
    val field = RoundedCornerShape(24.dp)
    val card = RoundedCornerShape(20.dp)
    val bubble = RoundedCornerShape(24.dp)
    val media = RoundedCornerShape(20.dp)
    val pill = RoundedCornerShape(28.dp)
    val button = RoundedCornerShape(28.dp)
    val dialog = RoundedCornerShape(24.dp)
    val fab = CircleShape
    val avatar = CircleShape
}

/** First letter of the given name (display name), not username. */
fun protoAvatarInitial(displayName: String): Char {
    val trimmed = displayName.trim()
    if (trimmed.isEmpty()) return '?'
    return trimmed.first().uppercaseChar()
}
