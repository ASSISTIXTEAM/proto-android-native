package org.assistix.proto.nativeapp.ui

import android.media.MediaRecorder
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.assistix.proto.nativeapp.data.AssistixChatRepository
import org.assistix.proto.nativeapp.data.AssistixChatTurn
import org.assistix.proto.nativeapp.data.AssistixRateLimit
import org.assistix.proto.nativeapp.data.AssistixText
import org.assistix.proto.nativeapp.data.AssistixThread
import org.assistix.proto.nativeapp.data.ProtoApi

@Composable
fun AssistixAiTab(
    token: String?,
    api: ProtoApi,
    languageCode: String = "en",
    deviceLanguage: String = java.util.Locale.getDefault().language,
    assistixChat: AssistixChatRepository,
    stt: org.assistix.proto.nativeapp.data.ProtoSttCoordinator,
    homeResetTick: Int = 0,
) {
    var activeThreadId by rememberSaveable { mutableStateOf<Long?>(null) }

    LaunchedEffect(homeResetTick) {
        if (homeResetTick > 0) activeThreadId = null
    }
    val scope = rememberCoroutineScope()
    val threads by assistixChat.observeThreads().collectAsState(initial = emptyList())

    LaunchedEffect(threads.size) {
        if (threads.isEmpty()) {
            assistixChat.ensureDefaultThread()
        }
    }

    if (activeThreadId == null) {
        AssistixThreadListScreen(
            token = token,
            api = api,
            assistixChat = assistixChat,
            onOpenThread = { activeThreadId = it },
            onNewThread = {
                scope.launch {
                    val id = assistixChat.createThread("")
                    activeThreadId = id
                }
            },
        )
    } else {
        AssistixChatScreen(
            threadId = activeThreadId!!,
            token = token,
            api = api,
            languageCode = languageCode,
            deviceLanguage = deviceLanguage,
            assistixChat = assistixChat,
            stt = stt,
            onBack = { activeThreadId = null },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AssistixThreadListScreen(
    token: String?,
    api: ProtoApi,
    assistixChat: AssistixChatRepository,
    onOpenThread: (Long) -> Unit,
    onNewThread: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = ProtoHaptics.rememberSender()
    var configured by remember { mutableStateOf(true) }
    var checking by remember { mutableStateOf(true) }
    val threads by assistixChat.observeThreads().collectAsState(initial = emptyList())
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var menuThread by remember { mutableStateOf<AssistixThread?>(null) }
    var renameTarget by remember { mutableStateOf<AssistixThread?>(null) }
    var renameDraft by remember { mutableStateOf("") }
    var rateLimit by remember { mutableStateOf<AssistixRateLimit?>(null) }

    LaunchedEffect(token) {
        checking = true
        if (token.isNullOrBlank()) {
            configured = false
            rateLimit = null
            checking = false
            return@LaunchedEffect
        }
        val cat = withContext(Dispatchers.IO) { api.assistixCatalog(token) }
        configured = cat?.configured == true
        rateLimit = cat?.rateLimit
        checking = false
    }

    Box(Modifier.fillMaxSize().navigationBarsPadding()) {
        ProtoBrandBackdrop()
        when {
            checking -> {
                CircularProgressIndicator(Modifier.align(Alignment.Center), color = ProtoOrange)
            }
            !configured -> {
                ProfileGlassCard(
                    Modifier
                        .align(Alignment.Center)
                        .padding(24.dp)
                        .fillMaxWidth(),
                ) {
                    Text(
                        UiStrings.assistixNotConfigured,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            else -> {
                Column(Modifier.fillMaxSize()) {
                    AssistixHeroBanner(
                        rateLimit = rateLimit,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                    if (selectionMode) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(onClick = {
                                selectionMode = false
                                selectedIds = emptySet()
                            }) { Text(UiStrings.cancel) }
                            Text(
                                UiStrings.assistixSelectedFmt(selectedIds.size),
                                fontWeight = FontWeight.SemiBold,
                            )
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        selectedIds.forEach { assistixChat.deleteThread(it) }
                                        selectionMode = false
                                        selectedIds = emptySet()
                                    }
                                },
                                enabled = selectedIds.isNotEmpty(),
                            ) { Text(UiStrings.delete, color = MaterialTheme.colorScheme.error) }
                        }
                    }
                    if (threads.isEmpty()) {
                        AssistixEmptyState(
                            onNewChat = onNewThread,
                            modifier =
                                Modifier
                                    .padding(horizontal = 16.dp)
                                    .padding(top = 8.dp),
                        )
                    } else {
                        LazyColumn(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 88.dp, top = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(threads, key = { it.id }) { thread ->
                                val picked = thread.id in selectedIds
                                Box(
                                    Modifier.combinedClickable(
                                        onClick = {
                                            if (selectionMode) {
                                                selectedIds =
                                                    if (picked) selectedIds - thread.id else selectedIds + thread.id
                                            } else {
                                                onOpenThread(thread.id)
                                            }
                                        },
                                        onLongClick = {
                                            haptic(HapticKind.Action)
                                            if (!selectionMode) {
                                                selectionMode = true
                                                selectedIds = setOf(thread.id)
                                            } else {
                                                menuThread = thread
                                            }
                                        },
                                    ),
                                ) {
                                    AssistixThreadGlassRow(
                                        thread = thread,
                                        selected = picked,
                                        selectionMode = selectionMode,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        if (!selectionMode && configured && !checking) {
            FloatingActionButton(
                onClick = onNewThread,
                modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
                containerColor = ProtoOrange,
                contentColor = Color.White,
            ) {
                Icon(Icons.Default.Add, contentDescription = UiStrings.assistixNewChat)
            }
        }
    }
    menuThread?.let { thread ->
        AlertDialog(
            onDismissRequest = { menuThread = null },
            title = { Text(thread.title, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            renameDraft = thread.title
                            renameTarget = thread
                            menuThread = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(UiStrings.assistixRenameThread) }
                    TextButton(
                        onClick = {
                            scope.launch {
                                val file = assistixChat.exportThread(ctx, thread.id)
                                Toast.makeText(
                                    ctx,
                                    if (file != null) UiStrings.assistixExportedFmt(file.absolutePath) else UiStrings.assistixExportEmpty,
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                            menuThread = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(UiStrings.assistixExport) }
                    TextButton(
                        onClick = {
                            scope.launch { assistixChat.deleteThread(thread.id) }
                            menuThread = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(UiStrings.delete, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { menuThread = null }) { Text(UiStrings.close) } },
        )
    }
    renameTarget?.let { thread ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(UiStrings.assistixRenameThread) },
            text = {
                OutlinedTextField(
                    value = renameDraft,
                    onValueChange = { renameDraft = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            assistixChat.renameThread(thread.id, renameDraft)
                            renameTarget = null
                        }
                    },
                ) { Text(UiStrings.save) }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text(UiStrings.cancel) } },
        )
    }
}

@Composable
private fun AssistixChatScreen(
    threadId: Long,
    token: String?,
    api: ProtoApi,
    languageCode: String,
    deviceLanguage: String,
    assistixChat: AssistixChatRepository,
    stt: org.assistix.proto.nativeapp.data.ProtoSttCoordinator,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val chat = remember { mutableStateListOf<AssistixAiLine>() }
    val listState = rememberLazyListState()
    var streamIndex by remember { mutableIntStateOf(-1) }
    var confirmClear by remember { mutableStateOf(false) }
    var rateLimit by remember { mutableStateOf<AssistixRateLimit?>(null) }
    var recording by remember { mutableStateOf(false) }
    var voiceFile by remember { mutableStateOf<java.io.File?>(null) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    val saved by assistixChat.observeMessages(threadId).collectAsState(initial = emptyList())
    val threads by assistixChat.observeThreads().collectAsState(initial = emptyList())
    val threadTitle = threads.find { it.id == threadId }?.title?.ifBlank { null } ?: UiStrings.assistixChat

    LaunchedEffect(token) {
        if (token.isNullOrBlank()) {
            rateLimit = null
            return@LaunchedEffect
        }
        rateLimit = withContext(Dispatchers.IO) { api.assistixCatalog(token)?.rateLimit }
    }

    LaunchedEffect(threadId, saved, busy, streamIndex) {
        if (!busy && streamIndex < 0) {
            chat.clear()
            chat.addAll(saved.map { AssistixAiLine(it.role, it.text) })
        }
    }

    LaunchedEffect(chat.size, busy, streamIndex) {
        val target = if (streamIndex >= 0) streamIndex else chat.lastIndex
        if (target >= 0) listState.animateScrollToItem(target)
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(UiStrings.assistixDeleteHistoryTitle) },
            text = { Text(UiStrings.assistixDeleteHistoryBody) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        scope.launch {
                            assistixChat.clearThread(threadId)
                            chat.clear()
                            streamIndex = -1
                        }
                    },
                ) { Text(UiStrings.delete, color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(UiStrings.cancel) }
            },
        )
    }

    Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
        AssistixChatGlassBar(
            title = threadTitle,
            onBack = onBack,
            onExport =
                if (chat.isNotEmpty() && !busy) {
                    {
                        scope.launch {
                            val file = assistixChat.exportThread(ctx, threadId)
                            Toast.makeText(
                                ctx,
                                if (file != null) UiStrings.assistixExportedFmt(file.absolutePath) else UiStrings.assistixExportEmpty,
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    }
                } else {
                    null
                },
            onClear = if (chat.isNotEmpty() && !busy) { { confirmClear = true } } else null,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )

        Box(Modifier.weight(1f).fillMaxWidth()) {
            ProtoBrandBackdrop()
            if (chat.isEmpty() && !busy) {
                ProfileGlassCard(
                    Modifier
                        .align(Alignment.Center)
                        .padding(24.dp)
                        .fillMaxWidth(),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Psychology, null, tint = ProtoOrange, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.height(12.dp))
                        Text(
                            UiStrings.assistixChatEmpty,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            UiStrings.assistixToolsChatHint,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(chat.size, key = { "ai-$it-${chat[it].text.hashCode()}" }) { i ->
                        AssistixAiBubble(chat[i])
                    }
                }
            }
        }

        AssistixComposerGlass(
            rateLimit = rateLimit,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
            IconButton(
                onClick = {
                    val t = token ?: return@IconButton
                    if (recording) {
                        try {
                            recorder?.stop()
                        } catch (_: Exception) {
                        }
                        recorder?.release()
                        recorder = null
                        recording = false
                        val file = voiceFile
                        voiceFile = null
                        if (file == null || !file.exists() || file.length() < 32) return@IconButton
                        scope.launch {
                            val result =
                                stt.transcribe(
                                    token = t,
                                    uploadId = "",
                                    conversationId = 0,
                                    mediaFile = file,
                                    languageCode = languageCode,
                                )
                            result.getOrNull()?.text?.let { spoken ->
                                input = if (input.isBlank()) spoken else "${input.trimEnd()} $spoken"
                            }
                            file.delete()
                        }
                    } else {
                        val file = java.io.File(ctx.cacheDir, "assistix_voice_${System.currentTimeMillis()}.m4a")
                        voiceFile = file
                        val rec =
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                MediaRecorder(ctx)
                            } else {
                                @Suppress("DEPRECATION")
                                MediaRecorder()
                            }
                        rec.setAudioSource(MediaRecorder.AudioSource.MIC)
                        rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                        rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                        rec.setOutputFile(file.absolutePath)
                        rec.prepare()
                        rec.start()
                        recorder = rec
                        recording = true
                    }
                },
                enabled = !busy && !token.isNullOrBlank(),
            ) {
                Icon(
                    if (recording) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = UiStrings.voiceInput,
                    tint = if (recording) MaterialTheme.colorScheme.error else ProtoOrange,
                )
            }
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(UiStrings.assistixChatPlaceholder) },
                minLines = 1,
                maxLines = 5,
                shape = ProtoShapes.field,
                enabled = !busy && !token.isNullOrBlank() && rateLimit?.isExhausted() != true,
            )
            IconButton(
                onClick = {
                    val t = token ?: return@IconButton
                    val q = input.trim()
                    if (q.isEmpty() || busy || rateLimit?.isExhausted() == true) return@IconButton
                    scope.launch {
                        busy = true
                        assistixChat.appendUser(threadId, q)
                        chat.add(AssistixAiLine("user", q))
                        input = ""
                        val historyTurns = chat.dropLast(1).takeLast(4).map { AssistixChatTurn(it.role, it.text) }
                        chat.add(AssistixAiLine("assistant", "", streaming = true))
                        streamIndex = chat.lastIndex
                        val buffer = StringBuilder()
                        val reply =
                            withContext(Dispatchers.IO) {
                                api.assistixChat(
                                    token = t,
                                    text = q,
                                    language = languageCode,
                                    deviceLanguage = deviceLanguage,
                                    history = historyTurns,
                                    onDelta = { delta ->
                                        buffer.append(delta)
                                        val cleaned = AssistixText.forChat(buffer.toString())
                                        scope.launch(Dispatchers.Main) {
                                            if (streamIndex in chat.indices) {
                                                chat[streamIndex] = AssistixAiLine("assistant", cleaned, streaming = true)
                                            }
                                        }
                                    },
                                )
                            }
                        busy = false
                        reply.rateLimit?.let { rateLimit = it }
                        if (reply.ok && reply.text.isNotBlank()) {
                            if (streamIndex in chat.indices) {
                                chat[streamIndex] = AssistixAiLine("assistant", reply.text, streaming = false)
                            }
                            assistixChat.appendAssistant(threadId, reply.text)
                        } else {
                            reply.rateLimit?.let { rateLimit = it }
                            if (streamIndex in chat.indices) chat.removeAt(streamIndex)
                            if (chat.isNotEmpty() && chat.last().role == "user") chat.removeAt(chat.lastIndex)
                            assistixChat.rollbackLastUserMessage(threadId)
                            val toast =
                                when {
                                    reply.error == "assistix_not_configured" -> UiStrings.assistixNotConfigured
                                    reply.error == "rate_limited" -> UiStrings.assistixRateLimited
                                    reply.error == "network" -> UiStrings.networkUnavailable
                                    !reply.message.isNullOrBlank() -> reply.message!!.take(200)
                                    else -> UiStrings.assistixError
                                }
                            Toast.makeText(ctx, toast, Toast.LENGTH_LONG).show()
                        }
                        streamIndex = -1
                    }
                },
                enabled = !busy && input.isNotBlank() && !token.isNullOrBlank() && rateLimit?.isExhausted() != true,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = UiStrings.send,
                    tint =
                        if (input.isNotBlank() && !busy && rateLimit?.isExhausted() != true) {
                            ProtoOrange
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                )
            }
            }
        }
    }
}
