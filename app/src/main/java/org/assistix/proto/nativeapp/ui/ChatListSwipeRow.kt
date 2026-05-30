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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
/** Pin / archive need a deep swipe so actions are not triggered by accident. */
private const val PIN_SWIPE_FRACTION = 0.84f
private const val ARCHIVE_SWIPE_FRACTION = 0.88f
private const val MARK_READ_SWIPE_FRACTION = 0.58f

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
    var swipeProgress by remember { mutableFloatStateOf(0f) }
    val state =
        rememberSwipeToDismissBoxState(
            confirmValueChange = { target ->
                val progress = swipeProgress
                when (target) {
                    SwipeToDismissBoxValue.StartToEnd -> {
                        if (progress < PIN_SWIPE_FRACTION) {
                            false
                        } else {
                            haptic(HapticKind.Action)
                            onPin()
                            false
                        }
                    }
                    SwipeToDismissBoxValue.EndToStart -> {
                        val read = unreadCount > 0 && onMarkRead != null
                        val need = when {
                            read -> MARK_READ_SWIPE_FRACTION
                            onArchive != null -> ARCHIVE_SWIPE_FRACTION
                            else -> PIN_SWIPE_FRACTION
                        }
                        if (progress < need) {
                            false
                        } else {
                            haptic(HapticKind.Action)
                            when {
                                read -> onMarkRead?.invoke()
                                onArchive != null -> onArchive()
                                else -> onMute()
                            }
                            false
                        }
                    }
                    else -> false
                }
            },
            positionalThreshold = { distance -> distance * 0.42f },
        )

    LaunchedEffect(state.progress) {
        swipeProgress = state.progress
    }

    LaunchedEffect(state.targetValue, state.progress) {
        val target = state.targetValue
        if (target != SwipeToDismissBoxValue.Settled && target != revealedTarget) {
            val minReveal =
                when (target) {
                    SwipeToDismissBoxValue.StartToEnd -> PIN_SWIPE_FRACTION * 0.55f
                    SwipeToDismissBoxValue.EndToStart ->
                        if (unreadCount > 0 && onMarkRead != null) {
                            MARK_READ_SWIPE_FRACTION * 0.5f
                        } else {
                            ARCHIVE_SWIPE_FRACTION * 0.55f
                        }
                    else -> 1f
                }
            if (state.progress >= minReveal) {
                haptic(HapticKind.SwipeReveal)
                revealedTarget = target
            }
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
                        emphasis = state.progress,
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
                        emphasis = state.progress,
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
    emphasis: Float = 1f,
) {
    val alpha = (0.35f + emphasis.coerceIn(0f, 1f) * 0.65f).coerceIn(0.35f, 1f)
    Box(
        Modifier
            .fillMaxSize()
            .background(color.copy(alpha), ProtoShapes.card)
            .padding(horizontal = 20.dp),
        contentAlignment = align,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Color.White)
            if (emphasis > 0.72f) {
                Text(label, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}
