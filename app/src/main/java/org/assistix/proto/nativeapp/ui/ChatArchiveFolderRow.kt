package org.assistix.proto.nativeapp.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ChatArchiveFolderRow(
    archivedCount: Int,
    pullReveal: Float,
    pullHint: String,
    onOpenArchive: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reveal by animateFloatAsState(pullReveal.coerceIn(0f, 1f), label = "archiveReveal")
    if (reveal < 0.04f) return
    val rowHeight = (52.dp * reveal).coerceAtLeast(0.dp)
    ProtoSurfaceCard(
        onClick = onOpenArchive,
        modifier =
            modifier
                .fillMaxWidth()
                .height(rowHeight)
                .alpha(reveal.coerceIn(0.35f, 1f)),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Archive, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    UiStrings.archiveFolderTitle,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    if (archivedCount > 0) {
                        UiStrings.archiveFolderCountFmt(archivedCount)
                    } else {
                        pullHint
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
