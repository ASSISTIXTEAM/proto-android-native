package org.assistix.proto.nativeapp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Forward
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageSwipeRow(
    onReply: () -> Unit,
    onForward: () -> Unit,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    if (!enabled) {
        content()
        return
    }
    val haptic = ProtoHaptics.rememberSender()
    var revealedTarget by remember { mutableStateOf<SwipeToDismissBoxValue?>(null) }
    val state =
        rememberSwipeToDismissBoxState(
            confirmValueChange = { value ->
                when (value) {
                    SwipeToDismissBoxValue.StartToEnd -> {
                        haptic(HapticKind.Action)
                        onReply()
                        false
                    }
                    SwipeToDismissBoxValue.EndToStart -> {
                        haptic(HapticKind.Action)
                        onForward()
                        false
                    }
                    else -> false
                }
            },
        )

    LaunchedEffect(state.targetValue) {
        val target = state.targetValue
        if (target != SwipeToDismissBoxValue.Settled && target != revealedTarget) {
            haptic(HapticKind.SwipeReveal)
            revealedTarget = target
        }
        if (target == SwipeToDismissBoxValue.Settled) {
            revealedTarget = null
        }
    }

    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            when (state.targetValue) {
                SwipeToDismissBoxValue.StartToEnd ->
                    SwipeHint(Alignment.CenterStart, ProtoOrange.copy(0.9f), Icons.AutoMirrored.Filled.Reply, UiStrings.reply)
                SwipeToDismissBoxValue.EndToStart ->
                    SwipeHint(Alignment.CenterEnd, MaterialTheme.colorScheme.secondary.copy(0.85f), Icons.Default.Forward, UiStrings.forward)
                else -> Box(Modifier.fillMaxSize())
            }
        },
        content = { content() },
    )
}

@Composable
private fun SwipeHint(
    align: Alignment,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = align) {
        androidx.compose.foundation.layout.Row(
            Modifier.padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = Color.White)
            Text(label, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
        }
    }
}
