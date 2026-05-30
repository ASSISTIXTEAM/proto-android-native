package org.assistix.proto.nativeapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.assistix.proto.nativeapp.BuildConfig
import org.assistix.proto.nativeapp.ProtoApplication
import org.assistix.proto.nativeapp.data.ProtoCrashReporter
import org.assistix.proto.nativeapp.data.WhisperNativeSupport
import org.assistix.proto.nativeapp.data.local.ProtoDatabase as RoomDb

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProtoHealthScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as ProtoApplication
    val cellsStats by app.cellsManager.stats.collectAsState()
    var dataStoreOk by remember { mutableStateOf(false) }
    var roomOk by remember { mutableStateOf(false) }
    var crashLogLines by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        dataStoreOk =
            runCatching {
                app.prefs.onboardingComplete.first()
                true
            }.getOrDefault(false)
        roomOk =
            withContext(Dispatchers.IO) {
                runCatching { RoomDb.get(ctx).dao().outboxCount(); true }.getOrDefault(false)
            }
        crashLogLines = ProtoCrashReporter.readLocalLog(ctx).lines().count { it.isNotBlank() }
        val token = app.session.token()
        if (!token.isNullOrBlank()) {
            app.cellsManager.refreshStats(token)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(UiStrings.healthScreenTitle) },
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
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(UiStrings.healthScreenHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HealthRow(UiStrings.healthDataStore, dataStoreOk)
            HealthRow(UiStrings.healthRoom, roomOk)
            HealthRow(UiStrings.healthCellsEnrolled, cellsStats.holdsTotal >= 0)
            HealthLine(UiStrings.healthCellsShards, "${cellsStats.localShards} local · ${cellsStats.holdsAcked} ack")
            HealthLine(UiStrings.healthCellsStorage, UiStrings.cellsStorageFmt(cellsStats.usedBytes, cellsStats.quotaBytes))
            HealthLine(UiStrings.healthCellsChats, UiStrings.cellsHelpedFmt(cellsStats.conversationsHelped))
            HealthLine(UiStrings.healthCellsSync, if (cellsStats.lastSyncAt > 0) UiStrings.healthLastSyncFmt(cellsStats.lastSyncAt) else UiStrings.healthNever)
            HealthRow(UiStrings.healthWhisper, WhisperNativeSupport.isRuntimeSafe(ctx))
            HealthLine(UiStrings.healthBuild, "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            if (crashLogLines > 0) {
                HealthLine(UiStrings.healthCrashLog, UiStrings.healthCrashLogFmt(crashLogLines))
            }
        }
    }
}

@Composable
private fun HealthRow(label: String, ok: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Icon(
            if (ok) Icons.Default.CheckCircle else Icons.Default.Error,
            contentDescription = null,
            tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun HealthLine(label: String, value: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
