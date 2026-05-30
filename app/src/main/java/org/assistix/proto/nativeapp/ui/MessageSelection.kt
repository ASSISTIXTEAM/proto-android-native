package org.assistix.proto.nativeapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Lock
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Одно действие на нижней панели выделения. */
data class MessageSelectionAction(
    val id: String,
    val icon: ImageVector,
    val label: String,
    val enabled: Boolean = true,
    val danger: Boolean = false,
    val onClick: () -> Unit,
)

private const val ACTIONS_PER_ROW = 4
private val ActionChipHeight = 52.dp
private val SelectCircleSlotWidth = 44.dp
private val PanelShape = ProtoShapes.field
private val ChipShape = RoundedCornerShape(16.dp)

/** Общий контейнер в стиле блоков настроек (SettingsBlock). */
@Composable
private fun ProtoPanelContainer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(PanelShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                shape = PanelShape,
            )
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        content()
    }
}

/** Компактная панель — одна строка, без лишней высоты. */
@Composable
internal fun ProtoPanelContainerLite(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(PanelShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                shape = PanelShape,
            )
            .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        content()
    }
}

/** Кружок выбора слева. */
@Composable
fun MessageSelectCircle(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(SelectCircleSlotWidth),
    ) {
        Icon(
            if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint =
                if (selected) {
                    ProtoOrange
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                },
            modifier = Modifier.size(26.dp),
        )
    }
}

/** Верхняя панель — тот же визуальный язык, что и настройки. */
@Composable
fun MessageSelectionTopBar(
    selectedCount: Int,
    allSelected: Boolean,
    onClose: () -> Unit,
    onToggleSelectAll: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        ProtoPanelContainerLite {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Close, contentDescription = UiStrings.cancel)
                }
                Text(
                    UiStrings.msgSelectedFmt(selectedCount),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                )
                TextButton(onClick = onToggleSelectAll) {
                    Text(
                        if (allSelected) UiStrings.msgDeselectAll else UiStrings.msgSelectAll,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

/** Компактная панель выбора чатов в списке. */
@Composable
fun ChatSelectionTopBar(
    selectedCount: Int,
    onCancel: () -> Unit,
    onPin: () -> Unit,
    onMute: () -> Unit,
    onLock: () -> Unit,
    onArchive: (() -> Unit)? = null,
    archiveLabel: String = UiStrings.chatMultiArchive,
    onMarkRead: (() -> Unit)? = null,
) {
    val scroll = rememberScrollState()
    Box(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        ProtoPanelContainerLite {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onCancel, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Close, contentDescription = UiStrings.cancel)
                }
                Text(
                    UiStrings.chatSelectedFmt(selectedCount),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false).padding(horizontal = 4.dp),
                )
                Row(
                    Modifier.horizontalScroll(scroll),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    onMarkRead?.let {
                        CompactSelectionAction(Icons.Default.MarkEmailRead, UiStrings.chatMultiMarkRead, it)
                    }
                    CompactSelectionAction(Icons.Default.PushPin, UiStrings.chatMultiPin, onPin)
                    CompactSelectionAction(Icons.Default.NotificationsOff, UiStrings.chatMultiMute, onMute)
                    onArchive?.let {
                        CompactSelectionAction(Icons.Default.Archive, archiveLabel, it)
                    }
                    CompactSelectionAction(Icons.Default.Lock, UiStrings.chatMultiLock, onLock)
                }
            }
        }
    }
}

@Composable
private fun CompactSelectionAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Icon(icon, contentDescription = label, tint = ProtoOrange, modifier = Modifier.size(22.dp))
    }
}

/** Нижняя панель действий — карточка как в настройках, сетка без налезания. */
@Composable
fun MessageSelectionBottomBar(
    actions: List<MessageSelectionAction>,
    modifier: Modifier = Modifier,
) {
    if (actions.isEmpty()) return

    val haptic = ProtoHaptics.rememberSender()
    val rows = actions.chunked(ACTIONS_PER_ROW)

    Box(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        ProtoPanelContainer {
            rows.forEach { rowActions ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    rowActions.forEach { action ->
                        MessageSelectionActionChip(
                            action = action,
                            onClick = {
                                if (!action.enabled) return@MessageSelectionActionChip
                                haptic(
                                    when {
                                        action.danger -> HapticKind.Error
                                        else -> HapticKind.Action
                                    },
                                )
                                action.onClick()
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(ACTIONS_PER_ROW - rowActions.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageSelectionActionChip(
    action: MessageSelectionAction,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val bg =
        when {
            !action.enabled -> MaterialTheme.colorScheme.surface.copy(alpha = 0.28f)
            action.danger -> MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
            else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)
        }
    val tint =
        when {
            !action.enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.32f)
            action.danger -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.onSurface
        }

    Column(
        modifier
            .height(ActionChipHeight)
            .clip(ChipShape)
            .background(bg)
            .then(
                if (action.enabled) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = ripple(bounded = true, color = ProtoOrange.copy(0.35f)),
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .semantics {
                role = Role.Button
                contentDescription = action.label
            }
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            action.icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(3.dp))
        Text(
            action.label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Подсветка строки сообщения в режиме выбора. */
@Composable
fun MessageSelectionRowBackground(
    selectionMode: Boolean,
    selected: Boolean,
    content: @Composable () -> Unit,
) {
    val bg =
        when {
            selectionMode && selected -> ProtoOrange.copy(alpha = 0.1f)
            selectionMode -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.14f)
            else -> Color.Transparent
        }
    Box(
        Modifier
            .fillMaxWidth()
            .clip(ProtoShapes.field)
            .background(bg)
            .padding(horizontal = if (selectionMode) 2.dp else 0.dp),
    ) {
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulkMessageDeleteSheet(
    count: Int,
    canDeleteForEveryone: Boolean,
    onDismiss: () -> Unit,
    onDeleteForMe: () -> Unit,
    onDeleteForEveryone: (() -> Unit)?,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = ProtoShapes.dialog,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding(),
        ) {
            Text(
                UiStrings.deleteMessageTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                UiStrings.msgDeleteBulkHint(count),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            if (canDeleteForEveryone && onDeleteForEveryone != null) {
                ProtoPrimaryButton(
                    UiStrings.deleteForAllCountFmt(count),
                    onDeleteForEveryone,
                    Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
            }
            ProtoPrimaryButton(
                UiStrings.deleteForMeCountFmt(count),
                onDeleteForMe,
                Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            ProtoGhostButton(UiStrings.cancel, onDismiss, Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
        }
    }
}

object MessageSelectionIcons {
    val Reply get() = Icons.AutoMirrored.Filled.Reply
    val Copy get() = Icons.Default.ContentCopy
    val Delete get() = Icons.Default.Delete
    val Edit get() = Icons.Default.Edit
    val Forward get() = Icons.AutoMirrored.Filled.Forward
    val Report get() = Icons.Default.Report
    val React get() = Icons.Default.EmojiEmotions
    val Saved get() = Icons.Default.Bookmark
    val Pin get() = Icons.Default.PushPin
    val Translate get() = Icons.Default.Translate
    val Summarize get() = Icons.Default.Summarize
}
