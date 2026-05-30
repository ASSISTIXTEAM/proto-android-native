package org.assistix.proto.nativeapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Hive
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assistix.proto.nativeapp.ProtoApplication
import org.assistix.proto.nativeapp.data.ProtoCacheManager
import org.assistix.proto.nativeapp.data.ProtoPersistentStorage
import org.assistix.proto.nativeapp.data.ProtoTransferProgressHub

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProtoCellsScreen(
    onBack: () -> Unit,
    showBack: Boolean = true,
    compact: Boolean = false,
) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as ProtoApplication
    val stats by app.cellsManager.stats.collectAsState()
    val repair by app.cellsManager.repairActive.collectAsState()
    val transferJobs by ProtoTransferProgressHub.active.collectAsState()
    val cellsJobs = transferJobs.filter { it.id.startsWith("cells-") }

    LaunchedEffect(Unit) {
        val t = app.session.token() ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            runCatching { app.cellsManager.refreshStats(t) }
            runCatching { app.cellsManager.syncMyHolds(t) }
        }
    }

    Scaffold(
        topBar = {
            if (showBack) {
                TopAppBar(
                    title = { Text(UiStrings.cellsScreenTitle) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = UiStrings.back)
                        }
                    },
                )
            }
        },
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = if (compact) 8.dp else 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = Color.Transparent,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(ProtoOrange.copy(0.22f), MaterialTheme.colorScheme.primary.copy(0.12f)),
                            ),
                            RoundedCornerShape(28.dp),
                        ),
            ) {
                Column(
                    Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ProtoCellsArt(
                        modifier = Modifier.size(if (compact) 96.dp else 132.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        UiStrings.cellsScreenTitle,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        UiStrings.cellsScreenTagline,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    if (!compact) {
                        Spacer(Modifier.height(14.dp))
                        ProtoCellsTierBadge(stats.tier)
                    }
                }
            }
            if (!compact) {
                Spacer(Modifier.height(16.dp))
                Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(UiStrings.cellsStatsTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(UiStrings.cellsStorageFmt(stats.usedBytes.coerceAtLeast(stats.localBytes), stats.quotaBytes))
                        Text(UiStrings.cellsHelpedFmt(stats.conversationsHelped))
                        Text(
                            UiStrings.cellsLocalShardsFmt(stats.localShards, ProtoCacheManager.formatBytes(stats.localBytes)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (stats.lastSyncAt > 0L) {
                            val whenStr = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(stats.lastSyncAt))
                            Text(
                                UiStrings.cellsLastSyncFmt(whenStr),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (stats.holdsPending > 0) {
                            Text(UiStrings.cellsPendingHoldsFmt(stats.holdsPending), color = ProtoOrange)
                        }
                        if (repair > 0) {
                            Text(UiStrings.cellsRepairBadge, color = ProtoOrange, fontWeight = FontWeight.SemiBold)
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = ProtoOrange)
                        } else if (cellsJobs.isNotEmpty()) {
                            val agg =
                                cellsJobs.mapNotNull { j -> j.progress.takeIf { it >= 0f } }.average().takeIf { !it.isNaN() }
                            if (agg != null) {
                                LinearProgressIndicator(
                                    progress = { agg.toFloat() },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = ProtoOrange,
                                )
                            } else {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = ProtoOrange)
                            }
                            Text(
                                cellsJobs.first().label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                CellsInfoCard(Icons.Default.Storage, UiStrings.cellsVaultTitle, UiStrings.cellsVaultBody)
            }
            Spacer(Modifier.height(if (compact) 12.dp else 20.dp))
            CellsInfoCard(Icons.Default.Shield, UiStrings.cellsMandatoryTitle, UiStrings.cellsMandatoryBody)
            Spacer(Modifier.height(12.dp))
            CellsInfoCard(Icons.Default.Lock, UiStrings.cellsSafeTitle, UiStrings.cellsSafeBody)
            Spacer(Modifier.height(12.dp))
            CellsInfoCard(Icons.Default.Hive, UiStrings.cellsHowTitle, UiStrings.cellsHowBody)
            Spacer(Modifier.height(16.dp))
            Text(
                UiStrings.cellsBulletsTitle,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            UiStrings.cellsBullets.forEach { line ->
                CellsBullet(line)
            }
            if (!compact) {
                Spacer(Modifier.height(12.dp))
                Text(
                    UiStrings.dataStorageVaultHint,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun CellsInfoCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(icon, contentDescription = null, tint = ProtoOrange, modifier = Modifier.size(28.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun CellsBullet(text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            tint = ProtoOrange,
            modifier = Modifier.size(18.dp),
        )
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun OnboardCellsPage() {
    ProtoCellsScreen(onBack = {}, showBack = false, compact = true)
}
