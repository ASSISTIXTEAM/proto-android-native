package org.assistix.proto.nativeapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.assistix.proto.nativeapp.data.ConvItem

@Composable
fun ChatFolderEditorDialog(
    title: String,
    folderName: String,
    onFolderNameChange: (String) -> Unit,
    colorId: Int,
    onColorIdChange: (Int) -> Unit,
    chats: List<ConvItem>,
    pickedIds: Set<Int>,
    onToggleChat: (Int, Boolean) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    folderName,
                    onFolderNameChange,
                    label = { Text(UiStrings.folderName) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(UiStrings.folderColor, style = MaterialTheme.typography.labelMedium)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    (1..FolderColors.count).forEach { id ->
                        val c = FolderColors.colorFor(id) ?: ProtoOrange
                        val selected = colorId == id
                        Box(
                            Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(c)
                                .border(
                                    width = if (selected) 3.dp else 0.dp,
                                    color = if (selected) Color.White else c.copy(alpha = 0f),
                                    shape = CircleShape,
                                )
                                .clickable { onColorIdChange(id) },
                        )
                    }
                }
                Text(UiStrings.folderPickChats, style = MaterialTheme.typography.labelMedium)
                Column(
                    Modifier
                        .heightIn(max = 220.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    chats.filter { it.kind != "saved" }.forEach { c ->
                        val checked = c.id in pickedIds
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onToggleChat(c.id, !checked) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = checked, onCheckedChange = { onToggleChat(c.id, it) })
                            Text(c.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onSave,
                enabled = folderName.trim().isNotEmpty(),
            ) { Text(UiStrings.folderSave) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(UiStrings.cancel) }
        },
    )
}

@Composable
fun ChatFolderDeleteDialog(
    folderName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(UiStrings.folderDelete, fontWeight = FontWeight.Bold) },
        text = { Text(UiStrings.folderDeleteConfirmFmt(folderName)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(UiStrings.delete, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(UiStrings.cancel) }
        },
    )
}
