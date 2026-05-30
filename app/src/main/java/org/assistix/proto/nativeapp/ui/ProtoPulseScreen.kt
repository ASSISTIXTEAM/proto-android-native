package org.assistix.proto.nativeapp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.MarkChatUnread
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assistix.proto.nativeapp.data.ProtoApi
import org.assistix.proto.nativeapp.data.ProtoPulseRepository
import org.assistix.proto.nativeapp.data.PulseDigest
import org.assistix.proto.nativeapp.data.ProtoSessionStore

@Composable
fun ProtoPulseScreen(
    session: ProtoSessionStore,
    api: ProtoApi,
    languageCode: String,
    onOpenChat: (Int, String, String, Int) -> Unit,
    onOpenChannelFeed: (Int, String) -> Unit,
    reloadTick: Int = 0,
) {
    var loading by remember { mutableStateOf(true) }
    var digest by remember { mutableStateOf<PulseDigest?>(null) }
    LaunchedEffect(session, languageCode, reloadTick) {
        loading = true
        val t = session.token() ?: run {
            digest = null
            loading = false
            return@LaunchedEffect
        }
        digest = withContext(Dispatchers.IO) { ProtoPulseRepository.load(t, api, languageCode) }
        loading = false
    }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            UiStrings.pulseTitle,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            UiStrings.pulseSubtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
        )
        if (loading) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator(Modifier.size(32.dp), color = ProtoOrange)
            }
            return@Column
        }
        val d = digest
        if (d == null) {
            Text(UiStrings.signIn, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }
        if (d.aiSummary.isNotBlank()) {
            PulseCard(
                icon = { Icon(Icons.Default.AutoAwesome, null, tint = ProtoOrange) },
                title = UiStrings.pulseAiSummary,
            ) {
                Text(d.aiSummary, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(12.dp))
        }
        if (d.unread.isNotEmpty()) {
            PulseCard(
                icon = { Icon(Icons.Default.MarkChatUnread, null, tint = ProtoOrange) },
                title = UiStrings.pulseUnreadSection.format(d.totalUnread),
            ) {
                d.unread.forEach { item ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                onOpenChat(item.conversationId, item.title, item.kind, item.peerUserId)
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(item.title, fontWeight = FontWeight.SemiBold)
                            Text(
                                item.preview,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                            )
                        }
                        if (item.unreadCount > 0) {
                            Text(
                                item.unreadCount.toString(),
                                color = ProtoOrange,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        d.protoPost?.let { post ->
            PulseCard(
                icon = { Icon(Icons.Default.Campaign, null, tint = ProtoOrange) },
                title = UiStrings.pulseProtoSection,
            ) {
                Text(post.body, style = MaterialTheme.typography.bodyMedium, maxLines = 6)
                Text(
                    UiStrings.channelOpenFeed,
                    color = ProtoOrange,
                    style = MaterialTheme.typography.labelLarge,
                    modifier =
                        Modifier
                            .padding(top = 8.dp)
                            .clickable { onOpenChannelFeed(post.conversationId, post.title) },
                )
            }
            Spacer(Modifier.height(12.dp))
        }
        if (d.savedHints.isNotEmpty()) {
            PulseCard(
                icon = { Icon(Icons.Default.Bookmark, null, tint = ProtoOrange) },
                title = UiStrings.pulseSavedSection,
            ) {
                d.savedHints.forEach { hint ->
                    Text(
                        hint.preview.ifBlank { UiStrings.savedMessages },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onOpenChat(hint.conversationId, UiStrings.savedMessages, "saved", 0)
                                }
                                .padding(vertical = 6.dp),
                    )
                }
            }
        }
        if (d.unread.isEmpty() && d.protoPost == null && d.savedHints.isEmpty() && d.aiSummary.isBlank()) {
            Text(
                UiStrings.pulseAllClear,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 24.dp),
            )
        }
    }
}

@Composable
private fun PulseCard(
    icon: @Composable () -> Unit,
    title: String,
    content: @Composable () -> Unit,
) {
    ProtoSurfaceCard {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                icon()
                Spacer(Modifier.size(8.dp))
                Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}
