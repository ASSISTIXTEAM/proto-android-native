package org.assistix.proto.nativeapp.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import org.assistix.proto.nativeapp.R
import org.assistix.proto.nativeapp.update.AppUpdateInfo
import org.assistix.proto.nativeapp.update.AppUpdatePhase
import org.assistix.proto.nativeapp.update.ProtoAppUpdateManager

private const val MANDATORY_UPDATE_SITE_APK_URL = "https://proto.su/api/download-apk.php"
private const val MANDATORY_UPDATE_SUPPORT_EMAIL = "team@proto.su"

@Composable
fun MandatoryUpdateScreen(
    updateManager: ProtoAppUpdateManager,
    info: AppUpdateInfo,
) {
    val phase by updateManager.phase.collectAsState()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var downloadStarted by remember(info.versionCode) { mutableIntStateOf(0) }
    var helpExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(info.versionCode) {
        if (downloadStarted != 0) return@LaunchedEffect
        downloadStarted = 1
        when (phase) {
            is AppUpdatePhase.Ready, is AppUpdatePhase.Downloading -> Unit
            else -> updateManager.downloadUpdate(info, silent = true)
        }
    }

    val subtitle =
        info.requiredMessage.ifBlank {
            UiStrings.updateMandatoryDefaultBody(info.versionName)
        }
    val targetProgress =
        when (val p = phase) {
            is AppUpdatePhase.Downloading -> p.progress.coerceIn(0f, 1f)
            is AppUpdatePhase.Ready -> 1f
            else -> 0.06f
        }
    val animatedProgress by animateFloatAsState(targetProgress, tween(280), label = "apkProgress")
    val percent = (animatedProgress * 100).toInt().coerceIn(0, 100)
    val status =
        when (phase) {
            is AppUpdatePhase.Ready -> UiStrings.updateReadyShort
            is AppUpdatePhase.Downloading -> UiStrings.updateDownloadingProgress(percent)
            is AppUpdatePhase.Checking -> UiStrings.updateChecking
            is AppUpdatePhase.Error -> UiStrings.updateDownloadFailed
            else -> UiStrings.updateMandatoryPreparing
        }
    val errorDetail =
        when (val p = phase) {
            is AppUpdatePhase.Error ->
                when (p.message) {
                    "network" -> UiStrings.updateMandatoryErrorNetwork
                    "download_failed", "apk_corrupt" -> UiStrings.updateMandatoryErrorCorrupt
                    else -> null
                }
            else -> null
        }
    val apkUrl = info.apkUrl.ifBlank { MANDATORY_UPDATE_SITE_APK_URL }
    val helpWebUrl = info.helpUrl.ifBlank { "https://proto.su/download.html#help-install" }
    val supportEmail = info.supportEmail.ifBlank { MANDATORY_UPDATE_SUPPORT_EMAIL }
    val ready = phase is AppUpdatePhase.Ready
    val busy = phase is AppUpdatePhase.Downloading || phase is AppUpdatePhase.Checking
    val failed = phase is AppUpdatePhase.Error

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0C)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 360.dp)
                .padding(horizontal = 28.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            UpdateBrandIcon(forDarkBackground = true, size = 156.dp)
            Spacer(Modifier.height(28.dp))
            Text(
                UiStrings.updateMandatoryVersionChip(info.versionName),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF9CA3AF),
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
            )
            Spacer(Modifier.height(28.dp))
            if (!ready) {
                LinearProgressIndicator(
                    progress = { animatedProgress.coerceIn(0.04f, 1f) },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(999.dp)),
                    color = ProtoOrange,
                    trackColor = Color(0xFF252528),
                )
                Spacer(Modifier.height(12.dp))
            }
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = if (failed) ProtoOrange else Color(0xFFCBD5E1),
                textAlign = TextAlign.Center,
            )
            errorDetail?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, style = MaterialTheme.typography.labelMedium, color = ProtoOrange, textAlign = TextAlign.Center)
            }
            if (info.apkSizeBytes > 0 && !ready) {
                Spacer(Modifier.height(4.dp))
                Text(
                    UiStrings.updateApkSizeMb(info.apkSizeBytes),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF6B7280),
                )
            }
            Spacer(Modifier.height(28.dp))
            when {
                ready -> {
                    ProtoPrimaryButton(
                        UiStrings.updateInstall,
                        {
                            if (!updateManager.canInstallPackages()) {
                                updateManager.openInstallPermissionSettings()
                                Toast.makeText(ctx, UiStrings.updateAllowInstall, Toast.LENGTH_LONG).show()
                            } else {
                                updateManager.installPendingApk()
                            }
                        },
                        Modifier.fillMaxWidth(),
                    )
                    if (!updateManager.canInstallPackages()) {
                        TextButton(onClick = { updateManager.openInstallPermissionSettings() }) {
                            Text(UiStrings.updateAllowInstall, color = ProtoOrange)
                        }
                    }
                }
                failed -> {
                    ProtoPrimaryButton(
                        UiStrings.updateRetry,
                        { scope.launch { updateManager.downloadUpdate(info, silent = false) } },
                        Modifier.fillMaxWidth(),
                    )
                }
                busy -> {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clip(ProtoShapes.button)
                            .background(Color(0xFF16161A)),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(Modifier.size(26.dp), color = ProtoOrange, strokeWidth = 2.5.dp)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = { helpExpanded = !helpExpanded }) {
                Text(
                    if (helpExpanded) UiStrings.updateMandatoryHelpClose else UiStrings.updateHelpExpand,
                    color = Color(0xFF9CA3AF),
                )
            }
            AnimatedVisibility(
                visible = helpExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                UpdateHelpPanel(
                    showErrorHint = failed,
                    onOpenSite = { openUrlInBrowser(ctx, apkUrl) },
                    onOpenHelpWeb = { openUrlInBrowser(ctx, helpWebUrl) },
                    onOpenEmail = { openUrlInBrowser(ctx, "mailto:$supportEmail") },
                )
            }
        }
    }
}

@Composable
fun AppUpdatePromptDialog(
    updateManager: ProtoAppUpdateManager,
    info: AppUpdateInfo,
    onDismiss: () -> Unit,
) {
    val phase by updateManager.phase.collectAsState()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val ready = phase is AppUpdatePhase.Ready
    val busy = phase is AppUpdatePhase.Downloading

    Dialog(
        onDismissRequest = {
            if (!info.force) {
                scope.launch {
                    updateManager.dismissPrompt(info.versionCode)
                    onDismiss()
                }
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.88f).widthIn(max = 340.dp),
            shape = RoundedCornerShape(28.dp),
            color = Color(0xFF121216),
        ) {
            Column(
                Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                UpdateBrandIcon(forDarkBackground = true, size = 120.dp)
                Spacer(Modifier.height(20.dp))
                Text(
                    UiStrings.updateOptionalHeadline(info.versionName),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    UiStrings.updateDialogBody,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF9CA3AF),
                    textAlign = TextAlign.Center,
                )
                if (busy) {
                    Spacer(Modifier.height(20.dp))
                    val anim by animateFloatAsState(
                        (phase as AppUpdatePhase.Downloading).progress.coerceIn(0f, 1f),
                        label = "optProg",
                    )
                    LinearProgressIndicator(
                        progress = { anim.coerceIn(0.04f, 1f) },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(999.dp)),
                        color = ProtoOrange,
                        trackColor = Color(0xFF2A2A30),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        UiStrings.updateDownloadingProgress((anim * 100).toInt()),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFFCBD5E1),
                    )
                } else if (ready) {
                    Spacer(Modifier.height(12.dp))
                    Text(UiStrings.updateReadyShort, color = ProtoOrange, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.height(22.dp))
                val primaryLabel =
                    when {
                        ready -> UiStrings.updateInstall
                        busy -> UiStrings.updateDownloading
                        else -> UiStrings.updateDownload
                    }
                ProtoPrimaryButton(
                    primaryLabel,
                    {
                        scope.launch {
                            when (phase) {
                                is AppUpdatePhase.Ready -> {
                                    if (!updateManager.canInstallPackages()) {
                                        updateManager.openInstallPermissionSettings()
                                        Toast.makeText(ctx, UiStrings.updateAllowInstall, Toast.LENGTH_LONG).show()
                                    } else if (updateManager.installPendingApk()) {
                                        onDismiss()
                                    }
                                }
                                is AppUpdatePhase.Downloading -> Unit
                                else -> updateManager.downloadUpdate(info)
                            }
                        }
                    },
                    Modifier.fillMaxWidth(),
                )
                if (!info.force) {
                    TextButton(
                        onClick = {
                            scope.launch {
                                updateManager.dismissPrompt(info.versionCode)
                                onDismiss()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(UiStrings.updateLater, color = Color(0xFF9CA3AF))
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsAppUpdateSection(
    updateManager: ProtoAppUpdateManager,
    autoUpdate: Boolean,
    onAutoUpdateChange: (Boolean) -> Unit,
    blockIndex: Int,
    contentVisible: Boolean,
    reduceMotion: Boolean,
) {
    val phase by updateManager.phase.collectAsState()
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val dark = isSystemInDarkTheme()

    SettingsAnimatedGroup(blockIndex, contentVisible, reduceMotion) {
        SettingsSection(UiStrings.settingsUpdates) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                UpdateBrandIcon(forDarkBackground = dark, size = 48.dp)
                Column(Modifier.weight(1f)) {
                    Text(
                        UiStrings.settingsVersion(updateManager.installedVersionName()),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        updateSettingsStatus(phase),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (phase is AppUpdatePhase.Downloading) {
                val prog = (phase as AppUpdatePhase.Downloading).progress
                val anim by animateFloatAsState(prog.coerceIn(0f, 1f), label = "settingsProg")
                LinearProgressIndicator(
                    progress = { anim.coerceIn(0.04f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    color = ProtoOrange,
                )
            }
            Spacer(Modifier.height(12.dp))
            SettingsToggleRow(
                title = UiStrings.updateAutoBackground,
                subtitle = UiStrings.updateAutoBackgroundHint,
                checked = autoUpdate,
                onCheckedChange = onAutoUpdateChange,
            )
            Spacer(Modifier.height(12.dp))
            val action =
                when (val p = phase) {
                    is AppUpdatePhase.Ready ->
                        UiStrings.updateInstall to {
                            if (!updateManager.canInstallPackages()) {
                                updateManager.openInstallPermissionSettings()
                                Toast.makeText(ctx, UiStrings.updateAllowInstall, Toast.LENGTH_LONG).show()
                            } else {
                                updateManager.installPendingApk()
                            }
                            Unit
                        }
                    is AppUpdatePhase.Available ->
                        UiStrings.updateDownloadVersion(p.info.versionName) to {
                            scope.launch { updateManager.downloadUpdate(p.info) }
                            Unit
                        }
                    is AppUpdatePhase.Error ->
                        UiStrings.updateRetry to {
                            scope.launch { updateManager.refresh(silent = false) }
                            Unit
                        }
                    else ->
                        UiStrings.updateCheckNow to {
                            scope.launch { updateManager.refresh(silent = false) }
                            Unit
                        }
                }
            ProtoPrimaryButton(action.first, action.second, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun UpdateBrandIcon(
    forDarkBackground: Boolean,
    size: androidx.compose.ui.unit.Dp,
) {
    val res =
        if (forDarkBackground) {
            R.drawable.proto_update_icon_light
        } else {
            R.drawable.proto_update_icon
        }
    Image(
        painter = painterResource(res),
        contentDescription = null,
        modifier = Modifier.size(size),
    )
}

@Composable
private fun UpdateHelpPanel(
    showErrorHint: Boolean,
    onOpenSite: () -> Unit,
    onOpenHelpWeb: () -> Unit,
    onOpenEmail: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF141418))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (showErrorHint) {
            Text(UiStrings.updateMandatoryHelpErrorHint, color = ProtoOrange, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
        }
        ProtoGhostButton(UiStrings.updateMandatoryHelpDownloadBtn, onOpenSite, Modifier.fillMaxWidth())
        TextButton(onClick = onOpenHelpWeb) {
            Text(UiStrings.updateMandatoryHelpOpenWeb, color = ProtoOrange)
        }
        TextButton(onClick = onOpenEmail) {
            Text(UiStrings.updateMandatoryHelpEmailBtn, color = Color(0xFF9CA3AF))
        }
    }
}

private fun updateSettingsStatus(phase: AppUpdatePhase): String =
    when (phase) {
        is AppUpdatePhase.Checking -> UiStrings.updateChecking
        is AppUpdatePhase.Available -> UiStrings.updateAvailableFmt(phase.info.versionName)
        is AppUpdatePhase.Downloading -> UiStrings.updateDownloadingProgress((phase.progress * 100).toInt())
        is AppUpdatePhase.Ready -> UiStrings.updateReadyShort
        is AppUpdatePhase.UpToDate -> UiStrings.updateUpToDate
        is AppUpdatePhase.Error ->
            when (phase.message) {
                "network" -> UiStrings.networkUnavailable
                "download_failed", "apk_corrupt" -> UiStrings.updateDownloadFailed
                else -> UiStrings.updateCheckFailed
            }
        else -> UiStrings.updateTapCheck
    }
