package org.assistix.proto.nativeapp.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assistix.proto.nativeapp.data.ProtoPersistentStorage
import org.assistix.proto.nativeapp.data.ProtoStorageBreakdown
import org.assistix.proto.nativeapp.data.ProtoStorageStats

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProtoDataStorageScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var stats by remember { mutableStateOf<ProtoStorageBreakdown?>(null) }
    val needsAccess = remember { ProtoPersistentStorage.needsAllFilesAccess(ctx) }

    LaunchedEffect(Unit) {
        stats = withContext(Dispatchers.IO) { ProtoStorageStats.scan(ctx) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(UiStrings.dataStorageTitle) },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(UiStrings.dataStorageHint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            stats?.let { s ->
                StorageRow(UiStrings.dataStorageTotal, ProtoStorageStats.formatBytes(s.totalBytes))
                StorageRow(UiStrings.dataStoragePath, s.rootPath)
                StorageRow(UiStrings.dataStorageDb, ProtoStorageStats.formatBytes(s.databaseBytes))
                StorageRow(UiStrings.dataStorageCache, ProtoStorageStats.formatBytes(s.cacheBytes))
                StorageRow(UiStrings.dataStorageStt, ProtoStorageStats.formatBytes(s.sttBytes))
                StorageRow(UiStrings.dataStoragePrefs, ProtoStorageStats.formatBytes(s.prefsBytes))
                StorageRow(UiStrings.dataStorageBackups, ProtoStorageStats.formatBytes(s.backupsBytes))
            }
            if (needsAccess) {
                Spacer(Modifier.height(8.dp))
                Text(UiStrings.dataStoragePermissionHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                ProtoPrimaryButton(UiStrings.dataStorageGrantAccess, {
                    ProtoPersistentStorage.openAllFilesAccessSettings(ctx)
                }, Modifier.fillMaxWidth())
            }
            ProtoPrimaryButton(UiStrings.dataStorageOpenFolder, {
                val path = stats?.rootPath ?: ProtoPersistentStorage.rootDir(ctx).absolutePath
                runCatching {
                    val intent = Intent(Intent.ACTION_VIEW)
                    val uri = Uri.parse("content://com.android.externalstorage.documents/document/primary:Documents/PROTO")
                    intent.setDataAndType(uri, "*/*")
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    ctx.startActivity(intent)
                }.onFailure {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        ProtoPersistentStorage.openAllFilesAccessSettings(ctx)
                    }
                }
            }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun StorageRow(label: String, value: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
