package org.assistix.proto.nativeapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PushPin
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
fun ChatListSwipeRow(
    pinned: Boolean,
    muted: Boolean,
    unreadCount: Int = 0,
    onPin: () -> Unit,
    onMute: () -> Unit,
    onMarkRead: (() -> Unit)? = null,
    onArchive: (() -> Unit)? = null,
    archiveSwipeLabel: String = UiStrings.swipeArchive,
    content: @Composable () -> Unit,
) {
    val haptic = ProtoHaptics.rememberSender()
    var revealedTarget by remember { mutableStateOf<SwipeToDismissBoxValue?>(null) }
    val state =
        rememberSwipeToDismissBoxState(
            confirmValueChange = { value ->
                when (value) {
                    SwipeToDismissBoxValue.StartToEnd -> {
                        haptic(HapticKind.Action)
                        onPin()
                        false
                    }
                    SwipeToDismissBoxValue.EndToStart -> {
                        haptic(HapticKind.Action)
                        when {
                            unreadCount > 0 && onMarkRead != null -> onMarkRead()
                            onArchive != null -> onArchive()
                            else -> onMute()
                        }
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
                    SwipeActionBackground(
                        align = Alignment.CenterStart,
                        color = ProtoOrange.copy(0.85f),
                        icon = Icons.Default.PushPin,
                        label = if (pinned) UiStrings.unpinChat else UiStrings.pinChat,
                    )
                SwipeToDismissBoxValue.EndToStart -> {
                    val read = unreadCount > 0 && onMarkRead != null
                    val archive = !read && onArchive != null
                    SwipeActionBackground(
                        align = Alignment.CenterEnd,
                        color =
                            when {
                                read -> MaterialTheme.colorScheme.primary.copy(0.82f)
                                archive -> MaterialTheme.colorScheme.tertiary.copy(0.82f)
                                else -> MaterialTheme.colorScheme.secondary.copy(0.75f)
                            },
                        icon =
                            when {
                                read -> Icons.Default.DoneAll
                                archive -> Icons.Default.Archive
                                muted -> Icons.Default.Notifications
                                else -> Icons.Default.NotificationsOff
                            },
                        label =
                            when {
                                read -> UiStrings.swipeMarkRead
                                archive -> archiveSwipeLabel
                                muted -> UiStrings.unmuteChat
                                else -> UiStrings.muteChat
                            },
                    )
                }
                else -> Box(Modifier.fillMaxSize())
            }
        },
        content = { content() },
    )
}

@Composable
private fun SwipeActionBackground(
    align: Alignment,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(color, ProtoShapes.card)
            .padding(horizontal = 20.dp),
        contentAlignment = align,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Color.White)
            Text(label, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
        }
    }
}
