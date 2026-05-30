package org.assistix.proto.nativeapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.assistix.proto.nativeapp.data.AccountRestriction

@Composable
fun SuspensionScreen(
    restriction: AccountRestriction,
    onLogout: () -> Unit,
) {
    val until = restriction.untilLabel()
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = ProtoShapes.card,
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(22.dp)) {
                Text(
                    UiStrings.suspensionTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(12.dp))
                if (restriction.publicNote.isNotBlank()) {
                    Text(
                        restriction.publicNote,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Start,
                    )
                    Spacer(Modifier.height(10.dp))
                }
                if (restriction.reason.isNotBlank()) {
                    Text(
                        UiStrings.suspensionReason.format(restriction.reason),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (until != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        UiStrings.suspensionUntil.format(until),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    UiStrings.suspensionNoAppeal,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    UiStrings.suspensionIsolation,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        TextButton(onClick = onLogout) {
            Text(UiStrings.signOut)
        }
    }
}

@Composable
fun ProfileShameBoard(restriction: AccountRestriction) {
    val until = restriction.untilLabel()
    Surface(
        shape = ProtoShapes.card,
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                UiStrings.profileShameTitle,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                restriction.publicNote.ifBlank { UiStrings.profileShameDefault },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (restriction.reason.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    UiStrings.suspensionReason.format(restriction.reason),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (until != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    UiStrings.suspensionUntil.format(until),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                UiStrings.suspensionNoAppeal,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
