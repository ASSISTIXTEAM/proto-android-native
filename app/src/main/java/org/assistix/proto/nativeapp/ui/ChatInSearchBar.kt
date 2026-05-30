package org.assistix.proto.nativeapp.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ChatInSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    filter: ChatSearchFilter,
    onFilterChange: (ChatSearchFilter) -> Unit,
    hitIndex: Int,
    hitCount: Int,
    onPrevHit: () -> Unit,
    onNextHit: () -> Unit,
    dayLabels: List<String>,
    onJumpToDay: (String) -> Unit,
    onClose: () -> Unit,
    voiceSearchHint: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(UiStrings.searchMessagesHint) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = UiStrings.close)
                }
            },
            singleLine = true,
            shape = ProtoShapes.field,
        )
        if (voiceSearchHint) {
            Row(
                Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Default.Mic, contentDescription = null, tint = ProtoOrange, modifier = Modifier.size(16.dp))
                Text(
                    UiStrings.voiceSearchHint,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ChatSearchFilter.entries.forEach { f ->
                FilterChip(
                    selected = filter == f,
                    onClick = { onFilterChange(f) },
                    label = { Text(chatSearchFilterLabel(f)) },
                )
            }
        }
        if (dayLabels.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                dayLabels.takeLast(8).forEach { day ->
                    FilterChip(
                        selected = false,
                        onClick = { onJumpToDay(day) },
                        label = { Text(day, maxLines = 1) },
                    )
                }
            }
        }
        if (query.isNotBlank() || filter != ChatSearchFilter.All) {
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    if (hitCount > 0) "${hitIndex + 1}/$hitCount" else UiStrings.chatSearchNoResults,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (hitCount > 0) {
                    Row {
                        IconButton(onClick = onPrevHit, enabled = hitIndex > 0) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = UiStrings.searchPrev)
                        }
                        IconButton(onClick = onNextHit, enabled = hitIndex < hitCount - 1) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = UiStrings.searchNext)
                        }
                    }
                }
            }
        }
    }
}

private fun chatSearchFilterLabel(f: ChatSearchFilter): String =
    when (f) {
        ChatSearchFilter.All -> UiStrings.chatSearchFilterAll
        ChatSearchFilter.Text -> UiStrings.chatSearchFilterText
        ChatSearchFilter.Media -> UiStrings.chatSearchFilterMedia
        ChatSearchFilter.Links -> UiStrings.chatSearchFilterLinks
        ChatSearchFilter.Voice -> UiStrings.chatSearchFilterVoice
    }
