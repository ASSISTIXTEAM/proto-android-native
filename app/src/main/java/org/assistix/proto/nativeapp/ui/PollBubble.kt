package org.assistix.proto.nativeapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.assistix.proto.nativeapp.data.PollMeta

@Composable
fun PollBubble(
    poll: PollMeta,
    myUserId: Int,
    onVote: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val total = poll.totalVotes().coerceAtLeast(1)
    val mine = poll.mySelections(myUserId)
    Column(
        modifier
            .fillMaxWidth()
            .clip(ProtoShapes.card)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(0.45f))
            .border(1.dp, ProtoOrange.copy(0.25f), ProtoShapes.card)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Poll, null, tint = ProtoOrange, modifier = Modifier.padding(end = 8.dp))
            Text(poll.question, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        poll.options.forEachIndexed { index, label ->
            val count = poll.countForOption(index)
            val pct = count.toFloat() / total.toFloat()
            val selected = index in mine
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(ProtoShapes.field)
                    .background(
                        if (selected) ProtoOrange.copy(0.18f)
                        else MaterialTheme.colorScheme.surface.copy(0.6f),
                    )
                    .clickable { onVote(index) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text("$count", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { pct },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = ProtoOrange,
                    trackColor = MaterialTheme.colorScheme.outline.copy(0.25f),
                )
            }
        }
    }
}
