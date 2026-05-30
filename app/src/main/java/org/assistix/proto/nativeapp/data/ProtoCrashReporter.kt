package org.assistix.proto.nativeapp.data

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.assistix.proto.nativeapp.BuildConfig

/** Ring-buffer crash log + opt-in upload (no message content). */
object ProtoCrashReporter {
    private const val TAG = "ProtoCrash"
    private const val MAX_ENTRIES = 12
    private const val MAX_CHARS = 48_000

    private val installed = AtomicBoolean(false)
    private val lock = Any()
    private val buffer = ArrayDeque<String>()

    fun install(context: Context, scope: CoroutineScope, prefs: ProtoAppPreferences) {
        if (!installed.compareAndSet(false, true)) return
        val app = context.applicationContext
        val default = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            recordCrash(app, thread.name, error)
            scope.launch(Dispatchers.IO) {
                if (prefs.crashReportOptIn.first()) {
                    flushToServer(app, prefs)
                }
            }
            default?.uncaughtException(thread, error)
        }
    }

    fun recordCrash(context: Context, threadName: String, error: Throwable) {
        val entry = formatEntry(threadName, error)
        synchronized(lock) {
            buffer.addLast(entry)
            while (buffer.size > MAX_ENTRIES) buffer.removeFirst()
            trimBuffer()
        }
        persistToDisk(context, entry)
        Log.e(TAG, "recorded crash on $threadName", error)
    }

    private fun formatEntry(threadName: String, error: Throwable): String =
        buildString {
            append(SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date()))
            append(" thread=")
            append(threadName)
            append(" build=")
            append(BuildConfig.VERSION_CODE)
            append('/')
            append(BuildConfig.VERSION_NAME)
            append(" abi=")
            append(Build.SUPPORTED_ABIS.firstOrNull().orEmpty())
            append('\n')
            append(Log.getStackTraceString(error).take(4000))
        }

    private fun crashLogFile(context: Context): File = File(context.filesDir, "proto_crash_log.txt")

    private fun persistToDisk(context: Context, entry: String) {
        runCatching {
            val f = crashLogFile(context)
            val prev = if (f.exists()) f.readText().take(MAX_CHARS) else ""
            f.writeText((prev + "\n---\n" + entry).takeLast(MAX_CHARS))
        }
    }

    fun readLocalLog(context: Context): String =
        runCatching { crashLogFile(context).takeIf { it.exists() }?.readText().orEmpty() }.getOrDefault("")

    suspend fun flushToServer(context: Context, prefs: ProtoAppPreferences, api: ProtoApi? = null) {
        if (!prefs.crashReportOptIn.first()) return
        val token = ProtoSessionStore(context).token() ?: return
        val body =
            synchronized(lock) {
                if (buffer.isEmpty()) readLocalLog(context) else buffer.joinToString("\n---\n")
            }.take(MAX_CHARS)
        if (body.isBlank()) return
        val client = api ?: ProtoApi(context)
        if (client.reportCrash(token, body, BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME)) {
            synchronized(lock) { buffer.clear() }
            runCatching { crashLogFile(context).delete() }
        }
    }

    private fun trimBuffer() {
        while (buffer.joinToString("\n").length > MAX_CHARS && buffer.isNotEmpty()) {
            buffer.removeFirst()
        }
    }
}
