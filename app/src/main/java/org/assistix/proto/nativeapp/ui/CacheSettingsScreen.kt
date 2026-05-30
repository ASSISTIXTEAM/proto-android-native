package org.assistix.proto.nativeapp.ui



import androidx.compose.foundation.background

import androidx.compose.foundation.layout.Arrangement

import androidx.compose.foundation.layout.Box

import androidx.compose.foundation.layout.Column

import androidx.compose.foundation.layout.Row

import androidx.compose.foundation.layout.Spacer

import androidx.compose.foundation.layout.fillMaxSize

import androidx.compose.foundation.layout.fillMaxWidth

import androidx.compose.foundation.layout.height

import androidx.compose.foundation.layout.padding

import androidx.compose.foundation.layout.size

import androidx.compose.foundation.rememberScrollState

import androidx.compose.foundation.shape.CircleShape

import androidx.compose.foundation.verticalScroll

import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.automirrored.filled.ArrowBack

import androidx.compose.material3.AlertDialog

import androidx.compose.material3.CircularProgressIndicator

import androidx.compose.material3.ExperimentalMaterial3Api

import androidx.compose.material3.Icon

import androidx.compose.material3.IconButton

import androidx.compose.material3.LinearProgressIndicator

import androidx.compose.material3.MaterialTheme

import androidx.compose.material3.Scaffold

import androidx.compose.material3.SnackbarHost

import androidx.compose.material3.SnackbarHostState

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

import androidx.compose.ui.draw.clip

import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.text.font.FontWeight

import androidx.compose.ui.unit.dp

import kotlinx.coroutines.Dispatchers

import kotlinx.coroutines.launch

import kotlinx.coroutines.withContext

import org.assistix.proto.nativeapp.ProtoApplication

import org.assistix.proto.nativeapp.data.ProtoCacheCategory

import org.assistix.proto.nativeapp.data.ProtoCacheManager

import org.assistix.proto.nativeapp.data.local.ProtoDatabase



@OptIn(ExperimentalMaterial3Api::class)

@Composable

fun CacheSettingsScreen(

    onBack: () -> Unit,

    onChatsCacheCleared: () -> Unit = {},

) {

    val ctx = LocalContext.current

    val app = ctx.applicationContext as ProtoApplication

    val cache = app.cache

    val scope = rememberCoroutineScope()

    val haptic = ProtoHaptics.rememberSender()

    val snackbarHostState = remember { SnackbarHostState() }

    var loading by remember { mutableStateOf(true) }

    var breakdown by remember { mutableStateOf<org.assistix.proto.nativeapp.data.ProtoCacheBreakdown?>(null) }

    var confirmClear by remember { mutableStateOf<ProtoCacheCategory?>(null) }

    var confirmClearAll by remember { mutableStateOf(false) }



    suspend fun afterCacheClear(clearedChats: Boolean) {
        loading = true
        val dbFile = org.assistix.proto.nativeapp.data.ProtoPersistentStorage.databaseFile(ctx)
        val dbBytes = if (dbFile.exists()) dbFile.length() else 0L
        breakdown = cache.scan(dbBytes)
        loading = false
        snackbarHostState.showSnackbar(
            if (clearedChats) UiStrings.cacheClearedReload else UiStrings.cacheCleared,
        )
        if (clearedChats) onChatsCacheCleared()
    }



    fun refresh() {

        scope.launch {

            loading = true

            val dbFile = org.assistix.proto.nativeapp.data.ProtoPersistentStorage.databaseFile(ctx)

            val dbBytes = if (dbFile.exists()) dbFile.length() else 0L

            breakdown = cache.scan(dbBytes)

            loading = false

        }

    }



    LaunchedEffect(Unit) { refresh() }



    if (confirmClear != null) {

        val cat = confirmClear!!

        val clearsChats = cat == ProtoCacheCategory.CHATS

        AlertDialog(

            onDismissRequest = { confirmClear = null },

            title = { Text(UiStrings.cacheClearConfirmTitle) },

            text = { Text(UiStrings.cacheClearConfirmBody(categoryLabel(cat).lowercase())) },

            confirmButton = {

                TextButton(

                    onClick = {

                        haptic(HapticKind.Action)

                        scope.launch {

                            if (clearsChats) {

                                withContext(Dispatchers.IO) {

                                    val dao = ProtoDatabase.get(ctx).dao()

                                    dao.clearMessages()

                                    dao.clearOutbox()

                                    dao.clearConversations()

                                }

                            } else {

                                cache.clear(cat)

                            }

                            confirmClear = null

                            afterCacheClear(clearsChats)

                        }

                    },

                ) { Text(UiStrings.cacheClearAction, color = MaterialTheme.colorScheme.error) }

            },

            dismissButton = {

                TextButton(onClick = { confirmClear = null }) { Text(UiStrings.cancel) }

            },

        )

    }



    if (confirmClearAll) {

        AlertDialog(

            onDismissRequest = { confirmClearAll = false },

            title = { Text(UiStrings.cacheClearAllTitle) },

            text = { Text(UiStrings.cacheClearAllBody) },

            confirmButton = {

                TextButton(

                    onClick = {

                        haptic(HapticKind.Action)

                        scope.launch {

                            withContext(Dispatchers.IO) {

                                val dao = ProtoDatabase.get(ctx).dao()

                                dao.clearMessages()

                                dao.clearOutbox()

                                dao.clearConversations()

                                cache.clearAllMedia()

                            }

                            confirmClearAll = false

                            afterCacheClear(clearedChats = true)

                        }

                    },

                ) { Text(UiStrings.cacheClearAction, color = MaterialTheme.colorScheme.error) }

            },

            dismissButton = {

                TextButton(onClick = { confirmClearAll = false }) { Text(UiStrings.cancel) }

            },

        )

    }



    Scaffold(

        snackbarHost = { SnackbarHost(snackbarHostState) },

        topBar = {

            TopAppBar(

                title = { Text(UiStrings.cacheStorageTitle) },

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

                .padding(horizontal = 16.dp, vertical = 12.dp),

            verticalArrangement = Arrangement.spacedBy(14.dp),

        ) {

            if (loading) {

                Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {

                    CircularProgressIndicator(color = ProtoOrange)

                }

            } else {

                val stats = breakdown!!

                Text(

                    UiStrings.cacheTotalFmt(ProtoCacheManager.formatBytes(stats.totalBytes)),

                    style = MaterialTheme.typography.headlineSmall,

                    fontWeight = FontWeight.Bold,

                )

                SettingsInfoText(UiStrings.cacheAvatarsHint)

                Spacer(Modifier.height(4.dp))

                CacheCategoryRow(UiStrings.cacheCategoryChats, stats.chatsBytes, stats.percentOf(ProtoCacheCategory.CHATS)) {

                    confirmClear = ProtoCacheCategory.CHATS

                }

                CacheCategoryRow(UiStrings.cacheCategoryPhotos, stats.photosBytes, stats.percentOf(ProtoCacheCategory.PHOTOS)) {

                    confirmClear = ProtoCacheCategory.PHOTOS

                }

                CacheCategoryRow(UiStrings.cacheCategoryVideos, stats.videosBytes, stats.percentOf(ProtoCacheCategory.VIDEOS)) {

                    confirmClear = ProtoCacheCategory.VIDEOS

                }

                CacheCategoryRow(UiStrings.cacheCategoryAudio, stats.audioBytes, stats.percentOf(ProtoCacheCategory.AUDIO)) {

                    confirmClear = ProtoCacheCategory.AUDIO

                }

                CacheCategoryRow(UiStrings.cacheCategoryFiles, stats.filesBytes, stats.percentOf(ProtoCacheCategory.FILES)) {

                    confirmClear = ProtoCacheCategory.FILES

                }

                if (stats.otherBytes > 0) {

                    CacheCategoryRow(UiStrings.cacheCategoryOther, stats.otherBytes, stats.percentOf(ProtoCacheCategory.OTHER)) {

                        confirmClear = ProtoCacheCategory.OTHER

                    }

                }

                Spacer(Modifier.height(8.dp))

                ProtoGhostButton(UiStrings.cacheClearAll, { confirmClearAll = true }, Modifier.fillMaxWidth())

            }

        }

    }

}



@Composable

private fun CacheCategoryRow(

    label: String,

    bytes: Long,

    percent: Int,

    onClear: () -> Unit,

) {

    Column(

        Modifier

            .fillMaxWidth()

            .clip(ProtoShapes.card)

            .background(MaterialTheme.colorScheme.surface)

            .padding(14.dp),

    ) {

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {

            Column(Modifier.weight(1f)) {

                Text(label, fontWeight = FontWeight.SemiBold)

                Text(

                    "${ProtoCacheManager.formatBytes(bytes)} · $percent%",

                    style = MaterialTheme.typography.bodySmall,

                    color = MaterialTheme.colorScheme.onSurfaceVariant,

                )

            }

            val haptic = ProtoHaptics.rememberSender()

            TextButton(

                onClick = {

                    haptic(HapticKind.Tap)

                    onClear()

                },

                enabled = bytes > 0,

            ) {

                Text(UiStrings.cacheClearCategory)

            }

        }

        Spacer(Modifier.height(8.dp))

        LinearProgressIndicator(

            progress = { percent / 100f },

            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),

            color = ProtoOrange,

            trackColor = MaterialTheme.colorScheme.surfaceVariant,

        )

    }

}



private fun categoryLabel(cat: ProtoCacheCategory): String =

    when (cat) {

        ProtoCacheCategory.CHATS -> UiStrings.cacheCategoryChats

        ProtoCacheCategory.PHOTOS -> UiStrings.cacheCategoryPhotos

        ProtoCacheCategory.VIDEOS -> UiStrings.cacheCategoryVideos

        ProtoCacheCategory.AUDIO -> UiStrings.cacheCategoryAudio

        ProtoCacheCategory.FILES -> UiStrings.cacheCategoryFiles

        ProtoCacheCategory.OTHER -> UiStrings.cacheCategoryOther

    }

