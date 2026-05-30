package org.assistix.proto.nativeapp.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MarkChatUnread
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import android.os.Build
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.assistix.proto.nativeapp.data.MsgItem
import org.assistix.proto.nativeapp.data.hasMediaAttachment
import org.assistix.proto.nativeapp.data.shouldShowMediaCaption
import org.assistix.proto.nativeapp.data.ProtoActiveChat
import org.assistix.proto.nativeapp.data.UserProfile
import org.assistix.proto.nativeapp.data.ProtoApi
import org.assistix.proto.nativeapp.data.ProtoMediaCompressor
import org.assistix.proto.nativeapp.data.ProtoCallGateway
import org.assistix.proto.nativeapp.data.ProtoEventHub
import org.assistix.proto.nativeapp.data.ProtoForwardState
import org.assistix.proto.nativeapp.data.MessageTranslationCache
import org.assistix.proto.nativeapp.data.ProtoMessageRepository
import org.assistix.proto.nativeapp.data.local.ProtoDatabase
import org.assistix.proto.nativeapp.data.ProtoAppPreferences
import org.assistix.proto.nativeapp.data.ProtoChatLocalPrefs
import org.assistix.proto.nativeapp.data.resolveDisplayName
import org.assistix.proto.nativeapp.data.ProtoSessionStore
import org.assistix.proto.nativeapp.data.ProtoTypingHub
import org.assistix.proto.nativeapp.data.AlbumItem
import org.assistix.proto.nativeapp.data.AlbumMeta
import org.assistix.proto.nativeapp.data.AssistixText
import org.assistix.proto.nativeapp.data.PendingOutgoingMedia
import org.assistix.proto.nativeapp.data.ProtoAudioTrim
import org.assistix.proto.nativeapp.data.ProtoMediaFiles
import org.assistix.proto.nativeapp.data.PinnedMessageInfo
import org.assistix.proto.nativeapp.data.ReplyMeta
import org.assistix.proto.nativeapp.data.SmartSavedFilter
import org.assistix.proto.nativeapp.ProtoApplication
import org.assistix.proto.nativeapp.data.isGalleryMedia
import org.assistix.proto.nativeapp.data.openChatMediaViewer

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    session: ProtoSessionStore,
    api: ProtoApi,
    messages: ProtoMessageRepository,
    calls: ProtoCallGateway,
    stt: org.assistix.proto.nativeapp.data.ProtoSttCoordinator,
    prefs: ProtoAppPreferences,
    chatLocalPrefs: ProtoChatLocalPrefs,
    draftPrefs: org.assistix.proto.nativeapp.data.ProtoDraftPrefs,
    conversationId: Int,
    title: String,
    isGroup: Boolean,
    isChannel: Boolean = false,
    isSaved: Boolean = false,
    peerUserId: Int = 0,
    onBack: () -> Unit,
    onNeedCallPermissions: (afterGranted: () -> Unit) -> Unit,
    onOpenProfile: (Int) -> Unit = {},
    onOpenGroupManage: () -> Unit = {},
    onCreatePoll: () -> Unit = {},
    onGroupCall: () -> Unit = {},
    onOpenChannelNick: (nick: String) -> Unit = {},
) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as ProtoApplication
    val online by app.network.isOnline.collectAsState(initial = app.network.checkOnline())
    var queuedOutbox by remember { mutableIntStateOf(0) }
    LaunchedEffect(online) {
        while (isActive) {
            queuedOutbox = withContext(Dispatchers.IO) { messages.pendingOutboxCount() }
            delay(2500)
        }
    }
    val scope = rememberCoroutineScope()
    val tokenFlow = session.tokenFlow.collectAsState(initial = null)
    val token = tokenFlow.value
    var aiEnabled by remember { mutableStateOf(false) }
    var chatPulseOpen by remember { mutableStateOf(false) }
    var chatPulseSeed by remember { mutableStateOf<String?>(null) }
    var assistixOpen by remember { mutableStateOf(false) }
    var aiExplainSheet by remember { mutableStateOf<String?>(null) }
    val msgs by messages.observeConversation(conversationId).collectAsState(initial = emptyList())
    var draft by remember { mutableStateOf("") }
    val savedDraft by draftPrefs.draftFor(conversationId).collectAsState(initial = "")
    var pendingDelete by remember { mutableStateOf<MsgItem?>(null) }
    val haptic = ProtoHaptics.rememberSender()
    var msgsLoading by remember(conversationId) { mutableStateOf(false) }
    var messageSelectionMode by remember { mutableStateOf(false) }
    var selectedMessageIds by remember { mutableStateOf(setOf<Long>()) }
    var showBulkDelete by remember { mutableStateOf(false) }
    var reportBulkIds by remember { mutableStateOf<Set<Long>?>(null) }
    var reactBulkIds by remember { mutableStateOf<Set<Long>?>(null) }
    var contextMenuMsg by remember { mutableStateOf<MsgItem?>(null) }

    fun exitMessageSelection() {
        messageSelectionMode = false
        selectedMessageIds = emptySet()
        showBulkDelete = false
    }

    fun enterMessageSelection(id: Long) {
        if (id <= 0L) return
        messageSelectionMode = true
        selectedMessageIds = setOf(id)
        contextMenuMsg = null
    }

    fun toggleMessageSelection(id: Long) {
        if (id <= 0L) return
        selectedMessageIds = if (id in selectedMessageIds) selectedMessageIds - id else selectedMessageIds + id
        if (selectedMessageIds.isEmpty()) messageSelectionMode = false
    }

    val allSelectableMessageIds =
        remember(msgs) {
            msgs.mapNotNull { m -> m.id.takeIf { it > 0 } }.toSet()
        }
    val selectedMessages =
        remember(msgs, selectedMessageIds) {
            msgs.filter { it.id in selectedMessageIds }
        }
    var pinnedInfo by remember { mutableStateOf<PinnedMessageInfo?>(null) }
    var pinReloadTick by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<MsgItem?>(null) }
    var editText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    var forceScrollToBottom by remember(conversationId) { mutableStateOf(false) }
    var uploading by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    var voiceRecorderHandle by remember { mutableStateOf<VoiceRecorderHandle?>(null) }
    var recordLevels by remember { mutableStateOf<List<Float>>(emptyList()) }
    var recordCancelArmed by remember { mutableStateOf(false) }
    var recordLocked by remember { mutableStateOf(false) }
    var recordTick by remember { mutableIntStateOf(0) }
    val recordElapsedMs =
        remember(recordTick, voiceRecorderHandle) {
            val h = voiceRecorderHandle ?: return@remember 0L
            (System.currentTimeMillis() - h.startedAtMs).coerceAtLeast(0L)
        }
    var pendingMedia by remember { mutableStateOf<PendingOutgoingMedia?>(null) }
    var channelInfo by remember { mutableStateOf<org.assistix.proto.nativeapp.data.ChannelHit?>(null) }
    var channelBusy by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            if (recording) {
                VoiceRecorderFactory.cancel(voiceRecorderHandle)
            }
        }
    }

    LaunchedEffect(recording, voiceRecorderHandle) {
        if (!recording || voiceRecorderHandle == null) return@LaunchedEffect
        while (recording) {
            val h = voiceRecorderHandle ?: break
            recordLevels = (recordLevels + VoiceRecorderFactory.pollLevel(h)).takeLast(80)
            recordTick++
            delay(45)
        }
    }

    LaunchedEffect(conversationId, isChannel, token) {
        if (!isChannel || token.isNullOrBlank()) {
            channelInfo = null
            return@LaunchedEffect
        }
        channelInfo = withContext(Dispatchers.IO) { api.channelByConversation(token, conversationId) }
    }

    val channelSubscribed = !isChannel || channelInfo?.subscribed == true
    val channelCanPost = isChannel && channelInfo?.canPost == true

    fun clearPendingMedia() {
        pendingMedia?.discard()
        pendingMedia = null
    }

    suspend fun sendConfirmedMedia(confirmed: PendingOutgoingMedia, caption: String) {
        val t = token ?: return
        uploading = true
        try {
            when (confirmed) {
                is PendingOutgoingMedia.Single -> {
                    val prepared =
                        withContext(Dispatchers.IO) {
                            ProtoMediaCompressor.prepareUploadFile(ctx, confirmed.file, confirmed.mime)
                        }
                    if (prepared.first.length() > ProtoMediaCompressor.MAX_UPLOAD_BYTES) {
                        Toast.makeText(ctx, UiStrings.uploadFailed, Toast.LENGTH_LONG).show()
                        return
                    }
                    val uploadId = withContext(Dispatchers.IO) { api.uploadFile(t, prepared.first, prepared.second) }
                    if (uploadId != null) {
                        withContext(Dispatchers.IO) {
                            if (isChannel && channelCanPost && confirmed.mime.startsWith("image/")) {
                                api.publishChannelPost(t, conversationId, caption, uploadId)
                            } else {
                                messages.sendMedia(t, conversationId, uploadId, confirmed.mime, confirmed.displayName, caption)
                            }
                        }
                        if (caption.isNotBlank()) draft = ""
                        haptic(HapticKind.Send)
                        clearPendingMedia()
                    } else {
                        haptic(HapticKind.Error)
                        Toast.makeText(ctx, UiStrings.uploadFailed, Toast.LENGTH_SHORT).show()
                    }
                }
                is PendingOutgoingMedia.Album -> {
                    val items = mutableListOf<AlbumItem>()
                    for (f in confirmed.files) {
                        val prepared = withContext(Dispatchers.IO) { ProtoMediaCompressor.prepareUploadFile(ctx, f, confirmed.mime) }
                        if (prepared.first.length() > ProtoMediaCompressor.MAX_UPLOAD_BYTES) continue
                        val uploadId = withContext(Dispatchers.IO) { api.uploadFile(t, prepared.first, prepared.second) } ?: continue
                        items.add(AlbumItem(uploadId, confirmed.mime, f.name))
                    }
                    if (items.size >= 2) {
                        withContext(Dispatchers.IO) { messages.sendAlbum(t, conversationId, items, caption) }
                        if (caption.isNotBlank()) draft = ""
                        haptic(HapticKind.Send)
                        clearPendingMedia()
                    } else {
                        Toast.makeText(ctx, UiStrings.albumNeedTwo, Toast.LENGTH_SHORT).show()
                    }
                }
                is PendingOutgoingMedia.Voice -> {
                    val trimmed =
                        withContext(Dispatchers.IO) {
                            ProtoAudioTrim.trimIfNeeded(confirmed.file, confirmed.trimStartMs, confirmed.trimEndMs)
                        }
                    val uploadId = withContext(Dispatchers.IO) { api.uploadFile(t, trimmed, "audio/mp4") }
                    if (uploadId != null) {
                        withContext(Dispatchers.IO) {
                            messages.sendMedia(t, conversationId, uploadId, "audio/mp4", "voice.m4a", "")
                        }
                        haptic(HapticKind.Send)
                        clearPendingMedia()
                        if (trimmed != confirmed.file) trimmed.delete()
                    } else {
                        haptic(HapticKind.Error)
                        Toast.makeText(ctx, UiStrings.uploadFailed, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } catch (e: Exception) {
            haptic(HapticKind.Error)
            Toast.makeText(ctx, e.message ?: UiStrings.genericError, Toast.LENGTH_SHORT).show()
        } finally {
            uploading = false
        }
    }
    var showReact by remember { mutableStateOf(false) }
    var reactTarget by remember { mutableStateOf<MsgItem?>(null) }
    var replyTarget by remember { mutableStateOf<ReplyMeta?>(null) }
    val languageCode by prefs.languageCodeFlow.collectAsState(initial = "en")
    val autoTranslate by prefs.autoTranslateChats.collectAsState(initial = false)
    val translationState = rememberChatTranslationState()
    val translationCache = remember { MessageTranslationCache(ProtoDatabase.get(ctx).dao()) }
    HydrateTranslationsEffect(msgs, languageCode, translationState, translationCache)
    var pendingLink by remember { mutableStateOf<String?>(null) }
    var chatSearchOpen by remember { mutableStateOf(false) }
    var chatSearchQuery by remember { mutableStateOf("") }
    var chatSearchFilter by remember { mutableStateOf(ChatSearchFilter.All) }
    var highlightedSearchMessageId by remember { mutableStateOf<Long?>(null) }
    var voiceTranscriptMap by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    LaunchedEffect(conversationId) {
        voiceTranscriptMap = org.assistix.proto.nativeapp.data.ProtoVoiceTranscriptStore.transcriptMap(ctx)
    }
    LaunchedEffect(Unit) {
        app.sttQueue.transcriptSaved.collect {
            voiceTranscriptMap = org.assistix.proto.nativeapp.data.ProtoVoiceTranscriptStore.transcriptMap(ctx)
        }
    }
    LaunchedEffect(msgs, token, conversationId, languageCode) {
        val t = token ?: return@LaunchedEffect
        app.sttQueue.enqueuePendingInChat(t, conversationId, msgs, languageCode)
    }
    var myLastReadMessageId by remember { mutableStateOf(0L) }
    var showStarredOnly by remember { mutableStateOf(false) }
    val starredIds by chatLocalPrefs.starredMessageIds(conversationId).collectAsState(initial = emptySet())
    val chatNote by chatLocalPrefs.noteFor(conversationId).collectAsState(initial = "")
    var showNoteDialog by remember { mutableStateOf(false) }
    var noteDraft by remember { mutableStateOf("") }
    val messageCount = remember(msgs) { msgs.count { it.id > 0L } }
    val rows = remember(msgs, languageCode, myLastReadMessageId) { buildChatRows(msgs, languageCode, myLastReadMessageId) }
    val filteredRows =
        remember(rows, chatSearchQuery, chatSearchFilter, voiceTranscriptMap) {
            val q = chatSearchQuery.trim()
            if (q.isEmpty() && chatSearchFilter == ChatSearchFilter.All) {
                rows
            } else {
                rows.filter { row ->
                    val m = row.msg ?: return@filter row.dayLabel != null || row.bannerLabel != null
                    val tr =
                        org.assistix.proto.nativeapp.data.ProtoVoiceTranscriptStore.transcriptForMessage(
                            voiceTranscriptMap,
                            conversationId,
                            m,
                        )
                    ChatMessageSearch.messageMatches(m, q, chatSearchFilter, tr)
                }
            }
        }
    val searchDayLabels = remember(rows) { rows.mapNotNull { it.dayLabel }.distinct() }
    var searchHitIndex by remember { mutableIntStateOf(0) }
    var smartSavedFilter by remember { mutableStateOf(SmartSavedFilter.All) }
    var savedMetaTick by remember { mutableIntStateOf(0) }
    var savedTags by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(isSaved, savedMetaTick) {
        if (isSaved) savedTags = org.assistix.proto.nativeapp.data.ProtoSavedMetaStore.allTags(ctx)
    }
    val displayRows =
        remember(filteredRows, isSaved, smartSavedFilter, showStarredOnly, starredIds) {
            var base =
                if (!isSaved || smartSavedFilter == SmartSavedFilter.All) {
                    filteredRows
                } else {
                    filteredRows.filter { row ->
                        val m = row.msg ?: return@filter row.dayLabel != null || row.bannerLabel != null
                        SmartSavedFilter.matches(m, smartSavedFilter)
                    }
                }
            if (showStarredOnly) {
                base =
                    base.filter { row ->
                        val m = row.msg ?: return@filter row.bannerLabel != null
                        m.id in starredIds
                    }
            }
            base
        }
    val starredHits =
        remember(displayRows, starredIds) {
            displayRows.filter { row -> row.msg?.id?.let { it in starredIds } == true }
        }
    var starredHitIndex by remember(conversationId) { mutableIntStateOf(-1) }
    LaunchedEffect(starredIds.size, conversationId) {
        starredHitIndex = -1
    }
    val searchHits = remember(displayRows, chatSearchQuery) { displayRows.filter { it.msg != null } }
    LaunchedEffect(chatSearchQuery) { searchHitIndex = 0 }
    LaunchedEffect(searchHitIndex, searchHits.size) {
        if (searchHits.isEmpty()) {
            highlightedSearchMessageId = null
            return@LaunchedEffect
        }
        val idx = searchHitIndex.coerceIn(0, searchHits.lastIndex)
        val hit = searchHits[idx]
        highlightedSearchMessageId = hit.msg?.id?.takeIf { it > 0 }
        val rowIndex = displayRows.indexOf(hit)
        if (rowIndex >= 0) listState.animateScrollToItem(rowIndex)
    }
    BackHandler(chatSearchOpen) {
        chatSearchOpen = false
        chatSearchQuery = ""
        chatSearchFilter = ChatSearchFilter.All
        highlightedSearchMessageId = null
    }
    var highlightedReplyMessageId by remember { mutableStateOf<Long?>(null) }
    var replyHighlightPulse by remember { mutableIntStateOf(0) }
    LaunchedEffect(highlightedReplyMessageId) {
        val targetId = highlightedReplyMessageId ?: return@LaunchedEffect
        delay(2400)
        if (highlightedReplyMessageId == targetId) highlightedReplyMessageId = null
    }
    fun jumpToReplyMessage(messageId: Long) {
        if (messageId <= 0L) return
        val rowIndex = displayRows.indexOfFirst { it.msg?.id == messageId }
        if (rowIndex < 0) {
            Toast.makeText(ctx, UiStrings.replyJumpMissing, Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch {
            listState.animateScrollToItem(rowIndex)
            highlightedReplyMessageId = messageId
            replyHighlightPulse++
        }
    }
    fun jumpToStarredAt(index: Int) {
        if (starredHits.isEmpty()) return
        starredHitIndex = index.coerceIn(0, starredHits.lastIndex)
        val hit = starredHits[starredHitIndex]
        val messageId = hit.msg?.id ?: return
        scope.launch {
            val rowIndex = displayRows.indexOf(hit)
            if (rowIndex >= 0) listState.animateScrollToItem(rowIndex)
            highlightedReplyMessageId = messageId
            replyHighlightPulse++
        }
    }
    fun jumpToNextStarred() {
        if (starredHits.isEmpty()) return
        jumpToStarredAt((starredHitIndex + 1).let { if (it < 0) 0 else it % starredHits.size })
    }
    fun jumpToPrevStarred() {
        if (starredHits.isEmpty()) return
        val next =
            if (starredHitIndex <= 0) {
                starredHits.lastIndex
            } else {
                starredHitIndex - 1
            }
        jumpToStarredAt(next)
    }
    val reactEmojis = listOf("❤️", "👍", "😂", "😮", "😢", "🔥")
    var peerProfile by remember { mutableStateOf<UserProfile?>(null) }
    var showAccentPicker by remember { mutableStateOf(false) }
    var chatMenuExpanded by remember { mutableStateOf(false) }
    var confirmClearHistory by remember { mutableStateOf(false) }
    var confirmDeleteChat by remember { mutableStateOf(false) }
    var reportMsgTarget by remember { mutableStateOf<MsgItem?>(null) }
    var showReportUser by remember { mutableStateOf(false) }
    val reportableMessages =
        remember(msgs, peerUserId, isGroup) {
            msgs.filter { m ->
                if (m.id <= 0L || m.mine) return@filter false
                !isGroup || peerUserId <= 0 || m.senderId == peerUserId
            }.takeLast(100)
        }
    val chatAccentId by chatLocalPrefs.accentFor(conversationId).collectAsState(initial = 0)
    val showReadReceipts by prefs.showReadReceipts.collectAsState(initial = true)
    val showTypingPref by prefs.showTyping.collectAsState(initial = true)
    val showLinkPreviews by prefs.linkPreviewsInChat.collectAsState(initial = true)
    val sendOnEnter by prefs.sendOnEnter.collectAsState(initial = false)
    val reduceMotion by prefs.reduceMotionEnabled.collectAsState(initial = false)
    var dissolvingMessageIds by remember { mutableStateOf(setOf<Long>()) }
    var pendingDeleteJobs by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }
    var myUserId by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { myUserId = session.userId() }

    LaunchedEffect(peerUserId, token) {
        val t = token ?: return@LaunchedEffect
        peerProfile =
            if (peerUserId > 0) {
                withContext(Dispatchers.IO) { api.userById(t, peerUserId) }
            } else {
                null
            }
    }

    fun exportChatText() {
        if (msgs.isEmpty()) {
            Toast.makeText(ctx, UiStrings.exportChatEmpty, Toast.LENGTH_SHORT).show()
            return
        }
        val peerLabel =
            peerProfile?.let { resolveDisplayName(it.displayName, it.nick) }?.takeIf { it.isNotBlank() }
                ?: title
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
        val sb = StringBuilder()
        sb.append(title).append('\n').append("---\n")
        msgs.filter { it.id > 0L }.forEach { m ->
            val who =
                when {
                    m.mine -> UiStrings.exportChatMeLabel
                    m.senderName.isNotBlank() -> m.senderName
                    else -> peerLabel
                }
            val body = displayBody(m, translationState).trim()
            if (body.isEmpty()) return@forEach
            sb.append('[')
                .append(fmt.format(java.util.Date(m.createdAt * 1000L)))
                .append("] ")
                .append(who)
                .append(": ")
                .append(body)
                .append('\n')
        }
        val intent =
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, sb.toString())
                putExtra(Intent.EXTRA_SUBJECT, UiStrings.exportChatSubjectFmt(title))
            }
        runCatching {
            ctx.startActivity(Intent.createChooser(intent, UiStrings.exportChat))
        }.onFailure {
            Toast.makeText(ctx, UiStrings.shareFailed, Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(conversationId) {
        exitMessageSelection()
        dissolvingMessageIds = emptySet()
        pendingDeleteJobs = emptyMap()
    }

    fun queueMessageDelete(messageId: Long, scope: String) {
        if (messageId <= 0L || messageId in dissolvingMessageIds) return
        dissolvingMessageIds = dissolvingMessageIds + messageId
        pendingDeleteJobs = pendingDeleteJobs + (messageId to scope)
    }

    fun finishDissolveDelete(messageId: Long) {
        val job = pendingDeleteJobs[messageId] ?: return
        val t = token ?: return
        scope.launch {
            val ok = withContext(Dispatchers.IO) { messages.deleteMessage(t, conversationId, messageId, job) }
            dissolvingMessageIds = dissolvingMessageIds - messageId
            pendingDeleteJobs = pendingDeleteJobs - messageId
            if (!ok) haptic(HapticKind.Error)
        }
    }

    BackHandler(messageSelectionMode && !chatSearchOpen) {
        exitMessageSelection()
    }

    LaunchedEffect(conversationId) {
        draftPrefs.ensureRecovered()
        draft = savedDraft
    }
    LaunchedEffect(draft, conversationId) {
        if (draft == savedDraft) return@LaunchedEffect
        draftPrefs.setDraft(conversationId, draft)
    }
    LaunchedEffect(draft, conversationId, token, online) {
        if (!online) return@LaunchedEffect
        val t = token ?: return@LaunchedEffect
        if (draft == savedDraft) return@LaunchedEffect
        delay(600)
        org.assistix.proto.nativeapp.data.ProtoClientPrefsSync.pushDrafts(t, api, draftPrefs)
    }
    androidx.compose.runtime.DisposableEffect(conversationId) {
        onDispose {
            scope.launch {
                draftPrefs.setDraft(conversationId, draft)
            }
        }
    }
    LaunchedEffect(conversationId, token) {
        val t = token ?: return@LaunchedEffect
        val list = withContext(Dispatchers.IO) { api.conversations(t) }
        myLastReadMessageId = list.find { it.id == conversationId }?.myLastReadMessageId ?: 0L
    }
    LaunchedEffect(conversationId, token, pinReloadTick, ProtoEventHub.tick) {
        val t = token ?: return@LaunchedEffect
        pinnedInfo = withContext(Dispatchers.IO) { api.fetchPinnedMessage(t, conversationId) }
    }

    val pickMedia =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            val copied = ProtoMediaFiles.copyToCache(ctx, uri) ?: run {
                Toast.makeText(ctx, UiStrings.uploadFailed, Toast.LENGTH_SHORT).show()
                return@rememberLauncherForActivityResult
            }
            clearPendingMedia()
            pendingMedia =
                PendingOutgoingMedia.Single(
                    file = copied.first,
                    mime = copied.second,
                    displayName = ProtoMediaFiles.displayName(copied.second),
                )
        }

    val pickAlbum =
        rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
            if (uris.isEmpty()) return@rememberLauncherForActivityResult
            scope.launch {
                val files = mutableListOf<File>()
                for (uri in uris.take(4)) {
                    val copied = ProtoMediaFiles.copyToCache(ctx, uri) ?: continue
                    if (!copied.second.startsWith("image/")) continue
                    files.add(copied.first)
                }
                when {
                    files.isEmpty() -> Toast.makeText(ctx, UiStrings.uploadFailed, Toast.LENGTH_SHORT).show()
                    files.size == 1 -> {
                        clearPendingMedia()
                        pendingMedia =
                            PendingOutgoingMedia.Single(
                                files.first(),
                                "image/jpeg",
                                ProtoMediaFiles.displayName("image/jpeg"),
                            )
                    }
                    else -> {
                        clearPendingMedia()
                        pendingMedia = PendingOutgoingMedia.Album(files)
                    }
                }
            }
        }

    LaunchedEffect(conversationId) {
        clearPendingMedia()
    }

    DisposableEffect(conversationId) {
        ProtoActiveChat.conversationId = conversationId
        onDispose {
            clearPendingMedia()
            if (ProtoActiveChat.conversationId == conversationId) ProtoActiveChat.conversationId = 0
        }
    }

    LaunchedEffect(conversationId, token, showReadReceipts, online) {
        val t = token ?: return@LaunchedEffect
        if (!online) {
            msgsLoading = false
            return@LaunchedEffect
        }
        if (msgs.isEmpty()) msgsLoading = true
        withContext(Dispatchers.IO) {
            messages.refreshFromServer(t, conversationId, myUserId, showReadReceipts)
            if (online) app.cachePrefetch.warmConversation(t, conversationId, myUserId)
        }
        msgsLoading = false
    }

    LaunchedEffect(ProtoEventHub.tick, conversationId, token, showReadReceipts, online) {
        val t = token ?: return@LaunchedEffect
        if (!online) return@LaunchedEffect
        withContext(Dispatchers.IO) { messages.refreshFromServer(t, conversationId, session.userId(), showReadReceipts) }
    }

    LaunchedEffect(conversationId, token, showTypingPref) {
        val t = token ?: return@LaunchedEffect
        if (!showTypingPref) {
            ProtoTypingHub.clear(conversationId)
            return@LaunchedEffect
        }
        while (isActive) {
            val ps = withContext(Dispatchers.IO) { api.presenceState(t, conversationId) }
            ProtoTypingHub.update(conversationId, ps.typingUserIds, ps.recordingUserIds)
            delay(2000)
        }
    }

    DisposableEffect(conversationId) {
        onDispose { ProtoTypingHub.clear(conversationId) }
    }

    val showScrollDown by remember(displayRows, listState) {
        derivedStateOf {
            if (displayRows.isEmpty()) return@derivedStateOf false
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible < info.totalItemsCount - 2
        }
    }

    val unreadJumpIndex =
        remember(displayRows, myLastReadMessageId) {
            displayRows.indexOfFirst { row ->
                val m = row.msg ?: return@indexOfFirst false
                m.id > myLastReadMessageId && !m.mine
            }
        }
    val showJumpUnread by remember(unreadJumpIndex, listState) {
        derivedStateOf {
            if (unreadJumpIndex < 0) return@derivedStateOf false
            val first = listState.firstVisibleItemIndex
            first > unreadJumpIndex + 1
        }
    }
    val showScrollTop by remember(listState) {
        derivedStateOf { listState.firstVisibleItemIndex > 14 }
    }

    var initialScrollDone by remember(conversationId) { mutableStateOf(false) }

    LaunchedEffect(conversationId) {
        initialScrollDone = false
    }

    LaunchedEffect(displayRows.size, conversationId) {
        if (displayRows.isEmpty()) return@LaunchedEffect
        if (!initialScrollDone) {
            listState.scrollToItem(displayRows.lastIndex)
            initialScrollDone = true
        }
    }

    LaunchedEffect(displayRows.lastIndex, forceScrollToBottom) {
        if (!initialScrollDone || displayRows.isEmpty()) return@LaunchedEffect
        if (forceScrollToBottom) {
            listState.animateScrollToItem(displayRows.lastIndex)
            forceScrollToBottom = false
            return@LaunchedEffect
        }
        val info = listState.layoutInfo
        if (info.totalItemsCount == 0) return@LaunchedEffect
        val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
        if (lastVisible >= info.totalItemsCount - 3) {
            listState.animateScrollToItem(displayRows.lastIndex)
        }
    }

    if (showAccentPicker) {
        AlertDialog(
            onDismissRequest = { showAccentPicker = false },
            title = { Text(UiStrings.chatAccent, fontWeight = FontWeight.Bold) },
            text = {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf(0 to UiStrings.chatAccentDefault, 1 to "Warm", 2 to "Cool", 3 to "Glow").forEach { (id, label) ->
                        TextButton(
                            onClick = {
                                scope.launch { chatLocalPrefs.setAccent(conversationId, id) }
                                showAccentPicker = false
                            },
                        ) {
                            Text(label, fontWeight = if (chatAccentId == id) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAccentPicker = false }) { Text(UiStrings.close) } },
        )
    }

    LaunchedEffect(token) {
        aiEnabled =
            if (token.isNullOrBlank()) {
                false
            } else {
                withContext(Dispatchers.IO) { api.assistixCatalog(token)?.configured == true }
            }
    }

    LaunchedEffect(draft, token, showTypingPref) {
        val t = token ?: return@LaunchedEffect
        if (!showTypingPref) return@LaunchedEffect
        if (draft.isNotBlank()) {
            withContext(Dispatchers.IO) { api.setTyping(t, conversationId, true) }
            delay(1200)
            withContext(Dispatchers.IO) { api.setTyping(t, conversationId, false) }
        }
    }

    if (editing != null) {
        AlertDialog(
            onDismissRequest = { editing = null },
            shape = ProtoShapes.dialog,
            title = { Text(UiStrings.edit) },
            text = {
                OutlinedTextField(editText, { editText = it }, modifier = Modifier.fillMaxWidth(), maxLines = 5, shape = ProtoShapes.field)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val t = token ?: return@TextButton
                        val mid = editing?.id ?: return@TextButton
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) { messages.editMessage(t, conversationId, mid, editText.trim()) }
                            if (!ok) Toast.makeText(ctx, UiStrings.editFailed, Toast.LENGTH_SHORT).show()
                            editing = null
                        }
                    },
                ) { Text(UiStrings.save) }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text(UiStrings.cancel) } },
        )
    }

    if (showReact && reactTarget != null) {
        val m = reactTarget!!
        val bulkReact = reactBulkIds
        AlertDialog(
            onDismissRequest = { showReact = false; reactBulkIds = null },
            shape = ProtoShapes.dialog,
            title = { Text(UiStrings.react) },
            text = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    reactEmojis.forEach { em ->
                        TextButton(
                            onClick = {
                                showReact = false
                                val t = token ?: return@TextButton
                                val targets =
                                    if (!bulkReact.isNullOrEmpty()) {
                                        msgs.filter { it.id in bulkReact && it.id > 0 }
                                    } else if (m.id > 0) {
                                        listOf(m)
                                    } else {
                                        emptyList()
                                    }
                                reactBulkIds = null
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        targets.forEach { target ->
                                            messages.toggleReaction(t, conversationId, session.userId(), target.id, em)
                                        }
                                    }
                                }
                                exitMessageSelection()
                            },
                        ) { Text(em, fontSize = 22.sp) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showReact = false }) { Text(UiStrings.close) } },
        )
    }

    pendingDelete?.let { target ->
        MessageDeleteSheet(
            message = target,
            canDeleteForEveryone = target.mine && target.id > 0,
            onDismiss = { pendingDelete = null },
            onDeleteForMe = {
                val mid = target.id
                pendingDelete = null
                if (mid > 0) queueMessageDelete(mid, "self")
            },
            onDeleteForEveryone =
                if (target.mine && target.id > 0) {
                    {
                        val mid = target.id
                        pendingDelete = null
                        queueMessageDelete(mid, "all")
                    }
                } else {
                    null
                },
        )
    }

    pendingLink?.let { link ->
        OpenLinkConfirmDialog(
            url = link,
            onDismiss = { pendingLink = null },
            onConfirm = {
                openUrlInBrowser(ctx, link)
                pendingLink = null
            },
        )
    }

    AutoTranslateEffect(autoTranslate, token, api, msgs, languageCode, translationState, translationCache)

    if (showBulkDelete && selectedMessageIds.isNotEmpty()) {
        val n = selectedMessageIds.size
        val allMine = selectedMessages.isNotEmpty() && selectedMessages.all { it.mine }
        BulkMessageDeleteSheet(
            count = n,
            canDeleteForEveryone = allMine,
            onDismiss = { showBulkDelete = false },
            onDeleteForMe = {
                val ids = selectedMessageIds.toList()
                showBulkDelete = false
                exitMessageSelection()
                ids.forEach { queueMessageDelete(it, "self") }
            },
            onDeleteForEveryone =
                if (allMine) {
                    {
                        val ids = selectedMessageIds.toList()
                        showBulkDelete = false
                        exitMessageSelection()
                        ids.forEach { queueMessageDelete(it, "all") }
                    }
                } else {
                    null
                },
        )
    }

    reportBulkIds?.let { bulkIds ->
        val targetUid =
            when {
                isGroup -> selectedMessages.firstOrNull { !it.mine }?.senderId ?: peerUserId
                else -> peerUserId
            }
        if (targetUid > 0 && bulkIds.isNotEmpty()) {
            ReportMessagesBulkDialog(
                targetUserId = targetUid,
                conversationId = conversationId,
                messageIds = bulkIds,
                api = api,
                token = token,
                onDismiss = { reportBulkIds = null },
                onDone = { msg ->
                    Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
                    reportBulkIds = null
                    exitMessageSelection()
                },
            )
        } else {
            reportBulkIds = null
        }
    }

    reportMsgTarget?.let { target ->
        ReportMessageDialog(
            messageId = target.id,
            conversationId = conversationId,
            targetUserId = if (isGroup) target.senderId else peerUserId,
            api = api,
            token = token,
            onDismiss = { reportMsgTarget = null },
            onDone = { msg -> Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show() },
        )
    }

    if (showReportUser && !isSaved && peerUserId > 0) {
        ReportUserDialog(
            targetUserId = peerUserId,
            api = api,
            token = token,
            conversationId = conversationId,
            selectableMessages = reportableMessages,
            onDismiss = { showReportUser = false },
            onDone = { msg -> Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show() },
        )
    }

    if (confirmClearHistory) {
        AlertDialog(
            onDismissRequest = { confirmClearHistory = false },
            title = { Text(UiStrings.confirmClearHistoryTitle) },
            text = { Text(UiStrings.confirmClearHistoryBody) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClearHistory = false
                        val t = token ?: return@TextButton
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) { api.clearChatHistory(t, conversationId) }
                            if (ok) {
                                messages.clearConversationLocal(conversationId)
                                haptic(HapticKind.Send)
                            } else {
                                haptic(HapticKind.Error)
                            }
                        }
                    },
                ) { Text(UiStrings.confirm) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearHistory = false }) { Text(UiStrings.cancel) }
            },
        )
    }

    if (showNoteDialog) {
        AlertDialog(
            onDismissRequest = { showNoteDialog = false },
            title = { Text(UiStrings.editChatNote) },
            text = {
                OutlinedTextField(
                    value = noteDraft,
                    onValueChange = { noteDraft = it },
                    placeholder = { Text(UiStrings.chatNotePlaceholder) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showNoteDialog = false
                        scope.launch { chatLocalPrefs.setNote(conversationId, noteDraft) }
                    },
                ) { Text(UiStrings.save) }
            },
            dismissButton = {
                TextButton(onClick = { showNoteDialog = false }) { Text(UiStrings.cancel) }
            },
        )
    }

    if (confirmDeleteChat) {
        AlertDialog(
            onDismissRequest = { confirmDeleteChat = false },
            title = { Text(UiStrings.confirmDeleteChatTitle) },
            text = { Text(UiStrings.confirmDeleteChatBody) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDeleteChat = false
                        val t = token ?: return@TextButton
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) { api.hideChat(t, conversationId) }
                            if (ok) {
                                messages.clearConversationLocal(conversationId)
                                onBack()
                            } else {
                                haptic(HapticKind.Error)
                            }
                        }
                    },
                ) { Text(UiStrings.delete, color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteChat = false }) { Text(UiStrings.cancel) }
            },
        )
    }

    contextMenuMsg?.let { m ->
        val isPinned = pinnedInfo?.messageId == m.id
        MessageContextSheet(
            msg = m,
            onDismiss = { contextMenuMsg = null },
            actions =
                buildList {
                    add(
                        MessageContextAction(MessageContextIcons.Reply, UiStrings.reply) {
                            replyTarget =
                                ReplyMeta(
                                    messageId = m.id.takeIf { it > 0 } ?: 0,
                                    preview = m.bodyRaw.take(120).ifBlank { m.body.take(120) },
                                    senderId = m.senderId,
                                )
                        },
                    )
                    if (m.bodyRaw.isNotBlank() || m.body.isNotBlank()) {
                        add(
                            MessageContextAction(MessageContextIcons.Copy, UiStrings.copy) {
                                val clip = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clip.setPrimaryClip(ClipData.newPlainText("proto", m.bodyRaw.ifBlank { m.body }))
                                Toast.makeText(ctx, UiStrings.copied, Toast.LENGTH_SHORT).show()
                            },
                        )
                        if (m.createdAt > 0L) {
                            add(
                                MessageContextAction(MessageContextIcons.Copy, UiStrings.copyTimestamp) {
                                    val clip = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clip.setPrimaryClip(
                                        ClipData.newPlainText(
                                            "proto",
                                            formatDateTime(m.createdAt * 1000L),
                                        ),
                                    )
                                    Toast.makeText(ctx, UiStrings.copied, Toast.LENGTH_SHORT).show()
                                },
                            )
                        }
                        add(
                            MessageContextAction(MessageContextIcons.Share, UiStrings.shareMessage) {
                                val text = m.bodyRaw.ifBlank { m.body }
                                val intent =
                                    Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, text)
                                    }
                                ctx.startActivity(Intent.createChooser(intent, UiStrings.shareMessage))
                            },
                        )
                    }
                    if (m.id > 0) {
                        val starred = m.id in starredIds
                        add(
                            MessageContextAction(
                                if (starred) MessageContextIcons.Star else MessageContextIcons.StarOutline,
                                if (starred) UiStrings.unstarMessage else UiStrings.starMessage,
                            ) {
                                scope.launch { chatLocalPrefs.toggleStar(conversationId, m.id) }
                            },
                        )
                    }
                    add(MessageContextAction(MessageContextIcons.React, UiStrings.react) { reactTarget = m; showReact = true })
                    add(
                        MessageContextAction(MessageContextIcons.Forward, UiStrings.forward) {
                            ProtoForwardState.start(m, title)
                            Toast.makeText(ctx, UiStrings.forwardPickChat, Toast.LENGTH_SHORT).show()
                            onBack()
                        },
                    )
                    if (m.id > 0) {
                        add(
                            MessageContextAction(MessageContextIcons.Pin, if (isPinned) UiStrings.unpinMessage else UiStrings.pinMessage) {
                                val t = token ?: return@MessageContextAction
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        if (isPinned) api.unpinMessage(t, conversationId) else api.pinMessage(t, conversationId, m.id)
                                    }
                                    pinReloadTick++
                                }
                            },
                        )
                    }
                    if (!isSaved) {
                        add(
                            MessageContextAction(MessageContextIcons.Save, UiStrings.saveToSaved) {
                                val t = token ?: return@MessageContextAction
                                scope.launch {
                                    val ok = withContext(Dispatchers.IO) { messages.saveToSaved(t, m, title, m.senderId) }
                                    Toast.makeText(ctx, if (ok) UiStrings.savedTo else UiStrings.forwardFailed, Toast.LENGTH_SHORT).show()
                                }
                            },
                        )
                    }
                    if (m.mine && m.messageType == "text" && !m.hasMediaAttachment()) {
                        add(MessageContextAction(MessageContextIcons.Edit, UiStrings.edit) { editing = m; editText = m.bodyRaw })
                    }
                    if (!m.isE2e && m.messageType == "text" && m.body.isNotBlank()) {
                        add(
                            MessageContextAction(MessageContextIcons.Translate, UiStrings.translateMessage) {
                                val t = token ?: return@MessageContextAction
                                scope.launch {
                                    val ok = translateMessage(api, t, m, languageCode, translationState, translationCache)
                                    Toast.makeText(ctx, if (ok) UiStrings.translateDone else UiStrings.assistixError, Toast.LENGTH_SHORT).show()
                                }
                            },
                        )
                        if (aiEnabled) {
                            add(
                                MessageContextAction(MessageContextIcons.Explain, UiStrings.assistixExplain) {
                                    val t = token ?: return@MessageContextAction
                                    scope.launch {
                                        val explained = assistixExplainMessage(api, t, m, languageCode)
                                        if (explained != null) {
                                            aiExplainSheet = explained
                                        } else {
                                            Toast.makeText(ctx, UiStrings.assistixError, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                            )
                        }
                    }
                    add(
                        MessageContextAction(MessageContextIcons.Select, UiStrings.msgContextSelect) {
                            enterMessageSelection(m.id)
                        },
                    )
                    if (!m.mine && m.id > 0 && (peerUserId > 0 || (isGroup && m.senderId > 0))) {
                        add(
                            MessageContextAction(MessageContextIcons.Report, UiStrings.reportMessageAction) {
                                reportMsgTarget = m
                            },
                        )
                    }
                    if (m.id > 0) {
                        add(
                            MessageContextAction(MessageContextIcons.Delete, UiStrings.delete, danger = true) {
                                pendingDelete = m
                            },
                        )
                    }
                },
        )
    }

    aiExplainSheet?.let { body ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { aiExplainSheet = null },
            sheetState = sheetState,
            shape = ProtoShapes.dialog,
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text(UiStrings.assistixAi, fontWeight = FontWeight.Bold)
                Spacer(Modifier.size(8.dp))
                Text(body, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.size(24.dp))
            }
        }
    }

    Scaffold(
        topBar = {
            if (messageSelectionMode) {
                val allSelected =
                    selectedMessageIds.size >= allSelectableMessageIds.size &&
                        allSelectableMessageIds.isNotEmpty()
                MessageSelectionTopBar(
                    selectedCount = selectedMessageIds.size,
                    allSelected = allSelected,
                    onClose = { exitMessageSelection() },
                    onToggleSelectAll = {
                        if (allSelected) {
                            exitMessageSelection()
                        } else {
                            selectedMessageIds = allSelectableMessageIds
                        }
                    },
                )
            } else {
            TopAppBar(
                title = {
                    val peer = peerProfile
                    val displayTitle =
                        if (isSaved) {
                            UiStrings.savedMessages
                        } else {
                            peer?.displayName?.trim()?.takeIf { it.isNotBlank() } ?: title
                        }
                    val emoji = if (isSaved) "" else peer?.statusEmoji ?: ""
                    val openProfile = !isGroup && !isSaved && peerUserId > 0
                    val titleModifier =
                        Modifier
                            .fillMaxWidth()
                            .then(
                                if (openProfile) {
                                    Modifier.clickable { onOpenProfile(peerUserId) }
                                } else {
                                    Modifier
                                },
                            )
                    if (openProfile) {
                        Row(
                            modifier = titleModifier,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ProtoAvatar(
                                uploadId = peer?.avatarUploadId,
                                displayName = displayTitle,
                                size = 40.dp,
                                api = api,
                                token = token,
                            )
                            Spacer(Modifier.size(10.dp))
                            Column(Modifier.weight(1f)) {
                                DisplayNameWithEmoji(displayName = displayTitle, statusEmoji = emoji, maxLines = 1)
                                when {
                                    showTypingPref && ProtoTypingHub.conversationId == conversationId && ProtoTypingHub.recordingUserIds.isNotEmpty() ->
                                        Text(
                                            UiStrings.someoneRecording,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    showTypingPref && ProtoTypingHub.conversationId == conversationId && ProtoTypingHub.userIds.isNotEmpty() ->
                                        Text(
                                            UiStrings.someoneTyping,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    draft.isNotBlank() ->
                                        Text(
                                            UiStrings.composerDraftSaved,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = ProtoOrange,
                                            maxLines = 1,
                                        )
                                    chatNote.isNotBlank() ->
                                        Text(
                                            chatNote,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = ProtoOrange,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    peer != null -> {
                                        Text(
                                            formatLastSeen(peer.lastSeenSec),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                        )
                                    }
                                    messageCount > 0 ->
                                        Text(
                                            when {
                                                starredIds.isNotEmpty() ->
                                                    UiStrings.chatStarredCountFmt(starredIds.size, messageCount)
                                                else -> UiStrings.chatMessageCountFmt(messageCount)
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                        )
                                }
                            }
                        }
                    } else {
                        val groupTitleModifier =
                            if (isGroup) {
                                Modifier.fillMaxWidth().clickable { onOpenGroupManage() }
                            } else {
                                titleModifier
                            }
                        Column(modifier = groupTitleModifier) {
                            DisplayNameWithEmoji(
                                displayName = displayTitle,
                                statusEmoji = emoji,
                                maxLines = 1,
                            )
                            if (draft.isNotBlank()) {
                                Text(
                                    UiStrings.composerDraftSaved,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ProtoOrange,
                                )
                            } else if (chatNote.isNotBlank()) {
                                Text(
                                    chatNote,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ProtoOrange,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            } else if (showTypingPref && ProtoTypingHub.conversationId == conversationId && ProtoTypingHub.userIds.isNotEmpty()) {
                                Text(
                                    UiStrings.someoneTyping,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = UiStrings.close)
                    }
                },
                actions = {
                    if (aiEnabled && !isSaved && !isChannel) {
                        IconButton(onClick = { chatPulseOpen = !chatPulseOpen }) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = UiStrings.chatPulse,
                                tint = if (chatPulseOpen) ProtoOrange else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    if (!isSaved && !isChannel) {
                        if (!isGroup) {
                            IconButton(onClick = {
                                val p = peerProfile
                                onNeedCallPermissions {
                                    calls.startOutgoing(
                                        conversationId,
                                        p?.let { resolveDisplayName(it.displayName, it.nick) }?.takeIf { it.isNotBlank() } ?: title,
                                        false,
                                        p?.avatarUploadId,
                                        p?.statusEmoji ?: "",
                                    )
                                }
                            }) {
                                Icon(Icons.Default.Phone, contentDescription = UiStrings.call)
                            }
                        }
                        Box {
                            IconButton(onClick = { chatMenuExpanded = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = UiStrings.chatMenuMore)
                            }
                            DropdownMenu(expanded = chatMenuExpanded, onDismissRequest = { chatMenuExpanded = false }) {
                                if (!isChannel) {
                                DropdownMenuItem(
                                    text = { Text(UiStrings.chatMenuVideoCall) },
                                    onClick = {
                                        chatMenuExpanded = false
                                        if (isGroup) {
                                            onGroupCall()
                                        } else {
                                            val p = peerProfile
                                            onNeedCallPermissions {
                                                calls.startOutgoing(
                                                    conversationId,
                                                    p?.let { resolveDisplayName(it.displayName, it.nick) }?.takeIf { it.isNotBlank() } ?: title,
                                                    true,
                                                    p?.avatarUploadId,
                                                    p?.statusEmoji ?: "",
                                                )
                                            }
                                        }
                                    },
                                    leadingIcon = { Icon(Icons.Default.Videocam, contentDescription = null) },
                                )
                                }
                                if (isGroup || isChannel) {
                                    DropdownMenuItem(
                                        text = { Text(UiStrings.createPoll) },
                                        onClick = { chatMenuExpanded = false; onCreatePoll() },
                                        leadingIcon = { Icon(Icons.Default.Poll, contentDescription = null) },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(UiStrings.groupInfo) },
                                        onClick = { chatMenuExpanded = false; onOpenGroupManage() },
                                        leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text(UiStrings.searchInChat) },
                                    onClick = {
                                        chatMenuExpanded = false
                                        chatSearchOpen = true
                                    },
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (showStarredOnly) {
                                                UiStrings.chatMenuShowAllMessages
                                            } else {
                                                UiStrings.chatMenuShowStarred
                                            },
                                        )
                                    },
                                    onClick = {
                                        chatMenuExpanded = false
                                        showStarredOnly = !showStarredOnly
                                    },
                                    leadingIcon = { Icon(Icons.Default.Star, contentDescription = null) },
                                )
                                if (draft.isNotBlank() || savedDraft.isNotBlank()) {
                                    DropdownMenuItem(
                                        text = { Text(UiStrings.clearComposerDraft) },
                                        onClick = {
                                            chatMenuExpanded = false
                                            draft = ""
                                            scope.launch {
                                                draftPrefs.setDraft(conversationId, "")
                                                val t = token
                                                if (t != null) {
                                                    org.assistix.proto.nativeapp.data.ProtoClientPrefsSync.pushDrafts(
                                                        t,
                                                        api,
                                                        draftPrefs,
                                                    )
                                                }
                                            }
                                        },
                                        leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null) },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text(UiStrings.exportChat) },
                                    onClick = {
                                        chatMenuExpanded = false
                                        exportChatText()
                                    },
                                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                )
                                if (!isSaved) {
                                    DropdownMenuItem(
                                        text = { Text(UiStrings.editChatNote) },
                                        onClick = {
                                            chatMenuExpanded = false
                                            noteDraft = chatNote
                                            showNoteDialog = true
                                        },
                                        leadingIcon = { Icon(Icons.Default.EditNote, contentDescription = null) },
                                    )
                                    if (chatNote.isNotBlank()) {
                                        DropdownMenuItem(
                                            text = { Text(UiStrings.clearChatNote) },
                                            onClick = {
                                                chatMenuExpanded = false
                                                scope.launch { chatLocalPrefs.setNote(conversationId, "") }
                                            },
                                            leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null) },
                                        )
                                    }
                                }
                                if (starredIds.isNotEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text(UiStrings.clearAllStars) },
                                        onClick = {
                                            chatMenuExpanded = false
                                            scope.launch { chatLocalPrefs.clearStars(conversationId) }
                                        },
                                        leadingIcon = { Icon(Icons.Default.Star, contentDescription = null) },
                                    )
                                }
                                if (peerUserId > 0) {
                                    DropdownMenuItem(
                                        text = { Text(UiStrings.reportUserAction) },
                                        onClick = {
                                            chatMenuExpanded = false
                                            showReportUser = true
                                        },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text(UiStrings.translateChat) },
                                    onClick = {
                                        chatMenuExpanded = false
                                        val t = token ?: return@DropdownMenuItem
                                        scope.launch {
                                            translationState.translatingAll.value = true
                                            val n =
                                                translateAllVisible(
                                                    api,
                                                    t,
                                                    msgs,
                                                    languageCode,
                                                    translationState,
                                                    translationCache,
                                                )
                                            translationState.translatingAll.value = false
                                            Toast.makeText(
                                                ctx,
                                                if (n > 0) UiStrings.translateChatDone.format(n) else UiStrings.translateNothing,
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(UiStrings.chatMenuWallpaper) },
                                    onClick = {
                                        chatMenuExpanded = false
                                        showAccentPicker = true
                                    },
                                    leadingIcon = { Icon(Icons.Default.Palette, contentDescription = null) },
                                )
                                DropdownMenuItem(
                                    text = { Text(UiStrings.chatExport) },
                                    onClick = {
                                        chatMenuExpanded = false
                                        scope.launch {
                                            val file =
                                                messages.exportConversation(ctx, conversationId, title)
                                            Toast.makeText(
                                                ctx,
                                                if (file != null) {
                                                    UiStrings.assistixExportedFmt(file.absolutePath)
                                                } else {
                                                    UiStrings.assistixExportEmpty
                                                },
                                                Toast.LENGTH_LONG,
                                            ).show()
                                        }
                                    },
                                    leadingIcon = { Icon(Icons.Outlined.FileDownload, contentDescription = null) },
                                )
                                DropdownMenuItem(
                                    text = { Text(UiStrings.chatMenuClearHistory) },
                                    onClick = {
                                        chatMenuExpanded = false
                                        confirmClearHistory = true
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(UiStrings.chatMenuDeleteChat, color = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        chatMenuExpanded = false
                                        confirmDeleteChat = true
                                    },
                                )
                            }
                        }
                    }
                },
            )
            }
        },
        bottomBar = {
            if (messageSelectionMode) {
            val sel = selectedMessages
            val one = sel.singleOrNull()
            val canReport =
                sel.isNotEmpty() &&
                    sel.any { !it.mine && it.id > 0 } &&
                    (peerUserId > 0 || isGroup)
            val selectionActions = buildList {
                add(
                    MessageSelectionAction(
                        id = "reply",
                        icon = MessageSelectionIcons.Reply,
                        label = UiStrings.reply,
                        enabled = sel.size == 1,
                    ) {
                        val m = one ?: return@MessageSelectionAction
                        replyTarget =
                            ReplyMeta(
                                messageId = m.id,
                                preview = m.bodyRaw.take(120).ifBlank { m.body.take(120) },
                                senderId = m.senderId,
                            )
                        exitMessageSelection()
                    },
                )
                add(
                    MessageSelectionAction(
                        id = "copy",
                        icon = MessageSelectionIcons.Copy,
                        label = UiStrings.copy,
                        enabled = sel.any { it.bodyRaw.isNotBlank() || it.body.isNotBlank() },
                    ) {
                        val text =
                            sel
                                .map { it.bodyRaw.ifBlank { it.body } }
                                .filter { it.isNotBlank() }
                                .joinToString("\n\n")
                        if (text.isNotBlank()) {
                            val clip = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clip.setPrimaryClip(ClipData.newPlainText("proto", text))
                            Toast.makeText(ctx, UiStrings.copied, Toast.LENGTH_SHORT).show()
                        }
                        exitMessageSelection()
                    },
                )
                add(
                    MessageSelectionAction(
                        id = "react",
                        icon = MessageSelectionIcons.React,
                        label = UiStrings.react,
                        enabled = sel.isNotEmpty(),
                    ) {
                        reactBulkIds = selectedMessageIds
                        reactTarget = one ?: sel.firstOrNull() ?: return@MessageSelectionAction
                        showReact = true
                    },
                )
                add(
                    MessageSelectionAction(
                        id = "forward",
                        icon = MessageSelectionIcons.Forward,
                        label = UiStrings.forward,
                        enabled = sel.isNotEmpty(),
                    ) {
                        val ordered = sel.sortedBy { it.id }
                        ProtoForwardState.start(ordered, title)
                        Toast.makeText(ctx, UiStrings.forwardPickChat, Toast.LENGTH_SHORT).show()
                        exitMessageSelection()
                        onBack()
                    },
                )
                if (!isSaved && sel.isNotEmpty()) {
                    add(
                        MessageSelectionAction(
                            id = "saved",
                            icon = MessageSelectionIcons.Saved,
                            label = UiStrings.msgActionSaveShort,
                            enabled = true,
                        ) {
                            val t = token ?: return@MessageSelectionAction
                            scope.launch {
                                sel.forEach { m ->
                                    withContext(Dispatchers.IO) {
                                        messages.saveToSaved(t, m, title, m.senderId)
                                    }
                                }
                                Toast.makeText(ctx, UiStrings.savedTo, Toast.LENGTH_SHORT).show()
                                exitMessageSelection()
                            }
                        },
                    )
                }
                if (one != null && one.mine && one.messageType == "text" && !one.hasMediaAttachment()) {
                    add(
                        MessageSelectionAction(
                            id = "edit",
                            icon = MessageSelectionIcons.Edit,
                            label = UiStrings.edit,
                            enabled = true,
                        ) {
                            editing = one
                            editText = one.bodyRaw
                            exitMessageSelection()
                        },
                    )
                }
                if (one != null && one.id > 0) {
                    val pinTarget = one
                    val isPinned = pinnedInfo?.messageId == pinTarget.id
                    add(
                        MessageSelectionAction(
                            id = "pin",
                            icon = MessageSelectionIcons.Pin,
                            label = if (isPinned) UiStrings.unpinMessage else UiStrings.pinMessage,
                            enabled = true,
                        ) {
                            val t = token ?: return@MessageSelectionAction
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    if (isPinned) api.unpinMessage(t, conversationId) else api.pinMessage(t, conversationId, pinTarget.id)
                                }
                                pinReloadTick++
                            }
                            exitMessageSelection()
                        },
                    )
                }
                if (one != null && !one.isE2e && one.messageType == "text" && one.body.isNotBlank()) {
                    add(
                        MessageSelectionAction(
                            id = "translate",
                            icon = MessageSelectionIcons.Translate,
                            label = UiStrings.translateMessage,
                            enabled = true,
                        ) {
                            val t = token ?: return@MessageSelectionAction
                            scope.launch {
                                val ok = translateMessage(api, t, one, languageCode, translationState, translationCache)
                                Toast.makeText(ctx, if (ok) UiStrings.translateDone else UiStrings.assistixError, Toast.LENGTH_SHORT).show()
                            }
                            exitMessageSelection()
                        },
                    )
                }
                if (aiEnabled && sel.size >= 2) {
                    add(
                        MessageSelectionAction(
                            id = "ai_summarize",
                            icon = MessageSelectionIcons.Summarize,
                            label = UiStrings.assistixSummarizeSelection,
                            enabled = true,
                        ) {
                            val t = token ?: return@MessageSelectionAction
                            scope.launch {
                                val summary = assistixSummarizeSelection(api, t, sel, languageCode)
                                if (summary != null) {
                                    aiExplainSheet = summary
                                } else {
                                    Toast.makeText(ctx, UiStrings.assistixError, Toast.LENGTH_SHORT).show()
                                }
                            }
                            exitMessageSelection()
                        },
                    )
                }
                add(
                    MessageSelectionAction(
                        id = "report",
                        icon = MessageSelectionIcons.Report,
                        label = UiStrings.msgActionReportShort,
                        enabled = canReport,
                    ) {
                        reportBulkIds = selectedMessageIds
                    },
                )
                add(
                    MessageSelectionAction(
                        id = "delete",
                        icon = MessageSelectionIcons.Delete,
                        label = UiStrings.delete,
                        enabled = sel.isNotEmpty(),
                        danger = true,
                    ) {
                        showBulkDelete = true
                    },
                )
            }
            MessageSelectionBottomBar(actions = selectionActions)
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            Column(Modifier.fillMaxSize().imePadding()) {
            ProtoOfflineBanner(offline = !online, queuedCount = queuedOutbox)
            pinnedInfo?.let { pin ->
                ProtoSurfaceCard(
                    onClick = { jumpToReplyMessage(pin.messageId) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.PushPin, null, tint = ProtoOrange, modifier = Modifier.padding(end = 8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(UiStrings.pinnedMessage, style = MaterialTheme.typography.labelMedium, color = ProtoOrange)
                            Text(
                                pin.preview,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
            if (chatSearchOpen) {
                ChatInSearchBar(
                    query = chatSearchQuery,
                    onQueryChange = { chatSearchQuery = it },
                    filter = chatSearchFilter,
                    onFilterChange = { chatSearchFilter = it },
                    hitIndex = searchHitIndex,
                    hitCount = searchHits.size,
                    onPrevHit = { searchHitIndex = (searchHitIndex - 1).coerceAtLeast(0) },
                    onNextHit = { searchHitIndex = (searchHitIndex + 1).coerceAtMost((searchHits.size - 1).coerceAtLeast(0)) },
                    dayLabels = searchDayLabels,
                    onJumpToDay = { day ->
                        val rowIndex = displayRows.indexOfFirst { it.dayLabel == day }
                        if (rowIndex >= 0) {
                            scope.launch { listState.animateScrollToItem(rowIndex) }
                        }
                    },
                    onClose = {
                        chatSearchOpen = false
                        chatSearchQuery = ""
                        chatSearchFilter = ChatSearchFilter.All
                        highlightedSearchMessageId = null
                    },
                    voiceSearchHint = chatSearchFilter == ChatSearchFilter.Voice || chatSearchQuery.isNotBlank(),
                )
            }
            if (isSaved) {
                SavedMessagesToolsBar(
                    selected = selectedMessages,
                    allTags = savedTags,
                    onTagsChanged = { savedMetaTick++ },
                    token = token,
                    api = api,
                    languageCode = languageCode,
                    aiEnabled = aiEnabled,
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SmartSavedFilter.entries.forEach { f ->
                        val label =
                            when (f) {
                                SmartSavedFilter.All -> UiStrings.smartSavedAll
                                SmartSavedFilter.Links -> UiStrings.smartSavedLinks
                                SmartSavedFilter.Media -> UiStrings.smartSavedMedia
                                SmartSavedFilter.Voice -> UiStrings.smartSavedVoice
                            }
                        androidx.compose.material3.FilterChip(
                            selected = smartSavedFilter == f,
                            onClick = { smartSavedFilter = f },
                            label = { Text(label) },
                        )
                    }
                }
            }
            Box(Modifier.weight(1f)) {
            val accentBrush = ChatAccent.backgroundBrush(chatAccentId)
            if (accentBrush != null) {
                Box(Modifier.matchParentSize().background(accentBrush))
            }
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 10.dp), state = listState) {
                items(displayRows, key = { it.key }) { row ->
                    if (row.bannerLabel != null) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = ProtoOrange.copy(alpha = 0.18f),
                            ) {
                                Text(
                                    row.bannerLabel,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = ProtoOrange,
                                )
                            }
                        }
                    } else if (row.dayLabel != null) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                            ) {
                                Text(
                                    row.dayLabel,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    } else if (row.msg != null) {
                        val m = row.msg
                        MessageSwipeRow(
                            enabled = !messageSelectionMode && m.messageType != "call" && m.pollMeta == null,
                            onReply = {
                                replyTarget =
                                    ReplyMeta(
                                        messageId = m.id.takeIf { it > 0 } ?: 0,
                                        preview = m.bodyRaw.take(120).ifBlank { m.body.take(120) },
                                        senderId = m.senderId,
                                    )
                            },
                            onForward = {
                                ProtoForwardState.start(m, title)
                                Toast.makeText(ctx, UiStrings.forwardPickChat, Toast.LENGTH_SHORT).show()
                                onBack()
                            },
                        ) {
                        MessageDissolveContainer(
                            dissolving = m.id in dissolvingMessageIds,
                            reduceMotion = reduceMotion,
                            onDissolved = { finishDissolveDelete(m.id) },
                        ) {
                        MessageBubble(
                            msg = m,
                            isStarred = m.id in starredIds,
                            selectionMode = messageSelectionMode,
                            selected = m.id in selectedMessageIds,
                            onSelectionToggle = { toggleMessageSelection(m.id) },
                            showLinkPreviews = showLinkPreviews,
                            displayText = displayBody(m, translationState),
                            showTranslated = translationState.translated[m.id] != null && translationState.showOriginal[m.id] != true,
                            languageBuddyOriginal =
                                if (translationState.translated[m.id] != null && translationState.showOriginal[m.id] != true) {
                                    m.body
                                } else {
                                    null
                                },
                            replyHighlightPulse = replyHighlightPulse,
                            isGroup = isGroup,
                            isChannelFeed = isChannel,
                            highlightMentions = isGroup,
                            onMentionClick = { nick ->
                                val t = token ?: return@MessageBubble
                                scope.launch {
                                    val hit =
                                        withContext(Dispatchers.IO) {
                                            api.searchUsers(t, nick).firstOrNull()
                                        }
                                    if (hit != null) onOpenProfile(hit.id)
                                }
                            },
                            showReadReceipts = showReadReceipts && !isGroup,
                            token = token,
                            api = api,
                            conversationId = conversationId,
                            myUserId = myUserId,
                            stt = stt,
                            sttQueue = app.sttQueue,
                            initialVoiceTranscript =
                                org.assistix.proto.nativeapp.data.ProtoVoiceTranscriptStore.transcriptForMessage(
                                    voiceTranscriptMap,
                                    conversationId,
                                    m,
                                ),
                            voiceTranscriptSource =
                                org.assistix.proto.nativeapp.data.ProtoVoiceTranscriptStore.transcriptSource(
                                    voiceTranscriptMap,
                                    conversationId,
                                    m,
                                ),
                            voiceSearchHighlight =
                                if (chatSearchFilter == org.assistix.proto.nativeapp.ui.ChatSearchFilter.Voice) {
                                    chatSearchQuery.trim()
                                } else {
                                    ""
                                },
                            languageCode = languageCode,
                            aiEnabled = aiEnabled,
                            onLongPress = {
                                haptic(HapticKind.Tap)
                                if (messageSelectionMode) {
                                    toggleMessageSelection(m.id)
                                } else {
                                    contextMenuMsg = m
                                }
                            },
                            replyJumpHighlighted = highlightedReplyMessageId == m.id || highlightedSearchMessageId == m.id,
                            onClick = {
                                if (messageSelectionMode) {
                                    toggleMessageSelection(m.id)
                                    return@MessageBubble
                                }
                                if (m.mine && (m.status == "failed" || m.status == "queued")) {
                                    val t = token ?: return@MessageBubble
                                    scope.launch {
                                        val ok = withContext(Dispatchers.IO) { messages.retryFailed(m.localId, t) }
                                        if (!ok) {
                                            Toast.makeText(ctx, UiStrings.uploadFailed, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    return@MessageBubble
                                }
                                if (canTapTranslate(m)) {
                                    haptic(HapticKind.Action)
                                    if (toggleMessageTranslation(m, translationState)) {
                                        return@MessageBubble
                                    }
                                    val t = token ?: return@MessageBubble
                                    scope.launch {
                                        val ok =
                                            translateMessage(
                                                api,
                                                t,
                                                m,
                                                languageCode,
                                                translationState,
                                                translationCache,
                                            )
                                        if (!ok) {
                                            Toast.makeText(ctx, UiStrings.assistixError, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                            onDoubleTap = {
                                if (messageSelectionMode) return@MessageBubble
                                val t = token ?: return@MessageBubble
                                if (m.id > 0 && m.messageType != "call" && m.pollMeta == null) {
                                    haptic(HapticKind.Reaction)
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            messages.toggleReaction(t, conversationId, myUserId, m.id, "❤️")
                                        }
                                    }
                                }
                            },
                            onToggleReaction = { em ->
                                val t = token ?: return@MessageBubble
                                if (m.id > 0) {
                                    haptic(HapticKind.Reaction)
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            messages.toggleReaction(t, conversationId, myUserId, m.id, em)
                                        }
                                    }
                                }
                            },
                            onMediaOpen = { messageId, uploadId ->
                                openChatMediaViewer(msgs, messageId, uploadId, title)
                            },
                            onPollVote = { optionIndex ->
                                val t = token ?: return@MessageBubble
                                if (m.id > 0 && m.pollMeta != null) {
                                    scope.launch {
                                        val ok = withContext(Dispatchers.IO) { api.pollVote(t, conversationId, m.id, optionIndex) }
                                        if (ok) messages.refreshFromServer(t, conversationId, myUserId)
                                    }
                                }
                            },
                            onDownload = { uploadId, name ->
                                val t = token ?: return@MessageBubble
                                scope.launch {
                                    val dest = File(org.assistix.proto.nativeapp.data.ProtoPersistentStorage.exportsDir(ctx), name.ifBlank { "proto_media" })
                                    val ok = withContext(Dispatchers.IO) { api.downloadMedia(t, uploadId, dest) }
                                    Toast.makeText(ctx, if (ok) "${UiStrings.savedTo}: ${dest.name}" else UiStrings.downloadFailed, Toast.LENGTH_LONG).show()
                                }
                            },
                            onLinkClick = { pendingLink = it },
                            onChangeReaction = { reactTarget = m; showReact = true },
                            onReplyQuoteClick = { jumpToReplyMessage(it) },
                            onOpenChannelNick = onOpenChannelNick,
                            onDiscussLink =
                                if (aiEnabled) {
                                    { card ->
                                        chatPulseSeed =
                                            "PROTO Link: ${card.url}\n${card.aiSummary.ifBlank { card.title }}"
                                        chatPulseOpen = true
                                    }
                                } else {
                                    null
                                },
                        )
                        }
                        }
                    }
                }
            }
            if (msgsLoading && msgs.isEmpty()) {
                ChatMessagesSkeleton(Modifier.matchParentSize())
            }
            if (starredHits.isNotEmpty() && !chatSearchOpen && !messageSelectionMode) {
                BadgedBox(
                    badge = {
                        val pos = if (starredHitIndex < 0) 1 else starredHitIndex + 1
                        Badge(containerColor = ProtoOrange) {
                            Text(
                                "$pos/${starredHits.size}",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    },
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(
                                end = 12.dp,
                                bottom =
                                    when {
                                        showJumpUnread && showScrollDown -> 192.dp
                                        showJumpUnread || showScrollDown -> 132.dp
                                        else -> 72.dp
                                    },
                            )
                            .pointerInput(starredHits.size) {
                                detectTapGestures(onLongPress = { jumpToPrevStarred() })
                            },
                ) {
                    FloatingActionButton(
                        onClick = { jumpToNextStarred() },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = ProtoOrange,
                        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp),
                    ) {
                        Icon(Icons.Default.Star, contentDescription = UiStrings.jumpToStarred)
                    }
                }
            }
            if (showScrollTop && !chatSearchOpen && !messageSelectionMode) {
                SmallFloatingActionButton(
                    onClick = {
                        scope.launch {
                            if (displayRows.isNotEmpty()) listState.animateScrollToItem(0)
                        }
                    },
                    modifier =
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 12.dp, bottom = if (showScrollDown) 72.dp else 12.dp),
                    containerColor = MaterialTheme.colorScheme.surface,
                ) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = UiStrings.scrollToTop, tint = ProtoOrange)
                }
            }
            if (showJumpUnread) {
                FloatingActionButton(
                    onClick = {
                        scope.launch {
                            if (unreadJumpIndex >= 0) listState.animateScrollToItem(unreadJumpIndex)
                        }
                    },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 72.dp),
                    containerColor = ProtoOrange,
                    contentColor = Color.White,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp),
                ) {
                    Icon(Icons.Default.MarkChatUnread, contentDescription = UiStrings.jumpToUnread)
                }
            }
            if (showScrollDown) {
                FloatingActionButton(
                    onClick = {
                        scope.launch {
                            if (displayRows.isNotEmpty()) listState.animateScrollToItem(displayRows.lastIndex)
                        }
                    },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                    containerColor = MaterialTheme.colorScheme.surface,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp),
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = UiStrings.scrollToBottom, tint = ProtoOrange)
                }
            }
            }
            if (uploading) {
                LinearProgress()
            }
            if (isChannel && !channelSubscribed) {
                ChannelSubscribeBar(
                    description = channelInfo?.description ?: "",
                    subscriberCount = channelInfo?.subscriberCount ?: 0,
                    busy = channelBusy,
                    onSubscribe = {
                        val t = token ?: return@ChannelSubscribeBar
                        scope.launch {
                            channelBusy = true
                            val ok = withContext(Dispatchers.IO) { api.subscribeChannel(t, conversationId) }
                            channelBusy = false
                            if (ok) {
                                channelInfo = withContext(Dispatchers.IO) { api.channelByConversation(t, conversationId) }
                                org.assistix.proto.nativeapp.data.ProtoEventHub.bump()
                            } else {
                                Toast.makeText(ctx, UiStrings.genericError, Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                )
            } else if ((!isChannel || channelCanPost) && !messageSelectionMode) {
            val composing = draft.isNotBlank()
            val draftLineCount = (draft.count { it == '\n' } + 1).coerceAtLeast(1)
            val composerMinLines = if (draftLineCount >= 3) 3 else if (composing) 2 else 1
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f), ProtoShapes.field)
                    .padding(horizontal = 6.dp, vertical = 8.dp),
            ) {
                replyTarget?.let { ComposerReplyBar(it) { replyTarget = null } }
                pendingMedia?.let { pending ->
                    MediaSendReviewPanel(
                        pending = pending,
                        caption = draft,
                        onCaptionChange = { draft = it },
                        onReplace = {
                            val kind = pending
                            clearPendingMedia()
                            when (kind) {
                                is PendingOutgoingMedia.Album -> pickAlbum.launch("image/*")
                                is PendingOutgoingMedia.Voice ->
                                    onNeedCallPermissions {
                                        VoiceRecorderFactory.start(ctx)?.let { h ->
                                            voiceRecorderHandle = h
                                            recording = true
                                            recordLevels = emptyList()
                                            recordCancelArmed = false
                                            val t = token
                                            if (t != null) {
                                                scope.launch {
                                                    withContext(Dispatchers.IO) {
                                                        api.setTyping(t, conversationId, typing = false, recording = true)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                else -> pickMedia.launch("*/*")
                            }
                        },
                        onCancel = { clearPendingMedia() },
                        onConfirm = { confirmed -> scope.launch { sendConfirmedMedia(confirmed, draft.trim()) } },
                    )
                }
                if (aiEnabled) {
                    ChatComposerAssistixSheet(
                        open = assistixOpen,
                        onDismiss = { assistixOpen = false },
                        token = token,
                        api = api,
                        draft = draft,
                        onDraft = { draft = it },
                        messages = msgs,
                        languageCode = languageCode,
                        enabled = aiEnabled,
                    )
                }
                if (recording) {
                    VoiceRecordingBar(
                        elapsedMs = recordElapsedMs,
                        levels = recordLevels,
                        cancelArmed = recordCancelArmed,
                        locked = recordLocked,
                        onStop =
                            if (recordLocked) {
                                {
                                    recording = false
                                    recordLocked = false
                                    recordCancelArmed = false
                                    val h = voiceRecorderHandle
                                    voiceRecorderHandle = null
                                    recordLevels = emptyList()
                                    val t = token
                                    if (t != null) {
                                        scope.launch {
                                            withContext(Dispatchers.IO) {
                                                api.setTyping(t, conversationId, typing = false, recording = false)
                                            }
                                        }
                                    }
                                    val elapsed = System.currentTimeMillis() - (h?.startedAtMs ?: 0L)
                                    if (elapsed < 450L) {
                                        VoiceRecorderFactory.cancel(h)
                                        Toast.makeText(ctx, UiStrings.holdToRecord, Toast.LENGTH_SHORT).show()
                                    } else {
                                        val file = VoiceRecorderFactory.stop(h)
                                        if (file != null) {
                                            clearPendingMedia()
                                            pendingMedia = PendingOutgoingMedia.Voice(file)
                                            haptic(HapticKind.Send)
                                        }
                                    }
                                }
                            } else {
                                null
                            },
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    if (aiEnabled) {
                        ChatComposerAssistixTrigger(
                            enabled = aiEnabled,
                            onClick = { assistixOpen = true },
                        )
                    }
                    Box(
                        modifier =
                            Modifier
                                .size(48.dp)
                                .combinedClickable(
                                    enabled = !uploading && !recording && pendingMedia == null,
                                    onClick = { pickMedia.launch("*/*") },
                                    onLongClick = { pickAlbum.launch("image/*") },
                                ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.AttachFile, contentDescription = UiStrings.attach)
                    }
                    if (!composing) {
                        VoiceMicHoldButton(
                            enabled = !uploading && pendingMedia == null && !recordLocked,
                            recording = recording,
                            cancelArmed = recordCancelArmed,
                            locked = recordLocked,
                            onPress = {
                                onNeedCallPermissions {
                                    VoiceRecorderFactory.start(ctx)?.let { h ->
                                        voiceRecorderHandle = h
                                        recording = true
                                        recordLevels = emptyList()
                                        recordCancelArmed = false
                                        recordLocked = false
                                        haptic(HapticKind.Action)
                                        val t = token
                                        if (t != null) {
                                            scope.launch {
                                                withContext(Dispatchers.IO) {
                                                    api.setTyping(t, conversationId, typing = false, recording = true)
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                            onCancelArmedChange = { recordCancelArmed = it },
                            onLockChange = { recordLocked = it },
                            onRelease = { cancelled, keepLocked ->
                                if (keepLocked) {
                                    recordCancelArmed = false
                                    return@VoiceMicHoldButton
                                }
                                recording = false
                                recordLocked = false
                                recordCancelArmed = false
                                val h = voiceRecorderHandle
                                voiceRecorderHandle = null
                                recordLevels = emptyList()
                                val t = token
                                if (t != null) {
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            api.setTyping(t, conversationId, typing = false, recording = false)
                                        }
                                    }
                                }
                                if (cancelled) {
                                    VoiceRecorderFactory.cancel(h)
                                    return@VoiceMicHoldButton
                                }
                                val elapsed = System.currentTimeMillis() - (h?.startedAtMs ?: 0L)
                                if (elapsed < 450L) {
                                    VoiceRecorderFactory.cancel(h)
                                    Toast.makeText(ctx, UiStrings.holdToRecord, Toast.LENGTH_SHORT).show()
                                    return@VoiceMicHoldButton
                                }
                                val file = VoiceRecorderFactory.stop(h)
                                if (file != null) {
                                    clearPendingMedia()
                                    pendingMedia = PendingOutgoingMedia.Voice(file)
                                    haptic(HapticKind.Send)
                                }
                            },
                        )
                    }
                    val sendDraft: () -> Unit = sendDraft@{
                        val text = draft.trim()
                        if (text.isEmpty()) return@sendDraft
                        val t = token ?: return@sendDraft
                        val reply = replyTarget
                        draft = ""
                        replyTarget = null
                        forceScrollToBottom = true
                        scope.launch {
                            draftPrefs.setDraft(conversationId, "")
                            withContext(Dispatchers.IO) {
                                if (isChannel && channelCanPost) {
                                    if (!api.publishChannelPost(t, conversationId, text, null)) {
                                        Toast.makeText(ctx, UiStrings.genericError, Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    messages.sendText(t, conversationId, session.userId(), text, isE2e = false, reply)
                                }
                            }
                            haptic(HapticKind.Send)
                        }
                    }
                    OutlinedTextField(
                        draft,
                        { draft = it },
                        modifier =
                            Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = if (composerMinLines >= 3) 88.dp else if (composing) 72.dp else 48.dp)
                                .heightIn(max = 220.dp),
                        placeholder = { Text(UiStrings.message) },
                        minLines = composerMinLines,
                        maxLines = 10,
                        shape = ProtoShapes.field,
                        keyboardOptions =
                            KeyboardOptions(
                                imeAction = if (sendOnEnter) ImeAction.Send else ImeAction.Default,
                            ),
                        keyboardActions =
                            KeyboardActions(
                                onSend = { if (sendOnEnter) sendDraft() },
                            ),
                    )
                    Spacer(Modifier.size(6.dp))
                    TextButton(
                        onClick = sendDraft,
                        modifier = Modifier.clip(ProtoShapes.fab).background(ProtoOrange).defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
                    ) {
                        Text("→", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    }
                }
            }
            } else if (isChannel && channelSubscribed) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f), ProtoShapes.field)
                        .padding(16.dp),
                ) {
                    val desc = channelInfo?.description?.trim().orEmpty()
                    if (desc.isNotBlank()) {
                        Text(desc, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                    }
                    Text(
                        UiStrings.channelSubscribersFmt(channelInfo?.subscriberCount ?: 0),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            }
            if (chatPulseOpen && aiEnabled) {
                val pulseSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                val previewLines =
                    remember(msgs) {
                        msgs.takeLast(30).map { m ->
                            val who = if (m.mine) "You" else "Them"
                            "$who: ${m.body.take(100)}"
                        }
                    }
                LaunchedEffect(chatPulseOpen, token) {
                    val t = token ?: return@LaunchedEffect
                    if (chatPulseOpen) {
                        withContext(Dispatchers.IO) {
                            api.assistixCatalog(t)?.rateLimit?.let { org.assistix.proto.nativeapp.data.AssistixUsageHub.apply(it) }
                        }
                    }
                }
                ModalBottomSheet(
                    onDismissRequest = {
                        chatPulseOpen = false
                        chatPulseSeed = null
                    },
                    sheetState = pulseSheetState,
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                    containerColor = MaterialTheme.colorScheme.surface,
                    dragHandle = null,
                ) {
                    ChatPulseSheet(
                        token = token,
                        api = api,
                        languageCode = languageCode,
                        chatTitle = title,
                        messagePreviewLines = previewLines,
                        initialPrompt = chatPulseSeed,
                        onClose = {
                            chatPulseOpen = false
                            chatPulseSeed = null
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    msg: MsgItem,
    isStarred: Boolean = false,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onSelectionToggle: () -> Unit = {},
    showLinkPreviews: Boolean = true,
    displayText: String = msg.body,
    showTranslated: Boolean = false,
    languageBuddyOriginal: String? = null,
    replyJumpHighlighted: Boolean = false,
    replyHighlightPulse: Int = 0,
    onClick: () -> Unit = {},
    isGroup: Boolean,
    isChannelFeed: Boolean = false,
    highlightMentions: Boolean = false,
    showReadReceipts: Boolean,
    token: String?,
    api: ProtoApi,
    conversationId: Int,
    myUserId: Int,
    stt: org.assistix.proto.nativeapp.data.ProtoSttCoordinator,
    sttQueue: org.assistix.proto.nativeapp.data.ProtoSttQueue,
    initialVoiceTranscript: String? = null,
    voiceTranscriptSource: String? = null,
    voiceSearchHighlight: String = "",
    languageCode: String,
    aiEnabled: Boolean = false,
    onLongPress: () -> Unit,
    onDoubleTap: () -> Unit = {},
    onToggleReaction: (String) -> Unit,
    onMediaOpen: (messageId: Long, uploadId: String?) -> Unit,
    onPollVote: (Int) -> Unit,
    onDownload: (String, String) -> Unit,
    onLinkClick: (String) -> Unit,
    onMentionClick: (String) -> Unit = {},
    onChangeReaction: () -> Unit = {},
    onReplyQuoteClick: (Long) -> Unit = {},
    onOpenChannelNick: (String) -> Unit = {},
    onDiscussLink: ((org.assistix.proto.nativeapp.data.LinkPreview) -> Unit)? = null,
) {
    val isCall = msg.messageType == "call" || msg.callMeta != null
    var pulseOn by remember(replyHighlightPulse) { mutableStateOf(false) }
    LaunchedEffect(replyJumpHighlighted, replyHighlightPulse) {
        if (!replyJumpHighlighted) {
            pulseOn = false
            return@LaunchedEffect
        }
        repeat(2) {
            pulseOn = true
            kotlinx.coroutines.delay(320)
            pulseOn = false
            kotlinx.coroutines.delay(280)
        }
    }
    val showHighlight = replyJumpHighlighted && pulseOn
    val highlightBorderColor by animateColorAsState(
        targetValue = if (showHighlight) ProtoOrange else Color.Transparent,
        animationSpec = tween(durationMillis = 220),
        label = "replyJumpHighlight",
    )
    val highlightFillColor by animateColorAsState(
        targetValue = if (showHighlight) ProtoOrange.copy(alpha = 0.38f) else Color.Transparent,
        animationSpec = tween(durationMillis = 220),
        label = "replyJumpFill",
    )
    val isPoll = msg.messageType == "poll" && msg.pollMeta != null
    val isChannelCard = msg.messageType == "channel_card" && msg.channelCardMeta != null
    if (isChannelCard && msg.channelCardMeta != null) {
        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 4.dp)) {
            ChannelCardBubble(
                card = msg.channelCardMeta,
                token = token,
                api = api,
                onOpenChannel = { nick, _ -> onOpenChannelNick(nick) },
            )
        }
        return
    }
    val isChannelPost = isChannelFeed && msg.channelPostMeta != null
    if (isChannelPost && msg.channelPostMeta != null) {
        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 4.dp)) {
            ChannelPostCard(post = msg.channelPostMeta, token = token, api = api)
        }
        return
    }
    val align = if (isCall) Arrangement.Center else if (msg.mine) Arrangement.End else Arrangement.Start
    val bg =
        when {
            isCall -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
            msg.mine -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.surfaceVariant
        }
    val fg = if (msg.mine && !isCall) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val pickedHighlight =
        if (selected) {
            Modifier.border(
                2.dp,
                ProtoOrange.copy(alpha = 0.75f),
                ProtoShapes.bubble,
            )
        } else {
            Modifier
        }

    MessageSelectionRowBackground(selectionMode = selectionMode, selected = selected) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            if (selectionMode) {
                MessageSelectCircle(selected = selected, onClick = onSelectionToggle)
            }
            Row(
                Modifier.weight(1f),
                horizontalArrangement = align,
            ) {
                Column(
                    Modifier
                        .widthIn(max = if (isCall) 340.dp else 300.dp)
                        .clip(ProtoShapes.bubble)
                        .background(if (showHighlight) highlightFillColor else bg)
                        .then(pickedHighlight)
                        .border(
                            width = if (showHighlight) 2.5.dp else 0.dp,
                            color = highlightBorderColor,
                            shape = ProtoShapes.bubble,
                        )
                        .combinedClickable(onClick = onClick, onLongClick = onLongPress, onDoubleClick = onDoubleTap)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
            if (isGroup && !msg.mine && msg.senderName.isNotBlank()) {
                Text(
                    msg.senderName,
                    style = MaterialTheme.typography.labelMedium,
                    color = fg.copy(alpha = 0.88f),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
            }
            if (msg.reply != null) {
                ReplyQuoteBlock(
                    reply = msg.reply!!,
                    textColor = fg,
                    onClick =
                        if (msg.reply!!.messageId > 0) {
                            { onReplyQuoteClick(msg.reply!!.messageId) }
                        } else {
                            null
                        },
                )
                Spacer(Modifier.height(6.dp))
            }
            if (msg.forward != null) {
                Text(
                    "↪ ${msg.forward.fromLabel}",
                    style = MaterialTheme.typography.labelSmall,
                    color = fg.copy(alpha = 0.85f),
                    fontWeight = FontWeight.SemiBold,
                )
                Text(msg.forward.bodySnippet, style = MaterialTheme.typography.bodySmall, color = fg.copy(alpha = 0.75f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                HorizontalDivider(Modifier.padding(vertical = 6.dp), color = fg.copy(alpha = 0.25f))
            }
            if (isPoll && msg.pollMeta != null) {
                PollBubble(poll = msg.pollMeta, myUserId = myUserId, onVote = onPollVote)
            } else if (isCall) {
                val callLabel = msg.callMeta?.let { formatCallPreview(it) } ?: msg.body
                Text(callLabel, color = fg, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            } else if (msg.albumMeta != null && token != null) {
                ChatAlbumGrid(
                    album = msg.albumMeta,
                    token = token,
                    api = api,
                    textColor = fg,
                    messageId = msg.id,
                    onMediaOpen = { mid, up -> onMediaOpen(mid, up) },
                )
                val cap = AlbumMeta.captionFromJson(msg.bodyRaw)
                if (cap.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    LinkifiedMessageText(
                        text = cap,
                        color = fg,
                        linkColor = if (msg.mine) fg.copy(alpha = 0.9f) else MaterialTheme.colorScheme.primary,
                        onLinkClick = onLinkClick,
                        highlightMentions = highlightMentions,
                        mentionColor = MaterialTheme.colorScheme.tertiary,
                    )
                }
            } else if (msg.hasMediaAttachment() && token != null && msg.albumMeta == null) {
                ChatMediaContent(
                    uploadId = msg.mediaUploadId!!,
                    mime = msg.mediaMime,
                    name = msg.mediaName,
                    token = token,
                    api = api,
                    textColor = fg,
                    onDownload = onDownload,
                    conversationId = conversationId,
                    messageId = msg.id,
                    stt = stt,
                    sttQueue = sttQueue,
                    initialVoiceTranscript = initialVoiceTranscript,
                    voiceTranscriptSource = voiceTranscriptSource,
                    voiceSearchHighlight = voiceSearchHighlight,
                    languageCode = languageCode,
                    aiEnabled = aiEnabled,
                    mediaKind = msg.mediaKind,
                    onOpenViewer =
                        if (msg.isGalleryMedia()) {
                            { onMediaOpen(msg.id, msg.mediaUploadId) }
                        } else {
                            null
                        },
                )
                if (msg.shouldShowMediaCaption()) {
                    Spacer(Modifier.height(4.dp))
                    val caption = msg.bodyRaw
                    LinkifiedMessageText(
                        text = caption,
                        color = fg,
                        linkColor = if (msg.mine) fg.copy(alpha = 0.9f) else MaterialTheme.colorScheme.primary,
                        onLinkClick = onLinkClick,
                        highlightMentions = highlightMentions,
                        mentionColor = MaterialTheme.colorScheme.tertiary,
                    )
                    if (!msg.isE2e) {
                        MessageLinkPreview(caption, token, api, fg, onLinkClick, enabled = showLinkPreviews)
                    }
                }
            } else {
                if (showTranslated) {
                    Text(
                        UiStrings.translatedLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = fg.copy(alpha = 0.65f),
                    )
                    Spacer(Modifier.height(2.dp))
                }
                LinkifiedMessageText(
                    text = displayText,
                    color = fg,
                    linkColor = if (msg.mine) fg.copy(alpha = 0.9f) else MaterialTheme.colorScheme.primary,
                    onLinkClick = onLinkClick,
                    highlightMentions = highlightMentions,
                    mentionColor = MaterialTheme.colorScheme.tertiary,
                    onMentionClick = onMentionClick,
                )
                if (!languageBuddyOriginal.isNullOrBlank() && showTranslated) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        UiStrings.languageBuddyOriginal,
                        style = MaterialTheme.typography.labelSmall,
                        color = fg.copy(alpha = 0.55f),
                    )
                    Text(
                        languageBuddyOriginal,
                        style = MaterialTheme.typography.bodySmall,
                        color = fg.copy(alpha = 0.72f),
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                if (!msg.isE2e && msg.messageType == "text") {
                    MessageLinkPreview(
                        msg.bodyRaw.ifBlank { msg.body },
                        token,
                        api,
                        fg,
                        onLinkClick,
                        enabled = showLinkPreviews,
                        onDiscussAssistix = onDiscussLink,
                    )
                }
            }
            if (msg.editedAt > 0) {
                Text(UiStrings.edited, fontSize = 10.sp, color = fg.copy(alpha = 0.7f))
            }
            ReactionChipsRow(
                msg.reactions,
                fg,
                onToggle = onToggleReaction,
                onMyReactionLongPress = onChangeReaction,
            )
            Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                if (isStarred) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        tint = ProtoOrange,
                        modifier = Modifier.size(12.dp),
                    )
                    Spacer(Modifier.size(4.dp))
                }
                Text(formatTime(msg.createdAt), fontSize = 10.sp, color = fg.copy(alpha = 0.75f))
                if (msg.mine && !isCall && showReadReceipts) {
                    Spacer(Modifier.size(4.dp))
                    val ticks =
                        when {
                            msg.status == "failed" -> "!"
                            msg.status == "queued" || msg.status == "sending" -> "…"
                            msg.readByPeer -> "✓✓"
                            else -> "✓"
                        }
                    Text(ticks, fontSize = 11.sp, color = if (msg.readByPeer) fg else fg.copy(alpha = 0.65f))
                    if (msg.readByPeer && msg.peerReadAt > 0) {
                        Spacer(Modifier.size(4.dp))
                        Text(
                            "${UiStrings.readAt} ${formatTime(msg.peerReadAt)}",
                            fontSize = 10.sp,
                            color = fg.copy(alpha = 0.8f),
                        )
                    }
                }
            }
                }
            }
        }
    }
}

@Composable
private fun LinearProgress() {
    Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) { CircularProgressIndicator(Modifier.size(22.dp)) }
}

private fun formatTime(ms: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))

private fun formatDateTime(ms: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(ms))

private fun dayKey(ms: Long, languageCode: String): String {
    val locale =
        when (languageCode.lowercase()) {
            "ru" -> Locale("ru")
            "it" -> Locale.ITALIAN
            else -> Locale.ENGLISH
        }
    val now = Calendar.getInstance()
    val msgCal = Calendar.getInstance().apply { timeInMillis = ms }
    val pattern =
        if (now.get(Calendar.YEAR) == msgCal.get(Calendar.YEAR)) {
            "d MMMM"
        } else {
            "d MMMM yyyy"
        }
    return SimpleDateFormat(pattern, locale).format(Date(ms))
}

private data class ChatRow(
    val key: String,
    val dayLabel: String?,
    val msg: MsgItem?,
    val bannerLabel: String? = null,
)

private fun buildChatRows(
    msgs: List<MsgItem>,
    languageCode: String,
    myLastReadMessageId: Long,
): List<ChatRow> {
    var last = ""
    var insertedNew = false
    val out = ArrayList<ChatRow>()
    for (m in msgs) {
        if (!insertedNew && m.id > myLastReadMessageId && !m.mine && m.id > 0L) {
            out.add(ChatRow("banner-new", null, null, UiStrings.newMessagesDivider))
            insertedNew = true
        }
        val d = dayKey(m.createdAt, languageCode)
        if (d != last) {
            out.add(ChatRow("day-$d", d, null))
            last = d
        }
        out.add(ChatRow(m.localId.ifBlank { "m-${m.id}" }, null, m))
    }
    return out
}
