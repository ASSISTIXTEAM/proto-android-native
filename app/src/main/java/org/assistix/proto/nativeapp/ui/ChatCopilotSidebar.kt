package org.assistix.proto.nativeapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import org.assistix.proto.nativeapp.data.ProtoApi

@Composable
fun ChatCopilotSidebar(
    token: String?,
    api: ProtoApi,
    languageCode: String,
    chatTitle: String,
    messagePreviewLines: List<String>,
    initialPrompt: String? = null,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var question by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(initialPrompt) {
        val seed = initialPrompt?.trim().orEmpty()
        if (seed.isNotBlank()) {
            question = seed
        }
    }
    Column(
        modifier
            .width(300.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f))
            .padding(12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(UiStrings.chatCopilot, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = UiStrings.close)
            }
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            if (answer.isBlank() && !busy) {
                Text(
                    UiStrings.chatCopilotHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (busy) {
                Text(UiStrings.assistixThinking, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(answer, style = MaterialTheme.typography.bodyMedium)
            }
        }
        OutlinedTextField(
            value = question,
            onValueChange = { question = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(UiStrings.assistixHint) },
            maxLines = 3,
            trailingIcon = {
                IconButton(
                    enabled = !busy && question.isNotBlank() && !token.isNullOrBlank(),
                    onClick = {
                        val t = token ?: return@IconButton
                        val q = question.trim()
                        scope.launch {
                            busy = true
                            val lines =
                                listOf("Chat: $chatTitle") +
                                    messagePreviewLines.takeLast(24)
                            val reply =
                                withContext(Dispatchers.IO) {
                                    api.assistixRequest(
                                        token = t,
                                        action = "ask_chat",
                                        text = q,
                                        previewLines = lines,
                                        language = languageCode,
                                    )
                                }
                            answer = if (reply.ok) reply.text else reply.message.orEmpty().ifBlank { UiStrings.assistixError }
                            busy = false
                        }
                    },
                ) {
                    Icon(Icons.Default.Send, contentDescription = UiStrings.send, tint = ProtoOrange)
                }
            },
        )
    }
}
