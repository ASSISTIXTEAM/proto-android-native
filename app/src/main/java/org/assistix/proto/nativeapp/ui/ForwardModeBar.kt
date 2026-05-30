package org.assistix.proto.nativeapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Компактная панель пересылки (как выделение сообщений). */
@Composable
fun ForwardModeBar(
    messageCount: Int,
    fromLabel: String,
    selectedCount: Int,
    onCancel: () -> Unit,
    onSendMulti: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        ProtoPanelContainerLite {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onCancel, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Close, contentDescription = UiStrings.cancel)
                }
                Icon(
                    Icons.AutoMirrored.Filled.Forward,
                    contentDescription = null,
                    tint = ProtoOrange,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    if (messageCount > 1) {
                        UiStrings.forwardModeMessagesFmt(messageCount)
                    } else {
                        UiStrings.forwardModeTitle
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
                )
                if (selectedCount > 0) {
                    TextButton(onClick = onSendMulti) {
                        Text(
                            UiStrings.forwardModeSendFmt(selectedCount),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.SemiBold,
                            color = ProtoOrange,
                        )
                    }
                } else {
                    Text(
                        fromLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                }
            }
        }
    }
}
