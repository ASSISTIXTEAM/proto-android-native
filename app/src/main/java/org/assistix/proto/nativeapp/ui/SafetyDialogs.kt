package org.assistix.proto.nativeapp.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.assistix.proto.nativeapp.data.MsgItem
import org.assistix.proto.nativeapp.data.ProtoApi

private val contentReasons = setOf("spam", "abuse", "illegal", "other")

@Composable
fun ReportMessagesBulkDialog(
    targetUserId: Int,
    conversationId: Int,
    messageIds: Set<Long>,
    api: ProtoApi,
    token: String?,
    onDismiss: () -> Unit,
    onDone: (String) -> Unit,
) {
    ReportDialog(
        targetUserId = targetUserId,
        conversationId = conversationId,
        preselectedMessageIds = messageIds,
        selectableMessages = emptyList(),
        title = UiStrings.reportMessagesSelected.format(messageIds.size),
        api = api,
        token = token,
        onDismiss = onDismiss,
        onDone = onDone,
    )
}

@Composable
fun ReportMessageDialog(
    messageId: Long,
    conversationId: Int,
    targetUserId: Int,
    api: ProtoApi,
    token: String?,
    onDismiss: () -> Unit,
    onDone: (String) -> Unit,
) {
    ReportDialog(
        targetUserId = targetUserId,
        conversationId = conversationId,
        preselectedMessageIds = if (messageId > 0) setOf(messageId) else emptySet(),
        selectableMessages = emptyList(),
        title = UiStrings.reportMessageTitle.format(messageId),
        api = api,
        token = token,
        onDismiss = onDismiss,
        onDone = onDone,
    )
}

@Composable
fun ReportUserDialog(
    targetUserId: Int,
    api: ProtoApi,
    token: String?,
    conversationId: Int = 0,
    selectableMessages: List<MsgItem> = emptyList(),
    onDismiss: () -> Unit,
    onDone: (String) -> Unit,
) {
    ReportDialog(
        targetUserId = targetUserId,
        conversationId = conversationId,
        preselectedMessageIds = emptySet(),
        selectableMessages = selectableMessages,
        title = UiStrings.reportTitle,
        api = api,
        token = token,
        onDismiss = onDismiss,
        onDone = onDone,
    )
}

@Composable
private fun ReportDialog(
    targetUserId: Int,
    conversationId: Int,
    preselectedMessageIds: Set<Long>,
    selectableMessages: List<MsgItem>,
    title: String,
    api: ProtoApi,
    token: String?,
    onDismiss: () -> Unit,
    onDone: (String) -> Unit,
) {
    var reason by remember(preselectedMessageIds, selectableMessages) {
        mutableStateOf(
            when {
                preselectedMessageIds.isNotEmpty() -> "spam"
                selectableMessages.isEmpty() -> "profile"
                else -> "spam"
            },
        )
    }
    var selectedIds by remember(preselectedMessageIds) {
        mutableStateOf(preselectedMessageIds)
    }
    LaunchedEffect(preselectedMessageIds) {
        if (preselectedMessageIds.isNotEmpty()) {
            selectedIds = preselectedMessageIds
            if (reason == "profile") reason = "spam"
        }
    }
    var details by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val needsMessages = reason in contentReasons
    val canSubmit =
        !busy &&
            !token.isNullOrBlank() &&
            targetUserId > 0 &&
            (!needsMessages || selectedIds.isNotEmpty())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(UiStrings.reportHint, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                ReasonChip(UiStrings.reportReasonProfile, reason == "profile") {
                    reason = "profile"
                }
                ReasonChip(UiStrings.reportReasonSpam, reason == "spam") { reason = "spam" }
                ReasonChip(UiStrings.reportReasonAbuse, reason == "abuse") { reason = "abuse" }
                ReasonChip(UiStrings.reportReasonIllegal, reason == "illegal") { reason = "illegal" }
                ReasonChip(UiStrings.reportReasonOther, reason == "other") { reason = "other" }
                Spacer(Modifier.height(8.dp))

                when {
                    reason == "profile" -> {
                        Text(
                            UiStrings.reportProfileNoMessagesHint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    needsMessages && selectableMessages.isEmpty() && preselectedMessageIds.isEmpty() -> {
                        Text(
                            UiStrings.reportSelectMessagesInChat,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    needsMessages && selectableMessages.isNotEmpty() -> {
                        Text(
                            UiStrings.reportSelectMessages,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (selectedIds.isNotEmpty()) {
                            Text(
                                UiStrings.reportMessagesSelected.format(selectedIds.size),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 220.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            items(selectableMessages, key = { it.id }) { m ->
                                val checked = m.id in selectedIds
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedIds =
                                                if (checked) {
                                                    selectedIds - m.id
                                                } else {
                                                    selectedIds + m.id
                                                }
                                        }
                                        .padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(checked = checked, onCheckedChange = null)
                                    Column(Modifier.weight(1f).padding(start = 4.dp)) {
                                        Text(
                                            "#${m.id} · ${m.body.take(80)}",
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    needsMessages && preselectedMessageIds.isNotEmpty() -> {
                        Text(
                            UiStrings.reportMessageHint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    details,
                    { details = it },
                    label = { Text(UiStrings.reportDetailsOptional) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSubmit,
                onClick = {
                    val t = token ?: return@TextButton
                    scope.launch {
                        busy = true
                        val ids = if (needsMessages) selectedIds.toList() else emptyList()
                        val msg =
                            withContext(Dispatchers.IO) {
                                api.report(
                                    t,
                                    targetUserId,
                                    conversationId,
                                    reason,
                                    details,
                                    ids,
                                )
                            }
                        busy = false
                        if (msg != null) {
                            onDone(msg)
                            onDismiss()
                        }
                    }
                },
            ) { Text(UiStrings.reportSubmit) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(UiStrings.cancel) }
        },
    )
}

@Composable
private fun ReasonChip(label: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(
            label,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun BlockUserDialog(
    displayName: String,
    api: ProtoApi,
    token: String?,
    userId: Int,
    onDismiss: () -> Unit,
    onBlocked: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(UiStrings.blockUserTitle) },
        text = { Text(UiStrings.blockUserConfirm.format(displayName)) },
        confirmButton = {
            TextButton(
                onClick = {
                    val t = token ?: return@TextButton
                    scope.launch {
                        withContext(Dispatchers.IO) { api.blockUser(t, userId) }
                        onBlocked()
                        onDismiss()
                    }
                },
            ) { Text(UiStrings.blockUserAction) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(UiStrings.cancel) }
        },
    )
}

fun openLegalUrl(context: android.content.Context, url: String) {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
