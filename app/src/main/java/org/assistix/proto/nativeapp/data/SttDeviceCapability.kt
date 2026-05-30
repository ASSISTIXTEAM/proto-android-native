package org.assistix.proto.nativeapp.data

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager

/** Rough device class for default on-device Whisper level. */
object SttDeviceCapability {
    enum class Class {
        LOW,
        MID,
        HIGH,
    }

    fun classify(context: Context): Class {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        val ramGb = info.totalMem.toDouble() / (1024.0 * 1024.0 * 1024.0)
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        return when {
            ramGb < 4.0 || cores <= 4 -> Class.LOW
            ramGb < 6.5 || cores <= 6 -> Class.MID
            else -> Class.HIGH
        }
    }

    fun recommendedLevel(device: Class): Int =
        when (device) {
            Class.LOW -> WhisperModelTier.TINY_FAST.level
            Class.MID -> WhisperModelTier.BASE.level
            Class.HIGH -> WhisperModelTier.SMALL_Q5.level
        }

    fun recommendedTier(device: Class): WhisperModelTier = WhisperModelTier.fromLevel(recommendedLevel(device))

    fun maxAdvisedLevel(device: Class): Int =
        when (device) {
            Class.LOW -> WhisperModelTier.BASE.level
            Class.MID -> WhisperModelTier.SMALL_Q5.level
            Class.HIGH -> WhisperModelTier.MEDIUM_Q5.level
        }

    fun isLevelHeavyForDevice(device: Class, level: Int): Boolean = level > maxAdvisedLevel(device)

    /** Skip native Whisper when RAM is tight or device is thermally throttled. */
    fun isWhisperRuntimeSafe(context: Context): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        val availMb = info.availMem / (1024 * 1024)
        if (availMb < 512) return false
        if (info.lowMemory) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (pm != null && pm.currentThermalStatus >= PowerManager.THERMAL_STATUS_MODERATE) return false
        }
        return classify(context) != Class.LOW || availMb >= 768
    }
}
