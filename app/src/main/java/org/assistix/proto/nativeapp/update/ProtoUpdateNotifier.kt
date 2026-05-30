package org.assistix.proto.nativeapp.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.assistix.proto.nativeapp.MainActivity
import org.assistix.proto.nativeapp.R
import org.assistix.proto.nativeapp.ui.UiStrings

/** Локальные уведомления о новой версии PROTO (скачать / установить). */
class ProtoUpdateNotifier(private val context: Context) {
    private val nm = NotificationManagerCompat.from(context)

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = context.getSystemService(NotificationManager::class.java)
            if (mgr != null) {
                mgr.createNotificationChannel(
                    NotificationChannel(CHAN_ID, UiStrings.updateNotifChannelName, NotificationManager.IMPORTANCE_HIGH).apply {
                        description = UiStrings.updateNotifChannelDesc
                        enableVibration(true)
                    },
                )
            }
        }
    }

    fun notifyUpdate(info: AppUpdateInfo, phase: UpdateNotifPhase) {
        if (!nm.areNotificationsEnabled()) return
        val changelog = info.changelog.trim().ifBlank { info.requiredMessage.trim() }
        val body =
            when (phase) {
                is UpdateNotifPhase.Downloading ->
                    if (phase.progress >= 0.02f) {
                        UiStrings.updateDownloadingProgress((phase.progress * 100).toInt())
                    } else {
                        UiStrings.updateDownloading
                    }
                UpdateNotifPhase.Ready -> changelog.ifBlank { UiStrings.updateReadyBody }
                UpdateNotifPhase.Available -> changelog.ifBlank { UiStrings.updateAvailableBody }
                UpdateNotifPhase.Mandatory -> changelog.ifBlank { info.requiredMessage.ifBlank { UiStrings.updateMandatoryShort } }
            }
        val title =
            when (phase) {
                is UpdateNotifPhase.Downloading -> UiStrings.updateDownloading
                UpdateNotifPhase.Ready -> UiStrings.updateReadyTitle(info.versionName)
                else -> UiStrings.updateAvailableTitle(info.versionName)
            }
        val builder =
            NotificationCompat.Builder(context, CHAN_ID)
                .setSmallIcon(R.drawable.proto_logo)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setOnlyAlertOnce(phase !is UpdateNotifPhase.Available)
                .setOngoing(phase is UpdateNotifPhase.Downloading)
                .setProgress(100, if (phase is UpdateNotifPhase.Downloading) (phase.progress * 100).toInt() else 0, phase is UpdateNotifPhase.Downloading)
                .setContentIntent(openAppIntent())
        when (phase) {
            UpdateNotifPhase.Ready -> {
                builder.addAction(0, UiStrings.updateInstall, actionIntent(ACTION_INSTALL))
                builder.setAutoCancel(true)
            }
            is UpdateNotifPhase.Downloading -> {
                builder.addAction(0, UiStrings.updateOpenApp, openAppIntent())
            }
            else -> {
                builder.addAction(0, UiStrings.updateDownload, actionIntent(ACTION_DOWNLOAD))
                if (info.isMandatoryFor(org.assistix.proto.nativeapp.BuildConfig.VERSION_CODE)) {
                    builder.setOngoing(true)
                }
                builder.setAutoCancel(false)
            }
        }
        try {
            nm.notify(NOTIF_ID, builder.build())
        } catch (_: SecurityException) {
        }
    }

    fun cancel() {
        nm.cancel(NOTIF_ID)
    }

    private fun openAppIntent(): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            pendingFlags(),
        )

    private fun actionIntent(action: String): PendingIntent =
        PendingIntent.getActivity(
            context,
            action.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_UPDATE_ACTION, action)
            },
            pendingFlags(),
        )

    private fun pendingFlags(): Int =
        PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0

    companion object {
        const val CHAN_ID = "proto_app_updates"
        const val NOTIF_ID = 8801
        const val ACTION_DOWNLOAD = "proto_update_download"
        const val ACTION_INSTALL = "proto_update_install"
    }
}

sealed class UpdateNotifPhase {
    data object Available : UpdateNotifPhase()
    data object Mandatory : UpdateNotifPhase()
    data class Downloading(val progress: Float) : UpdateNotifPhase()
    data object Ready : UpdateNotifPhase()
}
