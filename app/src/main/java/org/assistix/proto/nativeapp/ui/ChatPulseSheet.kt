package org.assistix.proto.nativeapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.assistix.proto.nativeapp.data.AssistixChatTurn
import org.assistix.proto.nativeapp.data.AssistixRateLimit
import org.assistix.proto.nativeapp.data.AssistixUsageHub
import org.assistix.proto.nativeapp.data.ProtoApi

private data class PulseChatLine(val role: String, val text: String)

@Composable
fun ChatPulseSheet(
    token: String?,
    api: ProtoApi,
    languageCode: String,
    chatTitle: String,
    messagePreviewLines: List<String>,
    initialPrompt: String? = null,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val lines = remember { mutableStateListOf<PulseChatLine>() }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val budget by AssistixUsageHub.budget.collectAsState()
    val exhausted = budget?.isExhausted() == true

    LaunchedEffect(initialPrompt) {
        val seed = initialPrompt?.trim().orEmpty()
        if (seed.isNotBlank()) input = seed
    }

    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) {
            listState.animateScrollToItem(lines.lastIndex)
        }
    }

    fun send() {
        val t = token ?: return
        val q = input.trim()
        if (q.isBlank() || busy || exhausted) return
        scope.launch {
            busy = true
            lines.add(PulseChatLine("user", q))
            input = ""
            val contextLines =
                buildList {
                    add("Chat topic: $chatTitle")
                    messagePreviewLines.takeLast(20).forEach { add(it) }
                }
            val history =
                lines.dropLast(1).takeLast(8).map { AssistixChatTurn(it.role, it.text) }
            val reply =
                withContext(Dispatchers.IO) {
                    if (history.isEmpty()) {
                        api.assistixRequest(
                            token = t,
                            action = "ask_chat",
                            text = q,
                            previewLines = contextLines,
                            language = languageCode,
                        )
                    } else {
                        api.assistixChat(
                            token = t,
                            text = q,
                            language = languageCode,
                            history = history,
                        )
                    }
                }
            if (reply.ok && reply.text.isNotBlank()) {
                lines.add(PulseChatLine("assistant", reply.text))
            } else {
                val err =
                    when {
                        reply.error == "rate_limited" -> UiStrings.assistixRateLimited
                        else -> reply.message.orEmpty().ifBlank { UiStrings.assistixError }
                    }
                lines.add(PulseChatLine("assistant", err))
            }
            busy = false
        }
    }

    Column(
        modifier
            .fillMaxWidth()
            .heightIn(min = 280.dp, max = 520.dp)
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Bolt, null, tint = ProtoOrange, modifier = Modifier.size(22.dp))
                Text(
                    UiStrings.chatPulse,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = UiStrings.close)
            }
        }
        Text(
            UiStrings.chatPulseHint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        budget?.let { limit ->
            AssistixTokenMeterCompact(limit)
            Spacer(Modifier.size(8.dp))
        }
        LazyColumn(
            state = listState,
            modifier =
                Modifier
                    .weight(1f, fill = false)
                    .heightIn(min = 120.dp, max = 300.dp)
                    .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (lines.isEmpty() && !busy) {
                item {
                    Text(
                        UiStrings.chatPulseEmpty,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(lines.size) { idx ->
                val line = lines[idx]
                val mine = line.role == "user"
                Box(
                    Modifier.fillMaxWidth(),
                    contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart,
                ) {
                    Text(
                        line.text,
                        modifier =
                            Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (mine) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.92f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    },
                                )
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        color =
                            if (mine) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            if (busy) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = ProtoOrange)
                        Text(
                            UiStrings.assistixThinking,
                            modifier = Modifier.padding(start = 10.dp),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(UiStrings.chatPulseInputHint) },
            maxLines = 4,
            enabled = !busy && !exhausted && !token.isNullOrBlank(),
            trailingIcon = {
                IconButton(
                    enabled = !busy && input.isNotBlank() && !exhausted && !token.isNullOrBlank(),
                    onClick = { send() },
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = UiStrings.send, tint = ProtoOrange)
                }
            },
        )
    }
}

@Composable
fun AssistixTokenMeterCompact(limit: AssistixRateLimit) {
    val fraction =
        if (limit.limit <= 0) {
            0f
        } else {
            (limit.used.toFloat() / limit.limit.toFloat()).coerceIn(0f, 1f)
        }
    Column(Modifier.fillMaxWidth()) {
        Text(
            if (limit.isTokenBudget) {
                UiStrings.assistixTokensUsedFmt(limit.used, limit.limit)
            } else {
                UiStrings.assistixRateRemainingFmt(limit.remaining, limit.limit)
            },
            style = MaterialTheme.typography.labelMedium,
            color =
                if (limit.isExhausted()) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
        if (limit.resetInSec > 0 && limit.isTokenBudget) {
            Text(
                UiStrings.assistixTokensResetFmt(limit.resetInSec / 3600, (limit.resetInSec % 3600) / 60),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            )
        }
        Spacer(Modifier.size(6.dp))
        LinearProgressIndicator(
            progress = { 1f - fraction },
            modifier = Modifier.fillMaxWidth(),
            color = if (limit.isExhausted()) MaterialTheme.colorScheme.error else ProtoOrange,
        )
    }
}
