package org.assistix.proto.nativeapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.assistix.proto.nativeapp.data.DeviceSession
import org.assistix.proto.nativeapp.data.ProtoApi
import org.assistix.proto.nativeapp.data.ProtoSessionStore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(
    session: ProtoSessionStore,
    api: ProtoApi,
    onBack: () -> Unit,
    onScanQr: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var devices by remember { mutableStateOf<List<DeviceSession>>(emptyList()) }

    fun reload() {
        scope.launch {
            val t = session.token() ?: return@launch
            loading = true
            devices =
                withContext(Dispatchers.IO) {
                    api.listDevices(t)
                        .filter { !it.revoked }
                        .distinctBy { it.id }
                        .sortedByDescending { if (it.current) Long.MAX_VALUE else it.lastActive }
                }
            loading = false
        }
    }

    LaunchedEffect(Unit) { reload() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(UiStrings.devicesSection, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = UiStrings.back)
                    }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            ProtoSurfaceCard(modifier = Modifier.padding(bottom = 14.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        UiStrings.scanQrLinkWeb,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(
                        UiStrings.scanQrLinkHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.size(12.dp))
                    ProtoPrimaryButton(UiStrings.linkWebConnect, onScanQr, Modifier.fillMaxWidth())
                }
            }

            val currentSession = devices.firstOrNull { it.current }
            val others = devices.filter { !it.current }
            val othersCount = others.size
            val totalSessions = othersCount + if (currentSession != null) 1 else 0

            ProtoSurfaceCard(modifier = Modifier.padding(bottom = 10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            UiStrings.activeSessions,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "$totalSessions · ${UiStrings.devicesSection.lowercase()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (othersCount > 0) {
                        Surface(shape = ProtoShapes.field, color = ProtoOrange.copy(alpha = 0.15f)) {
                            Text(
                                othersCount.toString(),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                color = ProtoOrange,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }

            if (othersCount > 0) {
                ProtoPrimaryButton(
                    UiStrings.revokeOtherDevices,
                    onClick = {
                        scope.launch {
                            val t = session.token() ?: return@launch
                            withContext(Dispatchers.IO) { api.revokeOtherDevices(t) }
                            reload()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                )
            }

            if (loading) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ProtoOrange)
                }
            } else if (currentSession == null && others.isEmpty()) {
                Text(UiStrings.noOtherSessions, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                if (currentSession != null) {
                    Text(
                        UiStrings.currentSession,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    SessionRow(
                        device = currentSession,
                        trailing = {},
                    )
                    Spacer(Modifier.size(10.dp))
                }
                if (others.isNotEmpty()) {
                    Text(
                        UiStrings.activeSessions,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                others.forEach { d ->
                    SessionRow(
                        device = d,
                        trailing = {
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        val t = session.token() ?: return@launch
                                        withContext(Dispatchers.IO) { api.revokeDevice(t, d.id) }
                                        reload()
                                    }
                                },
                            ) {
                                Text(UiStrings.revokeSession, color = MaterialTheme.colorScheme.error)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionRow(device: DeviceSession, trailing: @Composable () -> Unit) {
    val isWeb = device.label.contains("Web", ignoreCase = true)
    ProtoSurfaceCard(modifier = Modifier.padding(bottom = 8.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (isWeb) Icons.Default.Computer else Icons.Default.PhoneAndroid,
                    contentDescription = null,
                    tint = ProtoOrange,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.size(10.dp))
                Column {
                    Text(
                        device.label.ifBlank { "PROTO" },
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        if (device.current) UiStrings.currentSession else formatDeviceTime(device.lastActive),
                        style = MaterialTheme.typography.bodySmall,
                        color =
                            if (device.current) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    )
                    val geo =
                        when {
                            device.lastIp.isNotBlank() && device.lastCountry.isNotBlank() ->
                                UiStrings.deviceGeoFmt.format(device.lastIp, device.lastCountry)
                            device.lastIp.isNotBlank() -> device.lastIp
                            device.lastCountry.isNotBlank() -> device.lastCountry
                            else -> ""
                        }
                    if (geo.isNotBlank()) {
                        Text(
                            geo,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            trailing()
        }
    }
}

private fun formatDeviceTime(sec: Long): String {
    if (sec <= 0) return ""
    return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(sec * 1000))
}
