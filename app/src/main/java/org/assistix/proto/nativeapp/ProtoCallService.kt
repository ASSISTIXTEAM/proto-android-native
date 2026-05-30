package org.assistix.proto.nativeapp

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import org.assistix.proto.nativeapp.data.IncomingCallAudio
import org.assistix.proto.nativeapp.data.ProtoNotifier
import org.assistix.proto.nativeapp.ui.UiStrings

/** Keeps incoming/active call polling alive when the app is backgrounded. */
class ProtoCallService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                IncomingCallAudio.stop(this)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                val title = intent?.getStringExtra(EXTRA_TITLE) ?: getString(R.string.app_name)
                val ring = intent?.getBooleanExtra(EXTRA_RING, true) != false
                promoteForeground(title)
                if (ring) IncomingCallAudio.startIncoming(this)
            }
        }
        return START_STICKY
    }

    private fun promoteForeground(title: String) {
        try {
            promoteForegroundInner(title)
        } catch (e: Exception) {
            Log.w(TAG, "promoteForeground failed", e)
        }
    }

    private fun promoteForegroundInner(title: String) {
        val launch =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val notification: Notification =
            NotificationCompat.Builder(this, ProtoNotifier.CALL_CHANNEL_ID)
                .setSmallIcon(R.drawable.proto_logo)
                .setContentTitle(title)
                .setContentText(UiStrings.incomingCall)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setOngoing(true)
                .setContentIntent(launch)
                .setSilent(true)
                .setDefaults(0)
                .setSound(null)
                .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL,
            )
        } else {
            @Suppress("DEPRECATION")
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val TAG = "ProtoCallService"
        const val CALL_CHANNEL_ID = "proto_calls"
        private const val NOTIFICATION_ID = 9002
        private const val ACTION_STOP = "stop"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_RING = "ring"

        fun start(ctx: Context, title: String, playRingtone: Boolean = true) {
            val i =
                Intent(ctx, ProtoCallService::class.java).apply {
                    putExtra(EXTRA_TITLE, title)
                    putExtra(EXTRA_RING, playRingtone)
                }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ctx.startForegroundService(i)
                } else {
                    ctx.startService(i)
                }
            } catch (e: Exception) {
                Log.w(TAG, "startForegroundService failed, trying startService", e)
                try {
                    ctx.startService(i)
                } catch (e2: Exception) {
                    Log.w(TAG, "startService failed", e2)
                }
            }
        }

        fun stop(ctx: Context) {
            ctx.startService(
                Intent(ctx, ProtoCallService::class.java).apply { action = ACTION_STOP },
            )
        }
    }
}
