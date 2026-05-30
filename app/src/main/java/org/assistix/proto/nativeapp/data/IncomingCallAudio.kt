package org.assistix.proto.nativeapp.data

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.PowerManager
import android.util.Log

/** Системный рингтон входящего — работает из Activity, FGS и push. */
object IncomingCallAudio {
    private const val TAG = "IncomingCallAudio"
    private var player: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null

    @Synchronized
    fun startIncoming(context: Context) {
        if (player?.isPlaying == true) return
        stop(context)
        val appCtx = context.applicationContext
        try {
            val uri =
                RingtoneManager.getActualDefaultRingtoneUri(appCtx, RingtoneManager.TYPE_RINGTONE)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            val p = MediaPlayer()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                p.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
            } else {
                @Suppress("DEPRECATION")
                p.setAudioStreamType(AudioManager.STREAM_RING)
            }
            p.setDataSource(appCtx, uri)
            p.isLooping = true
            p.prepare()
            p.start()
            player = p
            acquireWake(appCtx)
        } catch (e: Exception) {
            Log.w(TAG, "startIncoming failed", e)
            stop(appCtx)
        }
    }

    @Synchronized
    fun stop(context: Context) {
        releaseWake()
        try {
            player?.stop()
            player?.release()
        } catch (_: Exception) {
        }
        player = null
    }

    private fun acquireWake(context: Context) {
        releaseWake()
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock =
                pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "proto:incoming_call").apply {
                    setReferenceCounted(false)
                    acquire(60_000L)
                }
        } catch (e: Exception) {
            Log.w(TAG, "wakeLock", e)
        }
    }

    private fun releaseWake() {
        try {
            wakeLock?.let { if (it.isHeld) it.release() }
        } catch (_: Exception) {
        }
        wakeLock = null
    }
}
