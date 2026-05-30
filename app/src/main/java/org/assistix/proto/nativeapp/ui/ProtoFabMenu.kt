package org.assistix.proto.nativeapp.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class ProtoFabAction(
    val icon: ImageVector,
    val title: String,
    val subtitle: String? = null,
    val enabled: Boolean = true,
    val accent: Boolean = false,
    val onClick: () -> Unit,
)

data class ProtoFabSection(
    val title: String,
    val actions: List<ProtoFabAction>,
)

data class ProtoFabQuickTile(
    val icon: ImageVector,
    val title: String,
    val accent: Boolean = false,
    val badge: Int? = null,
    val onClick: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProtoChatsFabMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    quickTiles: List<ProtoFabQuickTile>,
    sections: List<ProtoFabSection>,
    hapticsEnabled: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val rotation by animateFloatAsState(if (expanded) 45f else 0f, animationSpec = tween(220), label = "fabRotate")
    val haptic = if (hapticsEnabled) ProtoHaptics.rememberSender() else null
    var wasExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(expanded) {
        if (expanded && !wasExpanded) haptic?.invoke(HapticKind.Action)
        wasExpanded = expanded
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    if (expanded) {
        ModalBottomSheet(
            onDismissRequest = { onExpandedChange(false) },
            sheetState = sheetState,
            shape = ProtoShapes.dialog,
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = {
                Box(
                    Modifier
                        .padding(vertical = 10.dp)
                        .width(40.dp)
                        .height(4.dp)
                        .clip(ProtoShapes.field)
                        .background(MaterialTheme.colorScheme.outline.copy(0.35f)),
                )
            },
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 20.dp),
            ) {
                Text(
                    UiStrings.fabMenuTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
                Text(
                    UiStrings.fabMenuSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
                )
                Spacer(Modifier.height(12.dp))

                if (quickTiles.isNotEmpty()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        quickTiles.take(4).forEach { tile ->
                            ProtoFabQuickTile(
                                tile = tile,
                                onClick = {
                                    haptic?.invoke(HapticKind.Tap)
                                    onExpandedChange(false)
                                    tile.onClick()
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                sections.forEach { section ->
                    if (section.actions.isEmpty()) return@forEach
                    ProtoSectionLabel(
                        section.title,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                    ProtoSurfaceCard(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    ) {
                        Column(Modifier.padding(vertical = 4.dp)) {
                            section.actions.forEachIndexed { idx, action ->
                                ProtoFabMenuRow(action) {
                                    haptic?.invoke(HapticKind.Tap)
                                    onExpandedChange(false)
                                    action.onClick()
                                }
                                if (idx < section.actions.lastIndex) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 14.dp),
                                        color = MaterialTheme.colorScheme.outline.copy(0.08f),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    Box(modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
        FloatingActionButton(
            onClick = {
                if (!expanded) haptic?.invoke(HapticKind.Action)
                onExpandedChange(!expanded)
            },
            modifier = Modifier.padding(end = 14.dp, bottom = 14.dp),
            shape = ProtoShapes.fab,
            containerColor = ProtoOrange,
            contentColor = Color.White,
        ) {
            Icon(
                if (expanded) Icons.Default.Close else Icons.Default.Add,
                contentDescription = UiStrings.newChat,
                modifier = Modifier.rotate(rotation),
            )
        }
    }
}

@Composable
private fun ProtoFabQuickTile(
    tile: ProtoFabQuickTile,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(ProtoShapes.card)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(0.45f))
                .clickable(onClick = onClick)
                .padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                tile.icon,
                contentDescription = tile.title,
                tint = if (tile.accent) ProtoOrange else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                tile.title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 13.sp,
            )
        }
        val badge = tile.badge
        if (badge != null && badge > 0) {
            Surface(
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                shape = CircleShape,
                color = ProtoOrange,
            ) {
                Text(
                    UiStrings.fabDevicesBadge(badge.coerceAtMost(99)),
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                )
            }
        }
    }
}

@Composable
private fun ProtoFabMenuRow(action: ProtoFabAction, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = action.enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    if (action.accent) ProtoOrange.copy(0.14f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(0.65f),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                action.icon,
                contentDescription = null,
                tint =
                    when {
                        !action.enabled -> MaterialTheme.colorScheme.outline
                        action.accent -> ProtoOrange
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                action.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val sub = action.subtitle
            if (!sub.isNullOrBlank()) {
                Text(
                    sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

fun buildChatsFabMenu(
    otherDeviceCount: Int = 0,
    recentChats: List<Pair<Int, String>> = emptyList(),
    onRecentChat: ((Int) -> Unit)? = null,
    onScanQr: () -> Unit,
    onOpenQrHub: () -> Unit,
    onOpenDevices: () -> Unit,
    onNewDm: () -> Unit,
    onNewGroup: () -> Unit,
    onNewChannel: () -> Unit,
    onRefresh: () -> Unit,
    onOpenAssistix: () -> Unit,
    onNewPoll: (() -> Unit)? = null,
    onGroupCall: (() -> Unit)? = null,
    onMarkAllRead: (() -> Unit)? = null,
): Pair<List<ProtoFabQuickTile>, List<ProtoFabSection>> {
    val tiles =
        listOf(
            ProtoFabQuickTile(Icons.Default.QrCodeScanner, UiStrings.qrHubScan, accent = true, onClick = onScanQr),
            ProtoFabQuickTile(Icons.Default.QrCode2, UiStrings.qrHubMyCode, onClick = onOpenQrHub),
            ProtoFabQuickTile(
                Icons.Default.PhoneAndroid,
                UiStrings.devicesSection,
                badge = otherDeviceCount.takeIf { it > 0 },
                onClick = onOpenDevices,
            ),
            ProtoFabQuickTile(Icons.Default.Computer, UiStrings.linkWebConnect, onClick = onScanQr),
        )
    val sections =
        buildList {
            if (recentChats.isNotEmpty() && onRecentChat != null) {
                add(
                    ProtoFabSection(
                        UiStrings.fabRecentChats,
                        recentChats.map { (id, title) ->
                            ProtoFabAction(
                                Icons.AutoMirrored.Filled.Chat,
                                title,
                                null,
                                onClick = { onRecentChat(id) },
                            )
                        },
                    ),
                )
            }
            add(
                ProtoFabSection(
                    UiStrings.fabSectionChats,
                    listOf(
                        ProtoFabAction(Icons.AutoMirrored.Filled.Chat, UiStrings.newDm, UiStrings.fabNewDmHint, onClick = onNewDm),
                        ProtoFabAction(Icons.Default.Groups, UiStrings.newGroup, UiStrings.fabNewGroupHint, accent = true, onClick = onNewGroup),
                        ProtoFabAction(Icons.Default.Campaign, UiStrings.newChannel, UiStrings.fabChannelHint, onClick = onNewChannel),
                    ),
                ),
            )
            val tools =
                buildList {
                    onNewPoll?.let {
                        add(ProtoFabAction(Icons.Default.Poll, UiStrings.createPoll, UiStrings.fabPollHint, onClick = it))
                    }
                    onGroupCall?.let {
                        add(ProtoFabAction(Icons.Default.VideoCall, UiStrings.groupCall, UiStrings.fabGroupCallHint, onClick = it))
                    }
                    add(
                        ProtoFabAction(
                            Icons.Default.AutoAwesome,
                            UiStrings.assistixAi,
                            UiStrings.fabAssistixHint,
                            onClick = onOpenAssistix,
                        ),
                    )
                }
            if (tools.isNotEmpty()) add(ProtoFabSection(UiStrings.fabSectionTools, tools))
            val more =
                buildList {
                    onMarkAllRead?.let {
                        add(ProtoFabAction(Icons.Default.DoneAll, UiStrings.markAllRead, UiStrings.fabMarkAllHint, onClick = it))
                    }
                    add(ProtoFabAction(Icons.Default.Refresh, UiStrings.refreshChats, UiStrings.fabRefreshHint, onClick = onRefresh))
                }
            add(ProtoFabSection(UiStrings.fabSectionMore, more))
        }
    return tiles to sections
}

fun defaultChatsFabActions(
    onNewDm: () -> Unit,
    onNewGroup: () -> Unit,
    onNewChannel: () -> Unit,
    onRefresh: () -> Unit,
    onOpenAssistix: () -> Unit,
    onOpenQrHub: (() -> Unit)? = null,
    onNewPoll: (() -> Unit)? = null,
    onGroupCall: (() -> Unit)? = null,
    onMarkAllRead: (() -> Unit)? = null,
): List<ProtoFabAction> {
    val (_, sections) =
        buildChatsFabMenu(
            onScanQr = { onOpenQrHub?.invoke() },
            onOpenQrHub = { onOpenQrHub?.invoke() },
            onOpenDevices = {},
            onNewDm = onNewDm,
            onNewGroup = onNewGroup,
            onNewChannel = onNewChannel,
            onRefresh = onRefresh,
            onOpenAssistix = onOpenAssistix,
            onNewPoll = onNewPoll,
            onGroupCall = onGroupCall,
            onMarkAllRead = onMarkAllRead,
        )
    return sections.flatMap { it.actions }
}
