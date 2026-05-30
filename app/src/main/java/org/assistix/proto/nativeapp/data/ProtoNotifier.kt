package org.assistix.proto.nativeapp.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.graphics.drawable.IconCompat
import okhttp3.Request
import org.assistix.proto.nativeapp.IncomingCallActivity
import org.assistix.proto.nativeapp.MainActivity
import org.assistix.proto.nativeapp.ProtoCallActionReceiver
import org.assistix.proto.nativeapp.R
import org.assistix.proto.nativeapp.ui.UiStrings

class ProtoNotifier(private val context: Context) {
    private val nm = NotificationManagerCompat.from(context)
    private val http = okhttp3.OkHttpClient.Builder().build()

    init {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val mgr = context.getSystemService(NotificationManager::class.java) ?: return@runCatching
                mgr.createNotificationChannel(
                    NotificationChannel(CHAN_MSG, "Messages", NotificationManager.IMPORTANCE_HIGH).apply {
                        description = "New chat messages"
                        enableVibration(true)
                    },
                )
                val ringUri =
                    RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE)
                        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                mgr.createNotificationChannel(
                    NotificationChannel(CHAN_CALL, "Calls", NotificationManager.IMPORTANCE_HIGH).apply {
                        description = "Incoming calls"
                        enableVibration(true)
                        vibrationPattern = longArrayOf(0, 700, 350, 700)
                        lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                        if (ringUri != null) {
                            setSound(
                                ringUri,
                                android.media.AudioAttributes.Builder()
                                    .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                    .build(),
                            )
                        }
                    },
                )
            }
        }
    }

    fun loadAvatarBitmap(api: ProtoApi, token: String, uploadId: String): Bitmap? {
        val id = normalizeUploadId(uploadId) ?: return null
        val req =
            Request.Builder()
                .url(api.mediaUrl(id))
                .apply { api.authHeaders(token).forEach { (k, v) -> addHeader(k, v) } }
                .get()
                .build()
        return try {
            http.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return null
                res.body?.byteStream()?.use { BitmapFactory.decodeStream(it) }
            }
        } catch (_: Exception) {
            null
        }
    }

    fun notifyMessage(
        senderTitle: String,
        body: String,
        conversationId: Int,
        avatar: Bitmap? = null,
    ) {
        if (!nm.areNotificationsEnabled()) return
        val intent =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("open_conversation_id", conversationId)
                putExtra("open_conversation_title", senderTitle)
            }
        val pi = PendingIntent.getActivity(context, conversationId, intent, pendingFlags())
        val personBuilder = Person.Builder().setName(senderTitle)
        if (avatar != null) {
            personBuilder.setIcon(IconCompat.createWithBitmap(avatar))
        }
        val person = personBuilder.build()
        val style =
            NotificationCompat.MessagingStyle(person)
                .setConversationTitle(senderTitle)
                .addMessage(body, System.currentTimeMillis(), person)
        val n =
            NotificationCompat.Builder(context, CHAN_MSG)
                .setSmallIcon(R.drawable.proto_logo)
                .setContentTitle(senderTitle)
                .setContentText(body)
                .setStyle(style)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .apply {
                    avatar?.let { setLargeIcon(it) }
                }
                .build()
        try {
            nm.notify(MSG_BASE + conversationId, n)
        } catch (_: SecurityException) {
        }
    }

    fun notifyIncomingCall(peer: String, conversationId: Int, withVideo: Boolean = false) {
        vibrateIncoming()
        val fullScreenIntent =
            Intent(context, IncomingCallActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(IncomingCallActivity.EXTRA_CID, conversationId)
            }
        val fullScreenPi = PendingIntent.getActivity(context, CALL_ID, fullScreenIntent, pendingFlags())
        val declinePi =
            PendingIntent.getBroadcast(
                context,
                CALL_ID + 1,
                Intent(context, ProtoCallActionReceiver::class.java).apply {
                    action = ProtoCallActionReceiver.ACTION_DECLINE
                    putExtra(ProtoCallActionReceiver.EXTRA_CID, conversationId)
                },
                pendingFlags(),
            )
        val answerPi =
            PendingIntent.getBroadcast(
                context,
                CALL_ID + 2,
                Intent(context, ProtoCallActionReceiver::class.java).apply {
                    action = ProtoCallActionReceiver.ACTION_ANSWER
                    putExtra(ProtoCallActionReceiver.EXTRA_CID, conversationId)
                },
                pendingFlags(),
            )
        val kind = if (withVideo) UiStrings.videoCall else UiStrings.audioCall
        val caller = Person.Builder().setName(peer).setImportant(true).build()
        val builder =
            NotificationCompat.Builder(context, CHAN_CALL)
                .setSmallIcon(R.drawable.proto_logo)
                .setContentTitle(UiStrings.incomingCall)
                .setContentText("$peer · $kind")
                .setSubText(kind)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setAutoCancel(false)
                .setOnlyAlertOnce(false)
                .setFullScreenIntent(fullScreenPi, true)
                .setContentIntent(fullScreenPi)
                .addAction(0, UiStrings.decline, declinePi)
                .addAction(0, UiStrings.accept, answerPi)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setStyle(
                NotificationCompat.CallStyle.forIncomingCall(caller, declinePi, answerPi),
            )
        }
        try {
            nm.notify(CALL_ID, builder.build())
        } catch (_: SecurityException) {
        }
    }

    private fun vibrateIncoming() {
        try {
            val pattern = longArrayOf(0, 700, 350, 700, 350, 700)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(VibratorManager::class.java) ?: return
                vm.defaultVibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createWaveform(pattern, -1))
                } else {
                    @Suppress("DEPRECATION")
                    v.vibrate(pattern, 0)
                }
            }
        } catch (_: Exception) {
        }
    }

    fun cancelCallNotification() {
        nm.cancel(CALL_ID)
    }

    fun cancelAllNotifications() {
        nm.cancel(CALL_ID)
        for (id in MSG_BASE until MSG_BASE + 512) {
            nm.cancel(id)
        }
    }

    fun notifySttPackReady() {
        runCatching {
            val intent = Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP }
            val pi = PendingIntent.getActivity(context, STT_READY_ID, intent, pendingFlags())
            val n =
                NotificationCompat.Builder(context, CHAN_MSG)
                    .setSmallIcon(R.drawable.proto_logo)
                    .setContentTitle(UiStrings.sttPackReadyNotificationTitle)
                    .setContentText(UiStrings.sttPackReadyNotificationBody)
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .build()
            nm.notify(STT_READY_ID, n)
        }
    }

    private fun pendingFlags(): Int =
        PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0

    companion object {
        const val CALL_CHANNEL_ID = "proto_calls"
        private const val CHAN_MSG = "proto_messages"
        private const val CHAN_CALL = CALL_CHANNEL_ID
        private const val MSG_BASE = 4000
        private const val CALL_ID = 9001
        private const val STT_READY_ID = 9010
    }
}
