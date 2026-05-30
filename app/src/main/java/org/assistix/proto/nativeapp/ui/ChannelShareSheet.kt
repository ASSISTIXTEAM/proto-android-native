package org.assistix.proto.nativeapp.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import org.assistix.proto.nativeapp.data.ChannelCardMeta
import org.assistix.proto.nativeapp.data.ChannelHit
import org.assistix.proto.nativeapp.data.ConvItem
import org.assistix.proto.nativeapp.data.ProtoApi

fun channelShareUrl(channel: ChannelHit): String =
    channel.publicUrl.ifBlank {
        if (channel.nick.isNotBlank()) "https://proto.su/c/@${channel.nick}" else ""
    }

fun channelShareText(channel: ChannelHit): String {
    val url = channelShareUrl(channel)
    val nick = channel.nick.ifBlank { "channel" }
    val title = channel.title.ifBlank { nick }
    return buildString {
        append("📢 ")
        append(title)
        if (channel.verified) append(" ✓")
        append("\n@")
        append(nick)
        if (url.isNotBlank()) {
            append("\n")
            append(url)
        }
        append("\n\n")
        append(UiStrings.channelShareMessageCta)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelShareSheet(
    channel: ChannelHit,
    chats: List<ConvItem>,
    excludeConversationId: Int,
    api: ProtoApi,
    token: String?,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var pickChat by remember { mutableStateOf(false) }
    var sendBusy by remember { mutableStateOf(false) }
    val shareText = remember(channel) { channelShareText(channel) }
    val url = remember(channel) { channelShareUrl(channel) }

    fun copyLink() {
        if (url.isBlank()) return
        val clip = ClipData.newPlainText("proto_channel", url)
        (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
        Toast.makeText(ctx, UiStrings.copied, Toast.LENGTH_SHORT).show()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(
                UiStrings.channelShareTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(12.dp))
            ChannelTitleRow(
                title = channel.title.ifBlank { channel.nick },
                nick = channel.nick,
                verified = channel.verified,
                showVerifiedTooltip = true,
            )
            if (url.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            ShareActionRow(
                icon = { Icon(Icons.Default.ContentCopy, null) },
                label = UiStrings.channelShareCopyLink,
                onClick = {
                    copyLink()
                    onDismiss()
                },
            )
            ShareActionRow(
                icon = { Icon(Icons.Default.Share, null) },
                label = UiStrings.share,
                onClick = {
                    sharePlainText(ctx, channel.title.ifBlank { "@${channel.nick}" }, shareText)
                    onDismiss()
                },
            )
            ShareActionRow(
                icon = { Icon(Icons.AutoMirrored.Filled.Send, null) },
                label = UiStrings.channelShareToChat,
                onClick = { pickChat = true },
            )
        }
    }

    if (pickChat) {
        val targets =
            remember(chats, excludeConversationId) {
                chats.filter { c ->
                    c.id != excludeConversationId &&
                        (c.kind == "dm" || c.kind == "group" || c.kind == "saved")
                }
            }
        AlertDialog(
            onDismissRequest = { if (!sendBusy) pickChat = false },
            title = { Text(UiStrings.channelShareToChat) },
            text = {
                if (targets.isEmpty()) {
                    Text(UiStrings.channelShareNoChats)
                } else {
                    LazyColumn(
                        modifier = Modifier.height(280.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(targets, key = { it.id }) { chat ->
                            val label =
                                when (chat.kind) {
                                    "saved" -> UiStrings.savedMessages
                                    else -> chat.title.ifBlank { chat.peerDisplayName }
                                }
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !sendBusy) {
                                        val t = token ?: return@clickable
                                        scope.launch {
                                            sendBusy = true
                                            val body = ChannelCardMeta.fromChannel(channel).toMessageBody()
                                            val ok =
                                                withContext(Dispatchers.IO) {
                                                    api.sendMessage(t, chat.id, body).ok
                                                }
                                            sendBusy = false
                                            pickChat = false
                                            if (ok) {
                                                Toast
                                                    .makeText(ctx, UiStrings.channelLinkSent, Toast.LENGTH_SHORT)
                                                    .show()
                                                onDismiss()
                                            } else {
                                                Toast
                                                    .makeText(ctx, UiStrings.genericError, Toast.LENGTH_SHORT)
                                                    .show()
                                            }
                                        }
                                    }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                ProtoAvatar(
                                    if (chat.kind == "dm") chat.peerAvatarUploadId else null,
                                    label,
                                    40.dp,
                                    api,
                                    token,
                                )
                                Spacer(Modifier.size(12.dp))
                                Text(label, fontWeight = FontWeight.Medium, maxLines = 1)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { pickChat = false }, enabled = !sendBusy) {
                    Text(UiStrings.cancel)
                }
            },
        )
    }
}

@Composable
private fun ShareActionRow(
    icon: @Composable () -> Unit,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(Modifier.size(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
    }
}
