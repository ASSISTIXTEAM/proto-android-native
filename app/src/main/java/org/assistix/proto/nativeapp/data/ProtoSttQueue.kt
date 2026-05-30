package org.assistix.proto.nativeapp.data

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class VoiceSttUiState(
    val phase: String = "idle",
    val partialText: String? = null,
    val text: String? = null,
    val error: String? = null,
) {
    val isBusy: Boolean = phase in BUSY_PHASES

    companion object {
        private val BUSY_PHASES = setOf("queued", "decode", "writing", "partial", "server", "local")
    }
}

data class SttJob(
    val conversationId: Int,
    val messageId: Long,
    val uploadId: String,
    val mediaFile: File,
    val token: String,
    val languageCode: String,
    val mime: String?,
)

/** Serial background voice transcription with battery guard. */
class ProtoSttQueue(
    private val context: Context,
    private val scope: CoroutineScope,
    private val stt: ProtoSttCoordinator,
    private val api: ProtoApi,
    private val cache: ProtoCacheManager,
    private val prefs: ProtoAppPreferences? = null,
) {
    private val jobs = Channel<SttJob>(capacity = 128)
    private val states = ConcurrentHashMap<String, MutableStateFlow<VoiceSttUiState>>()
    private val enqueued = ConcurrentHashMap.newKeySet<String>()
    private val workerLock = Mutex()
    private var workerStarted = false
    private var maxQueueBurst = 20
    private var onlyWhenCharging = false
    private var burstEnqueued = 0

    private val _transcriptSaved = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    val transcriptSaved: SharedFlow<Unit> = _transcriptSaved.asSharedFlow()

    init {
        if (prefs != null) {
            scope.launch {
                prefs.sttMaxQueuePerBurst.collect { maxQueueBurst = it }
            }
            scope.launch {
                prefs.sttOnlyWhenCharging.collect { onlyWhenCharging = it }
            }
        }
    }

    fun jobKey(conversationId: Int, messageId: Long, uploadId: String): String =
        "$conversationId:$messageId:$uploadId"

    fun stateFor(conversationId: Int, messageId: Long, uploadId: String): StateFlow<VoiceSttUiState> =
        states.getOrPut(jobKey(conversationId, messageId, uploadId)) {
            MutableStateFlow(VoiceSttUiState())
        }.asStateFlow()

    fun enqueue(job: SttJob, force: Boolean = false) {
        val key = jobKey(job.conversationId, job.messageId, job.uploadId)
        val current = states[key]?.value
        if (!force && (current?.text != null || current?.isBusy == true)) return
        if (!force && !enqueued.add(key)) return
        if (!force && burstEnqueued >= maxQueueBurst) {
            Log.d(TAG, "skip enqueue $key — burst limit $maxQueueBurst")
            enqueued.remove(key)
            return
        }
        if (!batteryAllowsBackground()) {
            Log.d(TAG, "skip enqueue $key — battery/charging policy")
            enqueued.remove(key)
            return
        }
        if (!force) burstEnqueued++
        states.getOrPut(key) { MutableStateFlow(VoiceSttUiState(phase = "queued")) }.value =
            VoiceSttUiState(phase = "queued")
        startWorker()
        scope.launch { jobs.send(job) }
    }

    suspend fun enqueuePendingInChat(
        token: String,
        conversationId: Int,
        messages: List<MsgItem>,
        languageCode: String,
    ) {
        if (token.isBlank()) return
        burstEnqueued = 0
        val map = ProtoVoiceTranscriptStore.transcriptMap(context)
        for (m in messages) {
            if (!m.isVoiceMedia()) continue
            val uploadId = m.mediaUploadId?.let { normalizeUploadId(it) } ?: continue
            if (ProtoVoiceTranscriptStore.transcriptForMessage(map, conversationId, m) != null) continue
            val ext = cacheExtForMime(m.mediaMime)
            val file = cache.audioFile(uploadId, ext)
            if (!file.exists() || file.length() < 32L) {
                withContext(Dispatchers.IO) {
                    if (!file.exists() || file.length() < 32L) {
                        api.downloadMedia(token, uploadId, file)
                    }
                }
            }
            if (file.exists() && file.length() >= 32L) {
                enqueue(
                    SttJob(
                        conversationId = conversationId,
                        messageId = m.id,
                        uploadId = uploadId,
                        mediaFile = file,
                        token = token,
                        languageCode = languageCode,
                        mime = m.mediaMime,
                    ),
                )
            }
        }
    }

    private fun startWorker() {
        if (workerStarted) return
        synchronized(this) {
            if (workerStarted) return
            workerStarted = true
            scope.launch(Dispatchers.IO) {
                for (job in jobs) {
                    val key = jobKey(job.conversationId, job.messageId, job.uploadId)
                    try {
                        if (!batteryAllowsBackground()) {
                            states[key]?.value = VoiceSttUiState(phase = "idle")
                            continue
                        }
                        processJob(job)
                    } catch (e: Throwable) {
                        Log.e(TAG, "job failed $key", e)
                        states[key]?.value = VoiceSttUiState(phase = "failed", error = e.message)
                    } finally {
                        enqueued.remove(key)
                    }
                }
            }
        }
    }

    private suspend fun processJob(job: SttJob) {
        val key = jobKey(job.conversationId, job.messageId, job.uploadId)
        val flow = states.getOrPut(key) { MutableStateFlow(VoiceSttUiState()) }
        flow.value = VoiceSttUiState(phase = "decode")
        val res =
            stt.transcribe(
                token = job.token,
                uploadId = job.uploadId,
                conversationId = job.conversationId,
                mediaFile = job.mediaFile,
                languageCode = job.languageCode,
            ) { phase, partial ->
                flow.value =
                    when (phase) {
                        "partial" -> VoiceSttUiState(phase = "partial", partialText = partial)
                        "decode" -> VoiceSttUiState(phase = "decode", partialText = partial)
                        "writing" -> VoiceSttUiState(phase = "writing", partialText = partial)
                        else -> VoiceSttUiState(phase = phase, partialText = partial)
                    }
            }
        val result = res.getOrNull()
        val text = result?.text
        if (!text.isNullOrBlank()) {
            val source =
                when (result.source) {
                    ProtoSttCoordinator.TranscribeSource.SERVER -> "server"
                    ProtoSttCoordinator.TranscribeSource.LOCAL -> "local"
                }
            ProtoVoiceTranscriptStore.put(
                context,
                job.conversationId,
                job.messageId,
                job.uploadId,
                text,
                source,
            )
            flow.value = VoiceSttUiState(phase = "done", text = text)
            _transcriptSaved.emit(Unit)
        } else {
            flow.value = VoiceSttUiState(phase = "failed", error = res.exceptionOrNull()?.message)
        }
    }

    private fun batteryAllowsBackground(): Boolean {
        return try {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val intent = context.registerReceiver(null, filter) ?: return !onlyWhenCharging
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
            val pct = level * 100 / scale
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val charging =
                status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
            if (onlyWhenCharging) return charging
            charging || pct >= MIN_BATTERY_PCT
        } catch (_: Exception) {
            !onlyWhenCharging
        }
    }

    private fun MsgItem.isVoiceMedia(): Boolean {
        val kind = mediaKind?.lowercase()
        if (kind == "voice") return true
        val mime = mediaMime?.lowercase().orEmpty()
        return mime.startsWith("audio/")
    }

    companion object {
        private const val TAG = "ProtoSttQueue"
        private const val MIN_BATTERY_PCT = 15
    }
}
