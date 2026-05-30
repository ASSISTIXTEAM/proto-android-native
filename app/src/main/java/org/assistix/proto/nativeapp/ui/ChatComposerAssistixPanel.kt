package org.assistix.proto.nativeapp.ui



import android.content.ClipData

import android.content.ClipboardManager

import android.content.Context

import android.widget.Toast

import androidx.compose.foundation.horizontalScroll

import androidx.compose.foundation.layout.Arrangement

import androidx.compose.foundation.layout.Column

import androidx.compose.foundation.layout.Row

import androidx.compose.foundation.layout.fillMaxWidth

import androidx.compose.foundation.layout.padding

import androidx.compose.foundation.layout.size

import androidx.compose.foundation.rememberScrollState

import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.automirrored.filled.ShortText

import androidx.compose.material.icons.filled.AutoFixHigh

import androidx.compose.material.icons.filled.Close

import androidx.compose.material.icons.filled.ExpandLess

import androidx.compose.material.icons.filled.ExpandMore

import androidx.compose.material.icons.filled.Lightbulb

import androidx.compose.material.icons.filled.Psychology

import androidx.compose.material.icons.filled.QuestionAnswer

import androidx.compose.material.icons.filled.Summarize

import androidx.compose.material.icons.filled.TaskAlt

import androidx.compose.material.icons.filled.Translate

import androidx.compose.material.icons.filled.Tune

import androidx.compose.material.icons.filled.UnfoldMore

import androidx.compose.material3.AlertDialog

import androidx.compose.material3.CircularProgressIndicator

import androidx.compose.material3.ExperimentalMaterial3Api

import androidx.compose.material3.FilterChip

import androidx.compose.material3.FilterChipDefaults

import androidx.compose.material3.Icon

import androidx.compose.material3.IconButton

import androidx.compose.material3.MaterialTheme

import androidx.compose.material3.ModalBottomSheet

import androidx.compose.material3.OutlinedTextField

import androidx.compose.material3.Surface

import androidx.compose.material3.Text

import androidx.compose.material3.TextButton

import androidx.compose.material3.rememberModalBottomSheetState

import androidx.compose.runtime.Composable

import androidx.compose.runtime.getValue

import androidx.compose.runtime.mutableStateOf

import androidx.compose.runtime.remember

import androidx.compose.runtime.rememberCoroutineScope

import androidx.compose.runtime.setValue

import androidx.compose.ui.Alignment

import androidx.compose.ui.Modifier

import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.text.font.FontWeight

import androidx.compose.ui.unit.dp

import kotlinx.coroutines.Dispatchers

import kotlinx.coroutines.launch

import kotlinx.coroutines.withContext

import org.assistix.proto.nativeapp.data.MsgItem

import org.assistix.proto.nativeapp.data.ProtoApi



@OptIn(ExperimentalMaterial3Api::class)

@Composable
fun ChatComposerAssistixTrigger(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!enabled) return
    IconButton(onClick = onClick, modifier = modifier.size(40.dp)) {
        Icon(
            Icons.Default.AutoFixHigh,
            contentDescription = UiStrings.assistixAi,
            tint = ProtoOrange,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatComposerAssistixSheet(
    open: Boolean,
    onDismiss: () -> Unit,
    token: String?,
    api: ProtoApi,
    draft: String,
    onDraft: (String) -> Unit,
    messages: List<MsgItem>,
    languageCode: String,
    enabled: Boolean,
) {
    if (!enabled || token.isNullOrBlank() || !open) return

    val ctx = LocalContext.current

    val scope = rememberCoroutineScope()

    var busy by remember { mutableStateOf(false) }

    var showStyles by remember { mutableStateOf(false) }

    var resultTitle by remember { mutableStateOf<String?>(null) }

    var resultBody by remember { mutableStateOf<String?>(null) }

    var resultCanInsert by remember { mutableStateOf(false) }

    var smartReplies by remember { mutableStateOf<List<String>>(emptyList()) }

    var showAskDialog by remember { mutableStateOf(false) }

    var askText by remember { mutableStateOf("") }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)



    fun toastAssistixError(reply: org.assistix.proto.nativeapp.data.AssistixReply) {

        val toast =

            when {

                reply.error == "network" -> UiStrings.networkUnavailable

                reply.error == "assistix_not_configured" -> UiStrings.assistixNotConfigured

                reply.error == "rate_limited" -> UiStrings.assistixRateLimited

                !reply.message.isNullOrBlank() -> reply.message!!.take(200)

                else -> UiStrings.assistixError

            }

        Toast.makeText(ctx, toast, Toast.LENGTH_LONG).show()

    }



    fun showSheet(title: String, body: String, canInsert: Boolean = false) {

        resultTitle = title

        resultBody = body

        resultCanInsert = canInsert

    }



    fun run(

        action: String,

        style: String = "neutral",

        usePreview: Boolean = false,

        allChat: Boolean = false,

        unreadOnly: Boolean = true,

        asSheet: Boolean = false,

        canInsert: Boolean = false,

        inputText: String? = null,

        onReplies: ((List<String>) -> Unit)? = null,

    ) {

        if (busy) return

        val textIn = inputText ?: draft

        if (!usePreview && textIn.isBlank() && action !in listOf("translate", "smart_replies")) return

        scope.launch {

            busy = true

            val preview =

                if (usePreview) {

                    ChatAssistixPreview.lines(messages, allChat = allChat, unreadOnly = unreadOnly)

                } else {

                    emptyList()

                }

            val reply =

                withContext(Dispatchers.IO) {

                    api.assistixRequest(

                        token = token,

                        action = action,

                        text = if (usePreview) "" else textIn,

                        style = style,

                        previewLines = preview,

                        language = languageCode,

                        targetLanguage = languageCode,

                    )

                }

            busy = false

            when {

                reply.ok && reply.replies.isNotEmpty() -> onReplies?.invoke(reply.replies)

                reply.ok && reply.text.isNotBlank() -> {

                    if (asSheet || usePreview) {

                        val title =

                            when (action) {

                                "summarize_chat" -> UiStrings.assistixSummarizeChat

                                "summarize_unread" -> UiStrings.assistixSummarizeUnread

                                "extract_tasks" -> UiStrings.assistixExtractTasks

                                "tone_check" -> UiStrings.assistixToneCheck

                                "ask_chat" -> UiStrings.assistixAskChat

                                else -> UiStrings.assistixAi

                            }

                        showSheet(title, reply.text.trim(), canInsert)

                    } else {

                        onDraft(reply.text)

                    }

                }

                else -> toastAssistixError(reply)

            }

        }

    }



    val toolsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = toolsSheetState,
        shape = ProtoShapes.dialog,
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    UiStrings.assistixAi,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (busy) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = ProtoOrange)
                }
            }
            Row(

                Modifier

                    .fillMaxWidth()

                    .horizontalScroll(rememberScrollState())

                    .padding(horizontal = 6.dp, vertical = 3.dp),

                horizontalArrangement = Arrangement.spacedBy(6.dp),

                verticalAlignment = Alignment.CenterVertically,

            ) {

                AssistixChip(Icons.Default.Psychology, UiStrings.assistixSmartReplies, !busy) {

                    run("smart_replies", usePreview = true, unreadOnly = false, allChat = true) {

                        smartReplies = it

                    }

                }

                AssistixChip(Icons.Default.Summarize, UiStrings.assistixSummarizeChat, !busy) {

                    run("summarize_chat", usePreview = true, allChat = true, unreadOnly = false, asSheet = true)

                }

                AssistixChip(Icons.Default.Summarize, UiStrings.assistixSummarizeUnread, !busy) {

                    run("summarize_unread", usePreview = true, unreadOnly = true, asSheet = true)

                }

                AssistixChip(Icons.Default.TaskAlt, UiStrings.assistixExtractTasks, !busy) {

                    run("extract_tasks", usePreview = true, allChat = true, unreadOnly = false, asSheet = true)

                }

                AssistixChip(Icons.Default.QuestionAnswer, UiStrings.assistixAskChat, !busy) {

                    showAskDialog = true

                }

            }

                Row(

                Modifier

                    .fillMaxWidth()

                    .horizontalScroll(rememberScrollState())

                    .padding(horizontal = 6.dp, vertical = 2.dp),

                horizontalArrangement = Arrangement.spacedBy(6.dp),

                verticalAlignment = Alignment.CenterVertically,

            ) {

                AssistixChip(Icons.Default.Translate, UiStrings.translateMessage, !busy && draft.isNotBlank()) {

                    run("translate")

                }

                AssistixChip(Icons.Default.AutoFixHigh, UiStrings.assistixFixMessage, !busy && draft.isNotBlank()) {

                    run("fix_text")

                }

                AssistixChip(Icons.Default.UnfoldMore, UiStrings.assistixExpandDraft, !busy && draft.isNotBlank()) {

                    run("expand_draft")

                }

                AssistixChip(Icons.Default.Lightbulb, UiStrings.assistixToneCheck, !busy && draft.isNotBlank()) {

                    run("tone_check", asSheet = true)

                }

                AssistixChip(Icons.Default.Tune, UiStrings.assistixRewrite, !busy && draft.isNotBlank()) {

                    showStyles = !showStyles

                }

            }

            if (smartReplies.isNotEmpty()) {

                Row(

                    Modifier

                        .fillMaxWidth()

                        .horizontalScroll(rememberScrollState())

                        .padding(horizontal = 6.dp, vertical = 4.dp),

                    horizontalArrangement = Arrangement.spacedBy(6.dp),

                ) {

                    smartReplies.forEach { reply ->

                        FilterChip(

                            selected = false,

                            onClick = { onDraft(reply); smartReplies = emptyList() },

                            label = { Text(reply, maxLines = 2) },

                            colors =

                                FilterChipDefaults.filterChipColors(

                                    containerColor = ProtoOrange.copy(0.12f),

                                ),

                        )

                    }

                }

            }

            if (showStyles) {

                Row(

                    Modifier

                        .fillMaxWidth()

                        .horizontalScroll(rememberScrollState())

                        .padding(horizontal = 6.dp, vertical = 2.dp),

                    horizontalArrangement = Arrangement.spacedBy(6.dp),

                ) {

                    listOf(

                        "neutral" to UiStrings.assistixStyleNeutral,

                        "formal" to UiStrings.assistixStyleFormal,

                        "friendly" to UiStrings.assistixStyleFriendly,

                        "short" to UiStrings.assistixStyleShort,

                    ).forEach { (style, label) ->

                        FilterChip(

                            selected = false,

                            onClick = {

                                showStyles = false

                                run("rewrite_style", style = style)

                            },

                            label = { Text(label) },

                            enabled = !busy && draft.isNotBlank(),

                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.ShortText, null, Modifier.size(16.dp)) },

                            colors =

                                FilterChipDefaults.filterChipColors(

                                    selectedContainerColor = ProtoOrange.copy(0.22f),

                                ),

                        )

                    }

                }

            }

        }

    }

    if (showAskDialog) {

        AlertDialog(

            onDismissRequest = { showAskDialog = false },

            title = { Text(UiStrings.assistixAskChat) },

            text = {

                OutlinedTextField(

                    askText,

                    { askText = it },

                    placeholder = { Text(UiStrings.assistixAskHint) },

                    minLines = 2,

                    modifier = Modifier.fillMaxWidth(),

                )

            },

            confirmButton = {

                TextButton(

                    onClick = {

                        val q = askText.trim()

                        if (q.isBlank()) return@TextButton

                        showAskDialog = false

                        run("ask_chat", usePreview = true, allChat = true, unreadOnly = false, asSheet = true, inputText = q)

                        askText = ""

                    },

                ) { Text(UiStrings.send) }

            },

            dismissButton = {

                TextButton(onClick = { showAskDialog = false }) { Text(UiStrings.cancel) }

            },

        )

    }



    if (resultBody != null) {

        ModalBottomSheet(

            onDismissRequest = { resultBody = null; resultTitle = null },

            sheetState = sheetState,

            shape = ProtoShapes.dialog,

        ) {

            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {

                Row(

                    Modifier.fillMaxWidth(),

                    horizontalArrangement = Arrangement.SpaceBetween,

                    verticalAlignment = Alignment.CenterVertically,

                ) {

                    Text(resultTitle ?: UiStrings.assistixAi, fontWeight = FontWeight.Bold)

                    IconButton(onClick = { resultBody = null; resultTitle = null }) {

                        Icon(Icons.Default.Close, contentDescription = UiStrings.close)

                    }

                }

                Text(resultBody.orEmpty(), style = MaterialTheme.typography.bodyMedium)

                Row(

                    Modifier.fillMaxWidth().padding(top = 12.dp),

                    horizontalArrangement = Arrangement.spacedBy(8.dp),

                ) {

                    TextButton(

                        onClick = {

                            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

                            cm.setPrimaryClip(ClipData.newPlainText("proto_ai", resultBody))

                            Toast.makeText(ctx, UiStrings.copied, Toast.LENGTH_SHORT).show()

                        },

                    ) { Text(UiStrings.assistixCopyResult) }

                    if (resultCanInsert) {

                        TextButton(onClick = { onDraft(resultBody.orEmpty()) }) {

                            Text(UiStrings.assistixInsertDraft)

                        }

                    }

                }

                androidx.compose.foundation.layout.Spacer(Modifier.size(24.dp))

            }

        }

    }

}



@Composable

private fun AssistixChip(

    icon: androidx.compose.ui.graphics.vector.ImageVector,

    label: String,

    enabled: Boolean,

    onClick: () -> Unit,

) {

    FilterChip(

        selected = false,

        onClick = onClick,

        enabled = enabled,

        label = { Text(label, style = MaterialTheme.typography.labelMedium) },

        leadingIcon = {

            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = ProtoOrange)

        },

        colors =

            FilterChipDefaults.filterChipColors(

                containerColor = MaterialTheme.colorScheme.surface.copy(0.7f),

            ),

    )

}



/** Explain / improve a single message via Assistix (context menu). */

suspend fun assistixExplainMessage(

    api: ProtoApi,

    token: String,

    message: MsgItem,

    languageCode: String,

): String? {

    val text = message.bodyRaw.ifBlank { message.body }.trim()

    if (text.isBlank() || message.isE2e) return null

    val reply =

        withContext(Dispatchers.IO) {

            api.assistixRequest(token, "explain_message", text, language = languageCode, targetLanguage = languageCode)

        }

    return reply.text.takeIf { reply.ok && it.isNotBlank() }

}



/** Summarize selected messages. */

suspend fun assistixSummarizeSelection(

    api: ProtoApi,

    token: String,

    selected: List<MsgItem>,

    languageCode: String,

): String? {

    val preview = ChatAssistixPreview.selectionLines(selected)

    if (preview.isEmpty()) return null

    val reply =

        withContext(Dispatchers.IO) {

            api.assistixRequest(

                token,

                "summarize_chat",

                previewLines = preview,

                language = languageCode,

                targetLanguage = languageCode,

            )

        }

    return reply.text.takeIf { reply.ok && it.isNotBlank() }

}



/** AI tag suggestions for saved messages. */

suspend fun assistixSuggestTags(

    api: ProtoApi,

    token: String,

    text: String,

    languageCode: String,

): String? {

    val reply =

        withContext(Dispatchers.IO) {

            api.assistixRequest(token, "suggest_tags", text, language = languageCode, targetLanguage = languageCode)

        }

    return reply.text.takeIf { reply.ok && it.isNotBlank() }

}



/** Summarize voice transcript. */

suspend fun assistixSummarizeVoice(

    api: ProtoApi,

    token: String,

    transcript: String,

    languageCode: String,

): String? {

    val reply =

        withContext(Dispatchers.IO) {

            api.assistixRequest(token, "summarize_voice", transcript, language = languageCode, targetLanguage = languageCode)

        }

    return reply.text.takeIf { reply.ok && it.isNotBlank() }

}



@Composable
fun ChatComposerAssistixPanel(
    token: String?,
    api: ProtoApi,
    draft: String,
    onDraft: (String) -> Unit,
    messages: List<MsgItem>,
    languageCode: String,
    enabled: Boolean,
) {
    // Legacy no-op: AI tools live in [ChatComposerAssistixSheet] + [ChatComposerAssistixTrigger].
}

@Composable
fun ChatComposerAiBar(
    token: String?,
    api: ProtoApi,
    draft: String,
    onDraft: (String) -> Unit,
    messages: List<MsgItem>,
    languageCode: String,
    enabled: Boolean,
) = ChatComposerAssistixPanel(token, api, draft, onDraft, messages, languageCode, enabled)

