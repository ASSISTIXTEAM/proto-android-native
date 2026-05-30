package org.assistix.proto.nativeapp.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.assistix.proto.nativeapp.data.ChannelCardMeta
import org.assistix.proto.nativeapp.data.ProtoApi

@Composable
fun ChannelCardBubble(
    card: ChannelCardMeta,
    token: String?,
    api: ProtoApi,
    onOpenChannel: (nick: String, subscribeIfNeeded: Boolean) -> Unit,
    onSubscribed: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var subscribed by remember(card.nick) { mutableStateOf(card.subscribed) }

    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Campaign, null, tint = ProtoOrange, modifier = Modifier.size(28.dp))
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(card.title.ifBlank { card.nick }, fontWeight = FontWeight.Bold, maxLines = 1)
                    if (card.verified) {
                        Spacer(Modifier.size(4.dp))
                        VerifiedBadge(showTooltip = true, modifier = Modifier.size(16.dp))
                    }
                }
                Text(
                    "@${card.nick}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (card.description.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                card.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))
        if (!subscribed) {
            ProtoPrimaryButton(
                if (busy) "…" else UiStrings.channelSubscribe,
                {
                    val t = token ?: return@ProtoPrimaryButton
                    scope.launch {
                        busy = true
                        val cid =
                            card.conversationId.takeIf { it > 0 }
                                ?: withContext(Dispatchers.IO) {
                                    api.channelByNick(t, card.nick)?.conversationId
                                }
                                ?: 0
                        val ok =
                            if (cid > 0) {
                                withContext(Dispatchers.IO) { api.subscribeChannel(t, cid) }
                            } else {
                                false
                            }
                        busy = false
                        if (ok) {
                            subscribed = true
                            onSubscribed()
                            Toast.makeText(ctx, UiStrings.channelLinkSent, Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(ctx, UiStrings.genericError, Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
        }
        ProtoGhostButton(
            UiStrings.channelOpenFeed,
            { onOpenChannel(card.nick, false) },
            Modifier.fillMaxWidth(),
        )
    }
}
