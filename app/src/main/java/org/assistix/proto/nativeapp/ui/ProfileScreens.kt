package org.assistix.proto.nativeapp.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.assistix.proto.nativeapp.data.AccountRestriction
import org.assistix.proto.nativeapp.data.MeProfile
import org.assistix.proto.nativeapp.data.ProtoApi
import org.assistix.proto.nativeapp.data.ProtoHosts
import org.assistix.proto.nativeapp.data.ProtoSessionStore
import org.assistix.proto.nativeapp.data.UserProfile
import org.assistix.proto.nativeapp.data.isFounderNick
import org.assistix.proto.nativeapp.data.resolveDisplayName

fun formatLastSeen(sec: Long): String {
    if (sec <= 0) return UiStrings.lastSeenUnknown
    val now = System.currentTimeMillis() / 1000
    val diff = now - sec
    if (diff < 90) return UiStrings.online
    if (diff < 3600) return String.format(Locale.getDefault(), UiStrings.lastSeenMinutes, diff / 60)
    if (diff < 86400) return String.format(Locale.getDefault(), UiStrings.lastSeenHours, diff / 3600)
    val whenStr = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(sec * 1000))
    return String.format(Locale.getDefault(), UiStrings.lastSeenAt, whenStr)
}

private fun maskEmail(email: String): String {
    val e = email.trim()
    if (e.isBlank() || !e.contains('@')) return e
    val parts = e.split('@', limit = 2)
    val local = parts[0]
    val domain = parts.getOrElse(1) { "" }
    val masked =
        when {
            local.length <= 1 -> "*"
            local.length == 2 -> "${local.first()}*"
            else -> "${local.take(2)}${"*".repeat((local.length - 2).coerceAtMost(4))}"
        }
    return "$masked@$domain"
}

@Composable
private fun ProfileLoadError(onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Text(UiStrings.profileLoadFailed, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            ProtoGhostButton(UiStrings.profileLoadRetry, onRetry, Modifier.fillMaxWidth())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyProfileTab(
    session: ProtoSessionStore,
    api: ProtoApi,
    reduceMotion: Boolean = false,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val token by session.tokenFlow.collectAsState(initial = null)
    var profile by remember { mutableStateOf<MeProfile?>(null) }
    var restriction by remember { mutableStateOf<AccountRestriction?>(null) }
    var loadFailed by remember { mutableStateOf(false) }
    var reloadTick by remember { mutableIntStateOf(0) }
    var nickDraft by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var statusEmoji by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var editorMode by remember { mutableStateOf(ProfileEditorMode.Card) }
    var showEmojiPicker by remember { mutableStateOf(false) }
    var showProfileQr by remember { mutableStateOf(false) }
    var profileLinkUrl by remember { mutableStateOf<String?>(null) }

    fun applyProfile(p: MeProfile) {
        profile = p
        nickDraft = p.nick
        displayName = resolveDisplayName(p.displayName, p.nick)
        bio = p.bio
        status = p.statusText
        statusEmoji = p.statusEmoji
        loadFailed = false
    }

    LaunchedEffect(token, reloadTick) {
        val t = token ?: return@LaunchedEffect
        loadFailed = false
        val loaded = runCatching { withContext(Dispatchers.IO) { api.me(t) } }.getOrNull()
        if (loaded?.profile != null) {
            applyProfile(loaded.profile)
            restriction = loaded.restriction?.takeIf { it.isActive }
        } else {
            profile = null
            loadFailed = true
        }
    }

    val p = profile
    val nickLive = nickDraft.trim().ifBlank { p?.nick.orEmpty() }
    val nameLive = resolveDisplayName(displayName, nickLive)
    val hasChanges =
        p != null &&
            (
                nickDraft.trim() != p.nick ||
                    resolveDisplayName(displayName, p.nick) != resolveDisplayName(p.displayName, p.nick) ||
                    bio.trim() != p.bio.trim() ||
                    status.trim() != p.statusText.trim() ||
                    statusEmoji != p.statusEmoji
            )

    val completeness =
        profileCompletenessPercent(
            hasAvatar = !p?.avatarUploadId.isNullOrBlank(),
            displayName = nameLive,
            bio = bio,
            status = status,
            statusEmoji = statusEmoji,
            nick = nickLive,
        )

    val pickAvatar =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            val t = token ?: return@rememberLauncherForActivityResult
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                try {
                    val tmp = File.createTempFile("av_", ".jpg", ctx.cacheDir)
                    ctx.contentResolver.openInputStream(uri)?.use { i -> tmp.outputStream().use { i.copyTo(it) } }
                    val id = withContext(Dispatchers.IO) { api.uploadFile(t, tmp, "image/jpeg") }
                    tmp.delete()
                    if (id != null) {
                        val updated =
                            withContext(Dispatchers.IO) {
                                api.updateProfile(t, nameLive, bio, status, statusEmoji, id)
                            }
                        if (updated != null) applyProfile(updated)
                        Toast.makeText(ctx, UiStrings.avatarUpdated, Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(ctx, e.message ?: UiStrings.genericError, Toast.LENGTH_SHORT).show()
                }
            }
        }

    val profileQrBmp = remember(profileLinkUrl) { profileLinkUrl?.let { ProtoQrEncoder.encode(it, 480) } }

    fun saveProfile() {
        val t = token ?: return
        val current = p ?: return
        val name = resolveDisplayName(displayName, nickDraft)
        if (displayName.trim().isEmpty()) {
            Toast.makeText(ctx, UiStrings.displayNameRequired, Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch {
            saving = true
            val trimmedNick = nickDraft.trim()
            if (trimmedNick.length < 3) {
                saving = false
                Toast.makeText(ctx, UiStrings.nickInvalid, Toast.LENGTH_SHORT).show()
                return@launch
            }
            var working = current
            if (!trimmedNick.equals(current.nick, ignoreCase = true)) {
                when (val nickRes = withContext(Dispatchers.IO) { api.changeNick(t, trimmedNick) }) {
                    is ProtoApi.NickChangeResult.Ok -> {
                        working = nickRes.profile
                        applyProfile(working)
                    }
                    is ProtoApi.NickChangeResult.Fail -> {
                        saving = false
                        Toast.makeText(ctx, nickRes.message, Toast.LENGTH_LONG).show()
                        return@launch
                    }
                }
            }
            val updated =
                withContext(Dispatchers.IO) {
                    api.updateProfile(t, name, bio.trim(), status.trim(), statusEmoji, working.avatarUploadId)
                }
            if (updated != null) applyProfile(updated)
            saving = false
            Toast.makeText(ctx, if (updated != null) UiStrings.profileSaved else UiStrings.saveFailed, Toast.LENGTH_SHORT).show()
        }
    }

    when {
        loadFailed -> ProfileLoadError(onRetry = { reloadTick++ })
        p == null ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = ProtoOrange)
            }
        else -> {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = Color.Transparent,
                floatingActionButton = {
                    if (hasChanges) {
                        FloatingActionButton(
                            onClick = { if (!saving) saveProfile() },
                            containerColor = ProtoOrange,
                            contentColor = Color.White,
                            modifier = Modifier.navigationBarsPadding(),
                        ) {
                            if (saving) {
                                CircularProgressIndicator(Modifier.size(26.dp), color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Text(UiStrings.save, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                },
            ) { innerPad ->
                Box(Modifier.fillMaxSize().padding(innerPad)) {
                    ProtoBrandBackdrop()
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp)
                            .padding(top = 8.dp, bottom = if (hasChanges) 88.dp else 24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        ProfileImmersiveHeader(
                            avatarUploadId = p.avatarUploadId,
                            displayName = nameLive,
                            statusEmoji = statusEmoji,
                            nick = nickLive,
                            subtitle = status.trim().ifBlank { bio.trim().take(120).ifBlank { null } },
                            token = token,
                            api = api,
                            onAvatarClick = { pickAvatar.launch("image/*") },
                        )

                        ProfileGlassCard {
                            ProfileCompletenessMeter(completeness)
                        }

                        restriction?.let { ProfileGlassCard { ProfileShameBoard(it) } }

                        ProfileModeSwitcher(mode = editorMode, onMode = { editorMode = it })

                        AnimatedContent(
                            targetState = editorMode,
                            transitionSpec = {
                                fadeIn(ProtoMotion.fade(reduceMotion, 220)) togetherWith fadeOut(ProtoMotion.fade(reduceMotion, 160))
                            },
                            label = "profileMode",
                        ) { mode ->
                            when (mode) {
                                ProfileEditorMode.Card -> {
                                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        ProfileGlassCard {
                                            Text(
                                                UiStrings.profilePreviewHint,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                            if (bio.trim().isNotBlank()) {
                                                Spacer(Modifier.height(12.dp))
                                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.1f))
                                                Spacer(Modifier.height(12.dp))
                                                Text(bio.trim(), style = MaterialTheme.typography.bodyLarge)
                                            }
                                        }
                                        ProfileActionTile(
                                            icon = Icons.Default.QrCode2,
                                            title = UiStrings.qrHubMyCode,
                                            subtitle = UiStrings.profileQrSubtitle,
                                            onClick = {
                                                scope.launch {
                                                    val t = token ?: return@launch
                                                    val link = withContext(Dispatchers.IO) { api.createPublicLink(t, "profile") }
                                                    profileLinkUrl = link?.url ?: ProtoHosts.profileUrl(p.nick)
                                                    showProfileQr = true
                                                }
                                            },
                                        )
                                        ProfileActionTile(
                                            icon = Icons.Default.Link,
                                            title = UiStrings.profileShareLink,
                                            subtitle = ProtoHosts.profileUrl(nickLive),
                                            onClick = {
                                                val url = ProtoHosts.profileUrl(nickLive)
                                                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                cm.setPrimaryClip(ClipData.newPlainText("proto", url))
                                                Toast.makeText(ctx, UiStrings.copied, Toast.LENGTH_SHORT).show()
                                            },
                                        )
                                    }
                                }
                                ProfileEditorMode.Edit -> {
                                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        ProfileGlassCard {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            ) {
                                                Icon(Icons.Default.CameraAlt, null, tint = ProtoOrange)
                                                Column(Modifier.weight(1f)) {
                                                    Text(UiStrings.changePhoto, fontWeight = FontWeight.SemiBold)
                                                    Text(
                                                        UiStrings.profileAvatarHint,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }
                                                TextButton(onClick = { pickAvatar.launch("image/*") }) {
                                                    Text(UiStrings.profileTapChange)
                                                }
                                            }
                                            Spacer(Modifier.height(12.dp))
                                            OutlinedTextField(
                                                displayName,
                                                { displayName = it },
                                                label = { Text(UiStrings.displayName) },
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = ProtoShapes.field,
                                                singleLine = true,
                                            )
                                        }
                                        ProfileGlassCard {
                                            Text(UiStrings.profileSectionAccount, fontWeight = FontWeight.Bold)
                                            Spacer(Modifier.height(10.dp))
                                            OutlinedTextField(
                                                nickDraft,
                                                { nickDraft = it.filter { ch -> ch.isLetterOrDigit() || ch == '_' }.take(32) },
                                                label = { Text(UiStrings.nick) },
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = ProtoShapes.field,
                                                singleLine = true,
                                            )
                                            Text(
                                                UiStrings.nickChangeHint,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(top = 6.dp),
                                            )
                                            if (p.email.isNotBlank()) {
                                                Spacer(Modifier.height(12.dp))
                                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.1f))
                                                Spacer(Modifier.height(12.dp))
                                                Text(UiStrings.profileEmailLabel, style = MaterialTheme.typography.labelMedium)
                                                Text(maskEmail(p.email), fontWeight = FontWeight.Medium)
                                                Text(
                                                    UiStrings.profileEmailHint,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                        ProfileGlassCard {
                                            Text(UiStrings.profileSectionStatus, fontWeight = FontWeight.Bold)
                                            Spacer(Modifier.height(8.dp))
                                            StatusEmojiField(
                                                selected = statusEmoji,
                                                onClick = { showEmojiPicker = true },
                                            )
                                            Spacer(Modifier.height(8.dp))
                                            OutlinedTextField(
                                                status,
                                                { status = it },
                                                label = { Text(UiStrings.status) },
                                                placeholder = { Text(UiStrings.profileStatusPlaceholder) },
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = ProtoShapes.field,
                                                singleLine = true,
                                            )
                                        }
                                        ProfileGlassCard {
                                            Text(UiStrings.profileSectionAbout, fontWeight = FontWeight.Bold)
                                            Spacer(Modifier.height(8.dp))
                                            OutlinedTextField(
                                                bio,
                                                { bio = it },
                                                label = { Text(UiStrings.bio) },
                                                placeholder = { Text(UiStrings.profileBioPlaceholder) },
                                                modifier = Modifier.fillMaxWidth(),
                                                minLines = 4,
                                                shape = ProtoShapes.field,
                                            )
                                        }
                                        ProtoPrimaryButton(
                                            text = if (saving) "…" else UiStrings.save,
                                            onClick = { if (!saving) saveProfile() },
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEmojiPicker) {
        StatusEmojiPickerSheet(
            selected = statusEmoji,
            onPick = { statusEmoji = it },
            onDismiss = { showEmojiPicker = false },
        )
    }

    if (showProfileQr) {
        AlertDialog(
            onDismissRequest = { showProfileQr = false },
            title = { Text(UiStrings.qrHubMyCode) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    profileQrBmp?.let { bmp ->
                        Image(bmp.asImageBitmap(), contentDescription = null, modifier = Modifier.size(240.dp))
                    }
                    profileLinkUrl?.let { Text(it, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center) }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        profileLinkUrl?.let { url ->
                            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("proto", url))
                            Toast.makeText(ctx, UiStrings.copied, Toast.LENGTH_SHORT).show()
                        }
                    },
                ) { Text(UiStrings.copyLink) }
            },
            dismissButton = { TextButton(onClick = { showProfileQr = false }) { Text(UiStrings.close) } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileSheet(
    userId: Int,
    api: ProtoApi,
    token: String?,
    onDismiss: () -> Unit,
    onMessage: ((userId: Int, title: String, peerId: Int) -> Unit)? = null,
) {
    var user by remember(userId) { mutableStateOf<UserProfile?>(null) }
    var loadFailed by remember(userId) { mutableStateOf(false) }
    var reloadTick by remember(userId) { mutableIntStateOf(0) }
    var showReport by remember { mutableStateOf(false) }
    var showBlock by remember { mutableStateOf(false) }
    var messageBusy by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(userId, token, reloadTick) {
        user = null
        loadFailed = false
        val t = token
        if (t.isNullOrBlank() || userId <= 0) {
            loadFailed = true
            return@LaunchedEffect
        }
        val loaded = runCatching { withContext(Dispatchers.IO) { api.userById(t, userId) } }.getOrNull()
        if (loaded != null) user = loaded else loadFailed = true
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = ProtoShapes.dialog,
    ) {
        when {
            loadFailed -> {
                Column(
                    Modifier.fillMaxWidth().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(UiStrings.profileLoadFailed, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    ProtoGhostButton(UiStrings.profileLoadRetry, { reloadTick++ }, Modifier.fillMaxWidth())
                    TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(UiStrings.close) }
                }
            }
            user == null -> {
                Box(Modifier.fillMaxWidth().height(320.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ProtoOrange)
                }
            }
            else -> {
                val u = user!!
                val nick = u.nick.ifBlank { "user$userId" }
                val label = resolveDisplayName(u.displayName, u.nick)
                val statusLine = u.statusText.ifBlank { formatLastSeen(u.lastSeenSec) }

                Column(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    ProfileImmersiveHeader(
                        avatarUploadId = u.avatarUploadId,
                        displayName = label,
                        statusEmoji = u.statusEmoji,
                        nick = nick,
                        subtitle = statusLine,
                        token = token,
                        api = api,
                        showProtoBadge = false,
                    )

                    ProfileActionTile(
                        icon = Icons.Default.ContentCopy,
                        title = "@$nick",
                        subtitle = UiStrings.profileNickTapCopy,
                        onClick = {
                            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("nick", nick))
                            Toast.makeText(ctx, UiStrings.nickCopied, Toast.LENGTH_SHORT).show()
                        },
                    )

                    u.moderationPublic?.let { ProfileGlassCard { ProfileShameBoard(it) } }

                    if (u.bio.isNotBlank()) {
                        ProfileGlassCard {
                            Text(UiStrings.bio, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Text(u.bio, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    if (u.canMessage && onMessage != null && !isFounderNick(u.nick)) {
                        ProtoPrimaryButton(
                            text = if (messageBusy) "…" else UiStrings.profileWriteMessage,
                            onClick = {
                                if (messageBusy) return@ProtoPrimaryButton
                                messageBusy = true
                                scope.launch {
                                    onMessage(userId, label, userId)
                                    messageBusy = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    if (!isFounderNick(u.nick) && (u.canReport || u.canBlock)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (u.canReport) {
                                ProtoGhostButton(UiStrings.reportUserAction, { showReport = true }, Modifier.weight(1f))
                            }
                            if (u.canBlock) {
                                ProtoGhostButton(UiStrings.blockUserAction, { showBlock = true }, Modifier.weight(1f))
                            }
                        }
                    }

                    toast?.let {
                        Text(it, color = ProtoOrange, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    }

                    TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(UiStrings.close) }
                }
            }
        }
    }

    if (showReport && user != null) {
        ReportUserDialog(
            targetUserId = userId,
            api = api,
            token = token,
            onDismiss = { showReport = false },
            onDone = { msg -> toast = msg },
        )
    }
    if (showBlock && user != null) {
        val label = resolveDisplayName(user!!.displayName, user!!.nick)
        BlockUserDialog(
            displayName = label,
            api = api,
            token = token,
            userId = userId,
            onDismiss = { showBlock = false },
            onBlocked = {
                toast = UiStrings.userBlockedDone
                onDismiss()
            },
        )
    }
}
