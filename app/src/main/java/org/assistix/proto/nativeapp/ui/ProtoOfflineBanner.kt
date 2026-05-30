package org.assistix.proto.nativeapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun ProtoOfflineBanner(
    offline: Boolean,
    queuedCount: Int = 0,
    cellsPending: Int = 0,
    cellsRepairing: Int = 0,
    showCellsStatus: Boolean = false,
    modifier: Modifier = Modifier,
) {
    if (!offline && queuedCount <= 0 && (!showCellsStatus || (cellsPending <= 0 && cellsRepairing <= 0))) return
    val bg =
        if (offline) {
            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.92f)
        } else {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.92f)
        }
    val fg =
        if (offline) {
            MaterialTheme.colorScheme.onTertiaryContainer
        } else {
            MaterialTheme.colorScheme.onSecondaryContainer
        }
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(bg)
                .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        if (offline) {
            Text(
                UiStrings.offlineShowingCache,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = fg,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                UiStrings.offlineSendWhenOnline,
                style = MaterialTheme.typography.labelSmall,
                color = fg.copy(alpha = 0.9f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            )
        }
        if (queuedCount > 0) {
            Text(
                if (offline) UiStrings.offlineQueuedFmt(queuedCount) else UiStrings.onlineQueuedFmt(queuedCount),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (offline) FontWeight.Normal else FontWeight.Medium,
                color = fg.copy(alpha = 0.92f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = if (offline) 4.dp else 0.dp),
            )
        }
        if (showCellsStatus && cellsRepairing > 0) {
            Text(
                UiStrings.cellsRepairBadge,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = fg,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                color = ProtoOrange,
            )
        } else if (showCellsStatus && cellsPending > 0) {
            Text(
                UiStrings.cellsPendingHoldsFmt(cellsPending),
                style = MaterialTheme.typography.labelSmall,
                color = fg.copy(alpha = 0.92f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                color = ProtoOrange.copy(0.7f),
            )
        }
    }
}
