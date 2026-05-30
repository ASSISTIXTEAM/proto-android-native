package org.assistix.proto.nativeapp.data

import android.os.Build
import android.util.Log
import org.assistix.proto.nativeapp.BuildConfig

/** whisper.cpp only runs on real ARM devices with enough free RAM. */
internal object WhisperNativeSupport {
    private const val TAG = "WhisperNative"

    val isDeviceSupported: Boolean by lazy {
        if (!BuildConfig.ENABLE_WHISPER_NATIVE) return@lazy false
        val primary = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
        if (primary.contains("x86", ignoreCase = true)) {
            Log.i(TAG, "whisper disabled: primary ABI is $primary")
            return@lazy false
        }
        val tag =
            listOf(Build.FINGERPRINT, Build.HARDWARE, Build.MODEL, Build.BRAND, Build.PRODUCT)
                .joinToString("|")
                .lowercase()
        if (
            tag.contains("goldfish") ||
                tag.contains("ranchu") ||
                tag.contains("sdk_gphone") ||
                tag.contains("emulator") ||
                tag.contains("generic_x86") ||
                tag.contains("vbox")
        ) {
            Log.i(TAG, "whisper disabled: emulator detected")
            return@lazy false
        }
        true
    }

    fun isRuntimeSafe(context: android.content.Context): Boolean {
        if (!isDeviceSupported) return false
        return SttDeviceCapability.isWhisperRuntimeSafe(context)
    }
}

internal object WhisperNative {
    private const val TAG = "WhisperNative"
    @Volatile
    private var libraryLoaded = false

    private fun ensureLibraryLoaded(): Boolean {
        if (!WhisperNativeSupport.isDeviceSupported) return false
        if (!BuildConfig.ENABLE_WHISPER_NATIVE) return false
        if (libraryLoaded) return true
        synchronized(this) {
            if (libraryLoaded) return true
            return try {
                System.loadLibrary("proto_whisper")
                libraryLoaded = true
                true
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "proto_whisper load failed", e)
                false
            } catch (e: Exception) {
                Log.e(TAG, "proto_whisper load failed", e)
                false
            }
        }
    }

    fun initContext(modelPath: String): Long {
        if (!ensureLibraryLoaded()) return 0L
        return nativeInitContext(modelPath)
    }

    fun freeContext(contextPtr: Long) {
        if (!libraryLoaded || contextPtr == 0L) return
        nativeFreeContext(contextPtr)
    }

    fun fullTranscribe(contextPtr: Long, numThreads: Int, audioData: FloatArray, language: String): Int {
        if (!libraryLoaded || contextPtr == 0L) return -1
        return nativeFullTranscribe(contextPtr, numThreads, audioData, language)
    }

    fun getFullText(contextPtr: Long): String {
        if (!libraryLoaded || contextPtr == 0L) return ""
        return nativeGetFullText(contextPtr)
    }

    private external fun nativeInitContext(modelPath: String): Long

    private external fun nativeFreeContext(contextPtr: Long)

    private external fun nativeFullTranscribe(
        contextPtr: Long,
        numThreads: Int,
        audioData: FloatArray,
        language: String,
    ): Int

    private external fun nativeGetFullText(contextPtr: Long): String
}
