package org.assistix.proto.nativeapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.assistix.proto.nativeapp.data.ConnectivityWarningKind
import org.assistix.proto.nativeapp.data.ProtoConnectivityAdvisor

@Composable
fun ProtoConnectivityBanner(
    advisor: ProtoConnectivityAdvisor,
    modifier: Modifier = Modifier,
) {
    val kind by advisor.warningKind.collectAsState()
    val k = kind ?: return
    val scope = rememberCoroutineScope()
    val (title, body) =
        when (k) {
            ConnectivityWarningKind.Vpn ->
                UiStrings.connectivityVpnTitle to UiStrings.connectivityVpnBody
            ConnectivityWarningKind.Foreign ->
                UiStrings.connectivityForeignTitle to UiStrings.connectivityForeignBody
            ConnectivityWarningKind.Slow ->
                UiStrings.connectivitySlowTitle to UiStrings.connectivitySlowBody
            ConnectivityWarningKind.VpnForeign ->
                UiStrings.connectivityVpnForeignTitle to UiStrings.connectivityVpnForeignBody
            ConnectivityWarningKind.ForeignSlow ->
                UiStrings.connectivityForeignSlowTitle to UiStrings.connectivityForeignSlowBody
        }
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.92f),
        tonalElevation = 2.dp,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(top = 2.dp),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            IconButton(
                onClick = { scope.launch { advisor.dismissWarning() } },
                modifier = Modifier.align(Alignment.Top),
            ) {
                Icon(Icons.Default.Close, contentDescription = UiStrings.close, tint = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}
