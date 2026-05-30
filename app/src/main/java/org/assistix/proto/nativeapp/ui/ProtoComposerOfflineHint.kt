package org.assistix.proto.nativeapp.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Subtle offline / outbox hint — composer only, no top banner. */
@Composable
fun ProtoComposerOfflineHint(
    offline: Boolean,
    queuedInChat: Int,
    modifier: Modifier = Modifier,
) {
    if (!offline && queuedInChat <= 0) return
    val text =
        when {
            offline && queuedInChat > 0 -> UiStrings.composerOfflineQueuedFmt(queuedInChat)
            offline -> UiStrings.offlineSendWhenOnline
            queuedInChat > 0 -> UiStrings.composerQueuedFmt(queuedInChat)
            else -> return
        }
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth().padding(bottom = 4.dp),
    )
}
