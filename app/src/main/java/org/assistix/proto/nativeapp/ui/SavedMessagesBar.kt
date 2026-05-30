package org.assistix.proto.nativeapp.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.assistix.proto.nativeapp.data.MsgItem
import org.assistix.proto.nativeapp.data.ProtoSavedMetaStore

@Composable
fun SavedMessagesToolsBar(
    selected: List<MsgItem>,
    allTags: List<String>,
    onTagsChanged: () -> Unit,
    token: String? = null,
    api: org.assistix.proto.nativeapp.data.ProtoApi? = null,
    languageCode: String = "en",
    aiEnabled: Boolean = false,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var showTagDialog by remember { mutableStateOf(false) }
    var showReminderDialog by remember { mutableStateOf(false) }
    var tagInput by remember { mutableStateOf("") }

    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (aiEnabled && token != null && api != null) {
            FilterChip(
                selected = false,
                onClick = {
                    if (selected.isEmpty()) return@FilterChip
                    val blob =
                        selected
                            .map { it.bodyRaw.ifBlank { it.body } }
                            .filter { it.isNotBlank() }
                            .joinToString("\n")
                            .take(1200)
                    if (blob.isBlank()) return@FilterChip
                    scope.launch {
                        val suggested = withContext(Dispatchers.IO) { assistixSuggestTags(api, token, blob, languageCode) }
                        if (suggested.isNullOrBlank()) {
                            Toast.makeText(ctx, UiStrings.assistixError, Toast.LENGTH_SHORT).show()
                        } else {
                            tagInput = suggested
                            showTagDialog = true
                        }
                    }
                },
                enabled = selected.isNotEmpty(),
                label = { Text(UiStrings.assistixSavedSuggestTags) },
                leadingIcon = { Icon(Icons.Default.Label, null) },
            )
        }
        FilterChip(
            selected = false,
            onClick = { showTagDialog = selected.isNotEmpty() },
            enabled = selected.isNotEmpty(),
            label = { Text(UiStrings.savedAddTag) },
            leadingIcon = { Icon(Icons.Default.Label, null) },
        )
        FilterChip(
            selected = false,
            onClick = { showReminderDialog = selected.size == 1 },
            enabled = selected.size == 1,
            label = { Text(UiStrings.savedSetReminder) },
            leadingIcon = { Icon(Icons.Default.Alarm, null) },
        )
        FilterChip(
            selected = false,
            onClick = {
                val text =
                    selected
                        .map { m -> m.bodyRaw.ifBlank { m.body } }
                        .filter { it.isNotBlank() }
                        .joinToString("\n\n—\n\n")
                if (text.isBlank()) return@FilterChip
                val share =
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                ctx.startActivity(Intent.createChooser(share, UiStrings.savedExportNotes))
            },
            enabled = selected.isNotEmpty(),
            label = { Text(UiStrings.savedExportNotes) },
            leadingIcon = { Icon(Icons.Default.Share, null) },
        )
        allTags.take(8).forEach { tag ->
            FilterChip(
                selected = false,
                onClick = { /* filter hook — parent can wire */ },
                label = { Text("#$tag") },
            )
        }
    }

    if (showTagDialog) {
        AlertDialog(
            onDismissRequest = { showTagDialog = false },
            title = { Text(UiStrings.savedAddTag) },
            text = {
                OutlinedTextField(
                    tagInput,
                    { tagInput = it },
                    placeholder = { Text(UiStrings.savedTagHint) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val tags = tagInput.split(',', ' ').map { it.trim() }.filter { it.isNotEmpty() }
                        if (tags.isEmpty()) return@TextButton
                        scope.launch {
                            selected.forEach { m ->
                                if (m.id <= 0L) return@forEach
                                val cur = ProtoSavedMetaStore.get(ctx, m.id)
                                ProtoSavedMetaStore.set(
                                    ctx,
                                    m.id,
                                    cur.copy(tags = (cur.tags + tags).distinct()),
                                )
                            }
                            showTagDialog = false
                            tagInput = ""
                            onTagsChanged()
                            Toast.makeText(ctx, UiStrings.savedTagSaved, Toast.LENGTH_SHORT).show()
                        }
                    },
                ) { Text(UiStrings.save) }
            },
            dismissButton = {
                TextButton(onClick = { showTagDialog = false }) { Text(UiStrings.cancel) }
            },
        )
    }

    if (showReminderDialog && selected.size == 1) {
        val m = selected.first()
        var reminderText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showReminderDialog = false },
            title = { Text(UiStrings.savedSetReminder) },
            text = {
                Column {
                    Text(UiStrings.savedReminderHint, style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        reminderText,
                        { reminderText = it },
                        placeholder = { Text("2026-05-25 18:00") },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            val at = parseReminderMs(reminderText) ?: System.currentTimeMillis() + 3_600_000L
                            val cur = ProtoSavedMetaStore.get(ctx, m.id)
                            ProtoSavedMetaStore.set(ctx, m.id, cur.copy(reminderAtMs = at))
                            showReminderDialog = false
                            onTagsChanged()
                            Toast.makeText(ctx, UiStrings.savedReminderSaved, Toast.LENGTH_SHORT).show()
                        }
                    },
                ) { Text(UiStrings.save) }
            },
            dismissButton = {
                TextButton(onClick = { showReminderDialog = false }) { Text(UiStrings.cancel) }
            },
        )
    }
}

private fun parseReminderMs(raw: String): Long? {
    val t = raw.trim()
    if (t.isEmpty()) return null
  return try {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).parse(t)?.time
    } catch (_: Exception) {
        null
    }
}
