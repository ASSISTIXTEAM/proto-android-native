package org.assistix.proto.nativeapp.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Filter chip with reliable long-press (Material [FilterChip] swallows long clicks). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color? = null,
    multiSelected: Boolean = false,
) {
    val shape = RoundedCornerShape(8.dp)
    val tint = accentColor ?: ProtoOrange
    val bg =
        when {
            multiSelected -> tint.copy(alpha = 0.35f)
            selected -> tint.copy(alpha = 0.22f)
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        }
    val fg =
        when {
            multiSelected || selected -> tint
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    Box(
        modifier
            .clip(shape)
            .background(bg)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            color = fg,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
