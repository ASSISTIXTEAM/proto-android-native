package org.assistix.proto.nativeapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val statusEmojiChoices =
    listOf(
        "",
        "😀", "😊", "😎", "🥳", "😴", "🤔", "😇", "🫡",
        "🔥", "💜", "💙", "💚", "✨", "⭐", "🌙", "☀️",
        "🎧", "☕", "🍕", "🍌", "🚀", "💼", "🎮", "📷", "🏋️",
        "🌍", "📚", "🎵", "⚡", "🛡️", "💬", "📞", "🎯",
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusEmojiPickerSheet(
    selected: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Text(
            UiStrings.statusEmojiPick,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(8),
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(statusEmojiChoices) { em ->
                val sel = selected == em
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(ProtoShapes.field)
                        .background(
                            if (sel) MaterialTheme.colorScheme.primary.copy(0.22f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(0.45f),
                        )
                        .clickable {
                            onPick(em)
                            onDismiss()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (em.isEmpty()) "—" else em,
                        fontSize = if (em.isEmpty()) 16.sp else 22.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
fun StatusEmojiField(
    selected: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(ProtoShapes.field)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(0.4f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            if (selected.isEmpty()) UiStrings.statusEmojiPick else selected,
            fontSize = if (selected.isEmpty()) 16.sp else 28.sp,
            color =
                if (selected.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface,
        )
    }
}
