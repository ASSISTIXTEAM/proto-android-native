package org.assistix.proto.nativeapp.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.assistix.proto.nativeapp.data.MsgItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageDeleteSheet(
    message: MsgItem,
    canDeleteForEveryone: Boolean,
    onDismiss: () -> Unit,
    onDeleteForMe: () -> Unit,
    onDeleteForEveryone: (() -> Unit)?,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text(
                UiStrings.deleteMessageTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (message.mine) UiStrings.deleteMessageOwnHint else UiStrings.deleteMessageOtherHint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            if (canDeleteForEveryone && onDeleteForEveryone != null) {
                ProtoPrimaryButton(
                    UiStrings.deleteForAll,
                    onDeleteForEveryone,
                    Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
            }
            ProtoPrimaryButton(
                UiStrings.deleteForMe,
                onDeleteForMe,
                Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            ProtoGhostButton(UiStrings.cancel, onDismiss, Modifier.fillMaxWidth())
            Spacer(Modifier.height(24.dp))
        }
    }
}
