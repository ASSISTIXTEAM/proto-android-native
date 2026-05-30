package org.assistix.proto.nativeapp.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.assistix.proto.nativeapp.data.ProtoTransferProgressHub

@Composable
fun ProtoGlobalProgressBar(
    modifier: Modifier = Modifier,
    /** Hide Cells upload/sync/repair jobs on main chat surfaces — details live in Settings → Cells. */
    hideCellsJobs: Boolean = false,
) {
    val jobs by ProtoTransferProgressHub.active.collectAsState()
    val visible =
        if (hideCellsJobs) {
            jobs.filter { !it.id.startsWith("cells-") }
        } else {
            jobs
        }
    if (visible.isEmpty()) return
    val primary = visible.first()
    val aggregate =
        if (visible.size == 1) {
            primary.progress
        } else {
            val known = visible.filter { it.progress >= 0f }
            if (known.isEmpty()) -1f else known.map { it.progress }.average().toFloat()
        }
    Column(modifier.fillMaxWidth()) {
        if (aggregate >= 0f) {
            LinearProgressIndicator(
                progress = { aggregate },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = ProtoOrange,
            )
        } else {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = ProtoOrange,
            )
        }
        if (visible.size == 1 || primary.label.isNotBlank()) {
            Text(
                if (visible.size > 1) UiStrings.transferProgressMulti(visible.size) else primary.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun ProtoCellsTierBadge(tier: String, modifier: Modifier = Modifier) {
    val label =
        when (tier) {
            "node" -> UiStrings.cellsTierNode
            "active" -> UiStrings.cellsTierActive
            else -> UiStrings.cellsTierMember
        }
    androidx.compose.material3.AssistChip(
        onClick = {},
        enabled = false,
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        modifier = modifier,
        border = null,
        colors =
            androidx.compose.material3.AssistChipDefaults.assistChipColors(
                disabledContainerColor = ProtoOrange.copy(0.18f),
                disabledLabelColor = ProtoOrange,
            ),
    )
}
