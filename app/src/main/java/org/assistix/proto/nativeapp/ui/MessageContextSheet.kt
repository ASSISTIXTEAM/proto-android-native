package org.assistix.proto.nativeapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.assistix.proto.nativeapp.data.MsgItem

data class MessageContextAction(
    val icon: ImageVector,
    val label: String,
    val danger: Boolean = false,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageContextSheet(
    msg: MsgItem,
    actions: List<MessageContextAction>,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, shape = ProtoShapes.dialog) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                msg.bodyRaw.ifBlank { msg.body }.take(80).ifBlank { UiStrings.message },
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            actions.forEach { action ->
                if (!action.enabled) return@forEach
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .then(
                            Modifier.padding(horizontal = 4.dp),
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    MessageContextRow(
                        icon = action.icon,
                        label = action.label,
                        danger = action.danger,
                        onClick = {
                            action.onClick()
                            onDismiss()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageContextRow(
    icon: ImageVector,
    label: String,
    danger: Boolean,
    onClick: () -> Unit,
) {
    androidx.compose.material3.TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint =
                    if (danger) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                label,
                fontWeight = FontWeight.Medium,
                color =
                    if (danger) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

object MessageContextIcons {
    val Reply = Icons.AutoMirrored.Filled.Reply
    val Forward = Icons.AutoMirrored.Filled.Forward
    val Copy = Icons.Default.ContentCopy
    val React = Icons.Default.EmojiEmotions
    val Edit = Icons.Default.Edit
    val Pin = Icons.Default.PushPin
    val Translate = Icons.Default.Translate
    val Explain = Icons.Default.Lightbulb
    val Save = Icons.Default.Bookmark
    val Select = Icons.Default.SelectAll
    val Report = Icons.Default.Report
    val Delete = Icons.Default.Delete
    val Share = Icons.Default.Share
    val Star = Icons.Default.Star
    val StarOutline = Icons.Default.StarOutline
}
