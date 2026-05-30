package org.assistix.proto.nativeapp.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.assistix.proto.nativeapp.data.ChannelHit

@Composable
fun ProtoOfficialChannelSubscribeDialog(
    channel: ChannelHit,
    busy: Boolean,
    onSubscribe: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            ChannelTitleRow(
                title = channel.title.ifBlank { "PROTO" },
                nick = channel.nick.ifBlank { "proto" },
                verified = channel.verified,
                showVerifiedTooltip = true,
            )
        },
        text = {
            Column {
                Text(
                    UiStrings.channelOfficialSubscribeBody,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (channel.description.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(channel.description, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSubscribe, enabled = !busy) {
                Text(if (busy) "…" else UiStrings.channelSubscribe)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(UiStrings.later)
            }
        },
    )
}
