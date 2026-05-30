package org.assistix.proto.nativeapp.data

import android.util.Log
import org.assistix.proto.nativeapp.BuildConfig
import java.io.File
import kotlin.math.min

/** Cached whisper.cpp context for fast repeat transcriptions. */
internal object ProtoWhisperLocal {
    private val lock = Any()
    private var contextPtr: Long = 0
    private var loadedPath: String? = null

    fun ensureModel(modelFile: File, minBytes: Long = 19_000_000L): Boolean {
        if (!WhisperNativeSupport.isDeviceSupported) return false
        if (!BuildConfig.ENABLE_WHISPER_NATIVE) return false
        val path = modelFile.absolutePath
        synchronized(lock) {
            if (contextPtr != 0L && loadedPath == path) return true
            releaseLocked()
            if (!modelFile.exists() || modelFile.length() < minBytes) {
                return false
            }
            return try {
                val ptr = WhisperNative.initContext(path)
                if (ptr == 0L) {
                    false
                } else {
                    contextPtr = ptr
                    loadedPath = path
                    true
                }
            } catch (e: Throwable) {
                Log.w("ProtoWhisper", "ensureModel", e)
                false
            }
        }
    }

    fun transcribe(audio16k: FloatArray, language: String = "auto", maxThreads: Int = 2): String? {
        if (!BuildConfig.ENABLE_WHISPER_NATIVE) return null
        synchronized(lock) {
            if (contextPtr == 0L || audio16k.isEmpty()) return null
            val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(2)
            val threads = min(maxThreads.coerceAtLeast(1), cores - 1).coerceAtLeast(1)
            val rc = WhisperNative.fullTranscribe(contextPtr, threads, audio16k, language)
            if (rc != 0) return null
            return WhisperNative.getFullText(contextPtr).trim().takeIf { it.isNotEmpty() }
        }
    }

    fun release() {
        synchronized(lock) {
            releaseLocked()
        }
    }

    private fun releaseLocked() {
        if (contextPtr != 0L) {
            WhisperNative.freeContext(contextPtr)
            contextPtr = 0L
            loadedPath = null
        }
    }
}
