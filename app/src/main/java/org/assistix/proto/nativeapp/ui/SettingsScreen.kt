package org.assistix.proto.nativeapp.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Storage
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.assistix.proto.nativeapp.data.ProtoAppPreferences
import org.assistix.proto.nativeapp.data.ProtoSessionStore
import org.assistix.proto.nativeapp.data.ProtoThemeMode
import org.assistix.proto.nativeapp.data.ProtoThemeStore

@Composable
fun SettingsTab(
    session: ProtoSessionStore,
    themeStore: ProtoThemeStore,
    prefs: ProtoAppPreferences,
    stt: org.assistix.proto.nativeapp.data.ProtoSttCoordinator,
    updateManager: org.assistix.proto.nativeapp.update.ProtoAppUpdateManager,
    onLogout: () -> Unit,
    onOpenDevices: () -> Unit = {},
    onOpenCache: () -> Unit = {},
    onOpenDataStorage: () -> Unit = {},
    onOpenOnboarding: () -> Unit = {},
    onOpenQrHub: () -> Unit = {},
) {
    val mode by themeStore.mode.collectAsState(initial = ProtoThemeMode.DARK)
    val msgNotif by prefs.messageNotifications.collectAsState(initial = true)
    val callNotif by prefs.callNotifications.collectAsState(initial = true)
    val readRx by prefs.showReadReceipts.collectAsState(initial = true)
    val typing by prefs.showTyping.collectAsState(initial = true)
    val autoDl by prefs.autoDownload.collectAsState(initial = false)
    val autoTranslate by prefs.autoTranslateChats.collectAsState(initial = false)
    val notifyMentionsOnly by prefs.notifyMentionsOnly.collectAsState(initial = false)
    val textScale by prefs.textSizeScale.collectAsState(initial = 1f)
    val reduceMotion by prefs.reduceMotionEnabled.collectAsState(initial = false)
    val languageCode by prefs.languageCodeFlow.collectAsState(initial = "en")
    val autoAppUpdate by prefs.autoAppUpdate.collectAsState(initial = true)
    val linkPreviews by prefs.linkPreviewsInChat.collectAsState(initial = true)
    val sendOnEnter by prefs.sendOnEnter.collectAsState(initial = false)
    val compactChatList by prefs.compactChatList.collectAsState(initial = false)
    val hapticFeedback by prefs.hapticFeedback.collectAsState(initial = true)
    val voiceSpeedIdx by prefs.voicePlaybackSpeedIdx.collectAsState(initial = 0)
    val sortUnreadFirst by prefs.sortUnreadFirst.collectAsState(initial = false)
    val sttWifiHeavy by prefs.sttWifiOnlyHeavyModels.collectAsState(initial = true)
    val sttChargingOnly by prefs.sttOnlyWhenCharging.collectAsState(initial = false)
    val sttMaxQueue by prefs.sttMaxQueuePerBurst.collectAsState(initial = 20)
    var sliderScale by remember { mutableFloatStateOf(textScale) }
    var contentVisible by remember { mutableStateOf(reduceMotion) }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var showDeleteAccount by remember { mutableStateOf(false) }
    var deletePassword by remember { mutableStateOf("") }
    var exporting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val app = ctx.applicationContext as org.assistix.proto.nativeapp.ProtoApplication

    LaunchedEffect(textScale) { sliderScale = textScale }
    LaunchedEffect(Unit) {
        updateManager.refresh(silent = true)
    }
    LaunchedEffect(reduceMotion) {
        if (!reduceMotion) {
            contentVisible = false
            try {
                kotlinx.coroutines.delay(40)
            } finally {
                contentVisible = true
            }
        } else {
            contentVisible = true
        }
    }

    val versionName = org.assistix.proto.nativeapp.BuildConfig.VERSION_NAME

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
            SettingsAnimatedGroup(0, contentVisible, reduceMotion) {
                SettingsSection(UiStrings.language) {
                    ProtoLanguageDropdown(
                        selectedCode = languageCode,
                        onSelect = { code -> scope.launch { prefs.setLanguageCode(code) } },
                    )
                }
            }

            SettingsAnimatedGroup(1, contentVisible, reduceMotion) {
                SettingsSection(UiStrings.appearance) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeModeChip(UiStrings.darkTheme, mode == ProtoThemeMode.DARK) {
                            scope.launch { themeStore.setMode(ProtoThemeMode.DARK) }
                        }
                        ThemeModeChip(UiStrings.onboardThemeLight, mode == ProtoThemeMode.LIGHT) {
                            scope.launch { themeStore.setMode(ProtoThemeMode.LIGHT) }
                        }
                        ThemeModeChip(UiStrings.onboardThemeSystem, mode == ProtoThemeMode.SYSTEM) {
                            scope.launch { themeStore.setMode(ProtoThemeMode.SYSTEM) }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    SettingsSlider(UiStrings.textSize, sliderScale, 0.85f, 1.25f, reduceMotion) {
                        sliderScale = it
                        scope.launch { prefs.setTextSizeScale(it) }
                    }
                    SettingsGroupDivider()
                    SettingsToggleRow(UiStrings.reduceMotion, "", reduceMotion, onCheckedChange = { on ->
                        scope.launch { prefs.setReduceMotion(on) }
                    })
                    SettingsGroupDivider()
                    SettingsToggleRow(UiStrings.settingsHapticFeedback, "", hapticFeedback, onCheckedChange = { on ->
                        scope.launch { prefs.setHapticFeedback(on) }
                    })
                }
            }

            SettingsAnimatedGroup(2, contentVisible, reduceMotion) {
                SettingsSection(UiStrings.settingsChatsSection) {
                    SettingsToggleRow(UiStrings.settingsLinkPreviews, "", linkPreviews, onCheckedChange = { on ->
                        scope.launch { prefs.setLinkPreviewsInChat(on) }
                    })
                    SettingsGroupDivider()
                    SettingsToggleRow(UiStrings.settingsCompactChatList, "", compactChatList, onCheckedChange = { on ->
                        scope.launch { prefs.setCompactChatList(on) }
                    })
                    SettingsGroupDivider()
                    SettingsToggleRow(UiStrings.settingsSendOnEnter, "", sendOnEnter, onCheckedChange = { on ->
                        scope.launch { prefs.setSendOnEnter(on) }
                    })
                    SettingsGroupDivider()
                    SettingsToggleRow(UiStrings.settingsSortUnreadFirst, "", sortUnreadFirst, onCheckedChange = { on ->
                        scope.launch { prefs.setSortUnreadFirst(on) }
                    })
                    SettingsGroupDivider()
                    SettingsNavRow(
                        title = UiStrings.settingsVoiceSpeed,
                        subtitle = UiStrings.voiceSpeedLabelFmt(voiceSpeedIdx),
                        onClick = {
                            scope.launch {
                                prefs.setVoicePlaybackSpeedIdx((voiceSpeedIdx + 1) % 3)
                            }
                        },
                    )
                    SettingsGroupDivider()
                    SettingsToggleRow(UiStrings.autoTranslateChats, "", autoTranslate, onCheckedChange = { on ->
                        scope.launch { prefs.setAutoTranslateChats(on) }
                    })
                }
            }

            SettingsAnimatedGroup(3, contentVisible, reduceMotion) {
                SettingsSection(UiStrings.notifications) {
                    SettingsToggleRow(UiStrings.messageNotifications, "", msgNotif, onCheckedChange = { on ->
                        scope.launch { prefs.setMessageNotifications(on) }
                    })
                    SettingsGroupDivider()
                    SettingsToggleRow(UiStrings.callNotifications, "", callNotif, onCheckedChange = { on ->
                        scope.launch { prefs.setCallNotifications(on) }
                    })
                    SettingsGroupDivider()
                    SettingsToggleRow(UiStrings.notifyMentionsOnly, "", notifyMentionsOnly, onCheckedChange = { on ->
                        scope.launch {
                            prefs.setNotifyMentionsOnly(on)
                            session.token()?.let { t ->
                                org.assistix.proto.nativeapp.data.ProtoClientPrefsSync.pushNotifyMentionsOnly(t, app.api, on)
                            }
                        }
                    })
                }
            }

            SettingsAnimatedGroup(4, contentVisible, reduceMotion) {
                SettingsSection(UiStrings.privacy) {
                    SettingsToggleRow(UiStrings.readReceiptsSetting, "", readRx, onCheckedChange = { on ->
                        scope.launch { prefs.setReadReceipts(on) }
                    })
                    SettingsGroupDivider()
                    SettingsToggleRow(UiStrings.typingIndicatorsSetting, "", typing, onCheckedChange = { on ->
                        scope.launch { prefs.setTypingIndicators(on) }
                    })
                }
            }

            SettingsAnimatedGroup(5, contentVisible, reduceMotion) {
                SettingsSection(UiStrings.settingsAssistixSection) { SettingsProtoAiCard() }
            }

            SettingsAnimatedGroup(6, contentVisible, reduceMotion) {
                SettingsSection(UiStrings.settingsData) {
                    SettingsNavRow(
                        UiStrings.cacheStorageTitle,
                        UiStrings.messagesCached,
                        onOpenCache,
                        icon = Icons.Default.Storage,
                    )
                    SettingsGroupDivider()
                    SettingsNavRow(
                        UiStrings.settingsDataStorage,
                        UiStrings.dataStorageHint,
                        onOpenDataStorage,
                        icon = Icons.Default.Storage,
                    )
                    SettingsGroupDivider()
                    SettingsNavRow(
                        UiStrings.activeSessions,
                        UiStrings.scanQrLinkHint,
                        onOpenDevices,
                        icon = Icons.Default.Devices,
                    )
                }
            }

            SettingsAnimatedGroup(7, contentVisible, reduceMotion) {
                SettingsSection(UiStrings.settingsNetworkSection) {
                    SettingsToggleRow(UiStrings.autoDownloadMedia, UiStrings.settingsNetworkHint, autoDl, onCheckedChange = { on ->
                        scope.launch { prefs.setAutoDownload(on) }
                    })
                    SettingsGroupDivider()
                    Text(
                        UiStrings.callsWebRtc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                    )
                }
            }

            SettingsAnimatedGroup(8, contentVisible, reduceMotion) {
                SettingsSection(UiStrings.sttSettings) {
                    val pack by stt.packState.collectAsState()
                    val sttProgress by stt.downloadProgress.collectAsState()
                    val modelLevel by stt.modelLevel.collectAsState()
                    val deviceClass = remember { stt.deviceCapability() }
                    val recommendedLevel = remember(deviceClass) { stt.recommendedLevelForDevice() }
                    Text(
                        UiStrings.sttDeviceOnly,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        UiStrings.storagePersistentHint,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    SttModelPickerSection(
                        stt = stt,
                        modelLevel = modelLevel,
                        deviceClass = deviceClass,
                        recommendedLevel = recommendedLevel,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        when (pack) {
                            org.assistix.proto.nativeapp.data.ProtoSttCoordinator.PackState.READY -> UiStrings.sttPackReady
                            org.assistix.proto.nativeapp.data.ProtoSttCoordinator.PackState.DOWNLOADING -> UiStrings.sttPackDownloading
                            else -> UiStrings.sttPackMissing
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (pack == org.assistix.proto.nativeapp.data.ProtoSttCoordinator.PackState.DOWNLOADING) {
                        Spacer(Modifier.height(8.dp))
                        val p = if (sttProgress >= 0f) sttProgress.coerceIn(0f, 1f) else null
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { p ?: 0.15f },
                            modifier = Modifier.fillMaxWidth(),
                            color = ProtoOrange,
                        )
                    }
                    if (pack != org.assistix.proto.nativeapp.data.ProtoSttCoordinator.PackState.DOWNLOADING) {
                        Spacer(Modifier.height(10.dp))
                        ProtoPrimaryButton(
                            UiStrings.sttDownloadPack,
                            {
                                scope.launch {
                                    val ok = runCatching { stt.ensurePackDownloaded() }.getOrDefault(false)
                                    if (!ok && sttWifiHeavy) {
                                        android.widget.Toast
                                            .makeText(ctx, UiStrings.sttDownloadWifiBlocked, android.widget.Toast.LENGTH_LONG)
                                            .show()
                                    }
                                }
                            },
                            Modifier.fillMaxWidth(),
                        )
                    }
                    SettingsGroupDivider()
                    SettingsToggleRow(
                        UiStrings.sttWifiOnlyHeavy,
                        UiStrings.sttWifiOnlyHeavyHint,
                        sttWifiHeavy,
                        onCheckedChange = { on -> scope.launch { prefs.setSttWifiOnlyHeavyModels(on) } },
                    )
                    SettingsGroupDivider()
                    SettingsToggleRow(
                        UiStrings.sttOnlyWhenCharging,
                        UiStrings.sttOnlyWhenChargingHint,
                        sttChargingOnly,
                        onCheckedChange = { on -> scope.launch { prefs.setSttOnlyWhenCharging(on) } },
                    )
                    SettingsGroupDivider()
                    Text(UiStrings.sttMaxQueueLabel, style = MaterialTheme.typography.bodyMedium)
                    Text(UiStrings.sttMaxQueueHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Slider(
                        value = sttMaxQueue.toFloat(),
                        onValueChange = { v -> scope.launch { prefs.setSttMaxQueuePerBurst(v.toInt()) } },
                        valueRange = 3f..50f,
                        steps = 46,
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(thumbColor = ProtoOrange, activeTrackColor = ProtoOrange),
                    )
                    Text("$sttMaxQueue", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }

            SettingsAppUpdateSection(
                updateManager = updateManager,
                autoUpdate = autoAppUpdate,
                onAutoUpdateChange = { on -> scope.launch { prefs.setAutoAppUpdate(on) } },
                blockIndex = 9,
                contentVisible = contentVisible,
                reduceMotion = reduceMotion,
            )

            SettingsAnimatedGroup(10, contentVisible, reduceMotion) {
                SettingsSection(UiStrings.settingsHelpSection) {
                    SettingsNavRow(
                        UiStrings.showOnboardingAgain,
                        UiStrings.settingsReplayOnboardingHint,
                        onOpenOnboarding,
                        icon = Icons.Default.Help,
                    )
                    SettingsGroupDivider()
                    SettingsNavRow(UiStrings.qrHubTitle, "", onOpenQrHub, icon = Icons.Default.QrCode2)
                }
            }

            SettingsAnimatedGroup(11, contentVisible, reduceMotion) {
                SettingsSection(UiStrings.legalSection) {
                    SettingsNavRow(
                        UiStrings.openPrivacyPolicy,
                        "",
                        { openLegalUrl(ctx, org.assistix.proto.nativeapp.ProtoLegal.PRIVACY_URL) },
                        icon = Icons.Default.PrivacyTip,
                    )
                    SettingsGroupDivider()
                    SettingsNavRow(
                        UiStrings.openRules,
                        "",
                        { openLegalUrl(ctx, org.assistix.proto.nativeapp.ProtoLegal.RULES_URL) },
                        icon = Icons.Default.Gavel,
                    )
                    SettingsGroupDivider()
                    SettingsNavRow(
                        UiStrings.supportEmail,
                        UiStrings.settingsAboutBody,
                        {
                            ctx.startActivity(
                                android.content.Intent(
                                    android.content.Intent.ACTION_SENDTO,
                                    android.net.Uri.parse("mailto:${org.assistix.proto.nativeapp.ProtoLegal.SUPPORT_EMAIL}"),
                                ),
                            )
                        },
                    )
                }
            }

            SettingsAnimatedGroup(12, contentVisible, reduceMotion) {
                SettingsSection(UiStrings.settingsGdprSection) {
                    Text(
                        UiStrings.settingsGdprBody,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(14.dp))
                    ProtoPrimaryButton(
                        if (exporting) UiStrings.settingsExporting else UiStrings.settingsExportData,
                        {
                            scope.launch {
                                val t = session.token() ?: return@launch
                                exporting = true
                                val j = withContext(kotlinx.coroutines.Dispatchers.IO) { app.api.exportMyData(t) }
                                exporting = false
                                if (j != null) {
                                    val out =
                                        java.io.File(
                                            org.assistix.proto.nativeapp.data.ProtoPersistentStorage.exportsDir(ctx),
                                            "proto-export-${System.currentTimeMillis()}.json",
                                        )
                                    out.writeText(j.toString(2))
                                    android.widget.Toast.makeText(
                                        ctx,
                                        String.format(java.util.Locale.getDefault(), UiStrings.settingsExportSavedFmt, out.absolutePath),
                                        android.widget.Toast.LENGTH_LONG,
                                    ).show()
                                } else {
                                    android.widget.Toast.makeText(ctx, UiStrings.saveFailed, android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    ProtoDangerButton(UiStrings.settingsDeleteAccount, { showDeleteAccount = true }, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    ProtoDangerButton(UiStrings.signOut, { showLogoutConfirm = true }, Modifier.fillMaxWidth())
                }
            }

            SettingsVersionFooter(versionName)
            Spacer(Modifier.height(20.dp))
        }

    if (showLogoutConfirm) {
            AlertDialog(
                onDismissRequest = { showLogoutConfirm = false },
                title = { Text(UiStrings.logoutConfirmTitle) },
                text = { Text(UiStrings.logoutConfirmBody) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showLogoutConfirm = false
                            onLogout()
                        },
                    ) {
                        Text(UiStrings.signOut, color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showLogoutConfirm = false }) {
                        Text(UiStrings.cancel)
                    }
                },
            )
        }

        if (showDeleteAccount) {
            AlertDialog(
                onDismissRequest = { showDeleteAccount = false },
                title = { Text(UiStrings.settingsDeleteAccountTitle) },
                text = {
                    Column {
                        Text(UiStrings.settingsDeleteAccountBody)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = deletePassword,
                            onValueChange = { deletePassword = it },
                            label = { Text(UiStrings.password) },
                            singleLine = true,
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                val t = session.token() ?: return@launch
                                val ok =
                                    withContext(Dispatchers.IO) {
                                        app.api.deleteMyAccount(t, deletePassword)
                                    }
                                if (ok) {
                                    showDeleteAccount = false
                                    deletePassword = ""
                                    onLogout()
                                } else {
                                    android.widget.Toast.makeText(ctx, UiStrings.saveFailed, android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                    ) {
                        Text(UiStrings.settingsDeleteConfirm, color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteAccount = false }) { Text(UiStrings.cancel) }
                },
            )
        }
}

@Composable
internal fun SettingsInfoText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
    )
}

@Composable
internal fun SettingsBlock(
    index: Int,
    visible: Boolean,
    reduceMotion: Boolean,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter =
            fadeIn(ProtoMotion.fade(reduceMotion, 380)) +
                slideInVertically(
                    animationSpec = ProtoMotion.slide(reduceMotion, 420),
                    initialOffsetY = { ProtoMotion.slideOffset(index) },
                ),
        exit =
            fadeOut(ProtoMotion.fade(reduceMotion, 120)) +
                slideOutVertically(
                    animationSpec = ProtoMotion.slide(reduceMotion, 120),
                    targetOffsetY = { it / 3 },
                ),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(ProtoShapes.field)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            content()
        }
    }
}

@Composable
internal fun SettingsSectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 4.dp, top = 2.dp),
    )
}

@Composable
private fun SettingsSwitch(
    label: String,
    checked: Boolean,
    reduceMotion: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    val haptic = ProtoHaptics.rememberSender()
    val rowBg by animateColorAsState(
        targetValue =
            if (checked) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
            } else {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
            },
        animationSpec = ProtoMotion.gentleColorSpring(reduceMotion),
        label = "settingsRowBg",
    )
    val rowScale by animateFloatAsState(
        targetValue = if (checked) 1f else 0.992f,
        animationSpec = ProtoMotion.softSpring(reduceMotion),
        label = "settingsRowScale",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = rowScale
                scaleY = rowScale
            }
            .clip(ProtoShapes.field)
            .background(rowBg)
            .clickable {
                haptic(HapticKind.Toggle)
                onChecked(!checked)
            }
            .padding(horizontal = 14.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(
            checked = checked,
            onCheckedChange = {
                haptic(HapticKind.Toggle)
                onChecked(it)
            },
            colors =
                SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
        )
    }
}

@Composable
private fun SettingsSlider(
    label: String,
    value: Float,
    min: Float,
    max: Float,
    reduceMotion: Boolean,
    onChange: (Float) -> Unit,
) {
    val display by animateFloatAsState(
        targetValue = value,
        animationSpec = ProtoMotion.gentleSpring(reduceMotion),
        label = "sliderValue",
    )
    val percentLabel = "${(display * 100).toInt()}%"
    Column(
        Modifier
            .fillMaxWidth()
            .clip(ProtoShapes.field)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            AnimatedContent(
                targetState = percentLabel,
                transitionSpec = {
                    fadeIn(ProtoMotion.fade(reduceMotion, 180)) togetherWith
                        fadeOut(ProtoMotion.fade(reduceMotion, 120))
                },
                label = "sliderPercent",
            ) { pct ->
                Text(
                    pct,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = min..max,
            colors =
                SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
        )
    }
}

@Composable
private fun ThemeModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val haptic = ProtoHaptics.rememberSender()
    FilterChip(
        selected = selected,
        onClick = {
            haptic(HapticKind.Toggle)
            onClick()
        },
        label = { Text(label, maxLines = 1) },
        colors =
            FilterChipDefaults.filterChipColors(
                selectedContainerColor = ProtoOrange.copy(0.22f),
                selectedLabelColor = MaterialTheme.colorScheme.primary,
            ),
    )
}
