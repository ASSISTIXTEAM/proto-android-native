package org.assistix.proto.nativeapp.data

import android.content.Context
import android.util.Log
import java.util.Properties
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream

/** Voice STT: fully offline on device when pack is ready; server only as optional fallback. */
class ProtoSttCoordinator(
    private val context: Context,
    private val api: ProtoApi,
    private val network: ProtoNetworkMonitor? = null,
    private val prefs: ProtoAppPreferences? = null,
    private val notifier: ProtoNotifier? = null,
) {
    private val sttRoot = ProtoPersistentStorage.sttDir(context)
    private val settingsFile = File(ProtoPersistentStorage.prefsDir(context), "stt.properties")
    private val legacyVoskDir = File(sttRoot, "vosk-ru-small")
    private val downloadMutex = Mutex()
    private val backgroundDownloadStarted = AtomicBoolean(false)
    private val settingsLock = Any()

    private val _packState = MutableStateFlow(PackState.UNKNOWN)
    val packState: StateFlow<PackState> = _packState

    private val _downloadProgress = MutableStateFlow(-1f)
    val downloadProgress: StateFlow<Float> = _downloadProgress.asStateFlow()

    private val _modelLevel = MutableStateFlow(readModelLevel())
    val modelLevel: StateFlow<Int> = _modelLevel.asStateFlow()

    private val _modelTier = MutableStateFlow(readModelTier())
    val modelTier: StateFlow<WhisperModelTier> = _modelTier.asStateFlow()

    enum class PackState { UNKNOWN, DOWNLOADING, READY, ERROR }

    enum class TranscribeSource { SERVER, LOCAL }

    data class TranscribeResult(val text: String, val source: TranscribeSource)

    init {
        runCatching {
            migrateLegacySttPrefs()
            setProp(KEY_OFFLINE_ENABLED, "true")
            if (!getProp(KEY_MODEL_LEVEL_USER_SET, "false").toBoolean()) {
                val auto = SttDeviceCapability.recommendedLevel(SttDeviceCapability.classify(context))
                setProp(KEY_MODEL_LEVEL, auto.toString())
            } else if (getProp(KEY_MODEL_LEVEL, "").isBlank()) {
                val legacyTier = WhisperModelTier.fromId(getProp(KEY_MODEL_TIER_LEGACY, ""))
                setProp(KEY_MODEL_LEVEL, legacyTier.level.toString())
            }
            _modelLevel.value = readModelLevel()
            _modelTier.value = readModelTier()
            refreshPackState()
        }
    }

    fun deviceCapability(): SttDeviceCapability.Class = SttDeviceCapability.classify(context)

    fun recommendedLevelForDevice(): Int = SttDeviceCapability.recommendedLevel(deviceCapability())

    fun recommendedTierForDevice(): WhisperModelTier = WhisperModelTier.fromLevel(recommendedLevelForDevice())

    fun getModelLevel(): Int = readModelLevel()

    fun getModelTier(): WhisperModelTier = readModelTier()

    fun setModelLevel(level: Int, userInitiated: Boolean = true) {
        val tier = WhisperModelTier.fromLevel(level)
        setModelTier(tier, userInitiated)
    }

    fun setModelTier(tier: WhisperModelTier, userInitiated: Boolean = true) {
        val prev = readModelTier()
        synchronized(settingsLock) {
            setProp(KEY_MODEL_LEVEL, tier.level.toString())
            setProp(KEY_MODEL_TIER_LEGACY, tier.id)
            if (userInitiated) setProp(KEY_MODEL_LEVEL_USER_SET, "true")
        }
        _modelLevel.value = tier.level
        _modelTier.value = tier
        if (prev.fileName != tier.fileName) {
            ProtoWhisperLocal.release()
        }
        if (!isPackReady(tier)) {
            _packState.value = PackState.UNKNOWN
            backgroundDownloadStarted.set(false)
        }
        refreshPackState()
    }

    private fun readModelLevel(): Int {
        val raw = getProp(KEY_MODEL_LEVEL, "").toIntOrNull()
        if (raw != null) return raw.coerceIn(WhisperModelTier.MIN_LEVEL, WhisperModelTier.MAX_LEVEL)
        return WhisperModelTier.fromId(getProp(KEY_MODEL_TIER_LEGACY, "")).level
    }

    private fun readModelTier(): WhisperModelTier = WhisperModelTier.fromLevel(readModelLevel())

    private fun modelFileFor(tier: WhisperModelTier = readModelTier()): File = File(sttRoot, tier.fileName)

    private fun migrateFromVoskIfNeeded() {
        if (legacyVoskDir.exists()) {
            runCatching { legacyVoskDir.deleteRecursively() }
            runCatching { File(sttRoot, "vosk-pack.zip").delete() }
            removeProp(KEY_PACK_READY)
        }
    }

    fun refreshPackState() {
        _packState.value =
            when {
                isPackReady() -> PackState.READY
                _packState.value == PackState.DOWNLOADING -> PackState.DOWNLOADING
                else -> PackState.UNKNOWN
            }
    }

    fun ensurePackHealthy(scope: CoroutineScope) {
        refreshPackState()
        if (!isPackReady()) {
            backgroundDownloadStarted.set(false)
            startBackgroundPackDownload(scope, delayMs = 500L)
        }
    }

    fun startBackgroundPackDownload(scope: CoroutineScope, delayMs: Long = 4_000L) {
        if (isPackReady()) {
            _packState.value = PackState.READY
            _downloadProgress.value = 1f
            return
        }
        if (!backgroundDownloadStarted.compareAndSet(false, true)) return
        scope.launch(Dispatchers.IO) {
            try {
                if (delayMs > 0) kotlinx.coroutines.delay(delayMs)
                runCatching { migrateFromVoskIfNeeded() }
                ensurePackDownloaded()
            } catch (e: Throwable) {
                Log.e(TAG, "background download", e)
            } finally {
                if (!isPackReady()) backgroundDownloadStarted.set(false)
            }
        }
    }

    suspend fun ensurePackDownloaded(): Boolean =
        downloadMutex.withLock {
            val tier = readModelTier()
            val modelFile = modelFileFor(tier)
            try {
                if (heavyDownloadBlocked(tier)) {
                    Log.i(TAG, "heavy model download blocked — not on Wi-Fi")
                    return@withLock false
                }
                val wasReady = isPackReady(tier)
                if (wasReady) {
                    _packState.value = PackState.READY
                    _downloadProgress.value = 1f
                    return@withLock true
                }
                _packState.value = PackState.DOWNLOADING
                _downloadProgress.value = 0f
                val fromAssets = withContext(Dispatchers.IO) { copyModelFromAssetsIfPresent(tier, modelFile) }
                val ok =
                    if (fromAssets) {
                        _downloadProgress.value = 1f
                        true
                    } else {
                        withContext(Dispatchers.IO) { downloadWhisperModelSafe(tier, modelFile) }
                    }
                _packState.value = if (ok) PackState.READY else PackState.ERROR
                if (!ok) _downloadProgress.value = -1f
                if (ok) notifier?.notifySttPackReady()
                ok
            } catch (e: Throwable) {
                Log.e(TAG, "ensurePackDownloaded", e)
                _packState.value = PackState.ERROR
                _downloadProgress.value = -1f
                false
            }
        }

    fun isPackReady(tier: WhisperModelTier = readModelTier()): Boolean {
        val f = modelFileFor(tier)
        return f.exists() && f.length() >= tier.minBytes
    }

    suspend fun warmupModel() =
        withContext(Dispatchers.IO) {
            if (!isPackReady()) return@withContext
            val tier = readModelTier()
            val modelFile = modelFileFor(tier)
            runCatching { ProtoWhisperLocal.ensureModel(modelFile, tier.minBytes) }
                .onFailure { Log.w(TAG, "warmup", it) }
        }

    suspend fun transcribe(
        token: String,
        uploadId: String,
        conversationId: Int,
        mediaFile: File,
        languageCode: String = "auto",
        onProgress: ((phase: String, partialText: String?) -> Unit)? = null,
    ): Result<TranscribeResult> =
        withContext(Dispatchers.IO) {
            val started = System.currentTimeMillis()
            try {
                if (!mediaFile.exists() || mediaFile.length() < 32L) {
                    return@withContext Result.failure(Exception("audio_missing"))
                }

                if (token.isNotBlank()) {
                    onProgress?.invoke("server", null)
                    val cached = tryServerTranscribe(token, uploadId, conversationId, poll = false)
                    if (!cached.isNullOrBlank()) {
                        logBench(started, "server_cache")
                        return@withContext Result.success(TranscribeResult(cached, TranscribeSource.SERVER))
                    }
                }

                if (isPackReady()) {
                    onProgress?.invoke("writing", null)
                    val localText = tryLocalTranscribeWithFallback(mediaFile, languageCode, onProgress)
                    if (!localText.isNullOrBlank()) {
                        logBench(started, "local")
                        return@withContext Result.success(TranscribeResult(localText, TranscribeSource.LOCAL))
                    }
                }

                if (token.isNotBlank()) {
                    onProgress?.invoke("server", null)
                    val serverText = tryServerTranscribe(token, uploadId, conversationId, poll = true)
                    if (!serverText.isNullOrBlank()) {
                        logBench(started, "server")
                        return@withContext Result.success(TranscribeResult(serverText, TranscribeSource.SERVER))
                    }
                }

                if (!isPackReady()) {
                    return@withContext Result.failure(Exception("stt_pack_missing"))
                }

                onProgress?.invoke("writing", null)
                val tier = readModelTier()
                val localRetry = tryLocalTranscribeWithFallback(mediaFile, languageCode, onProgress)
                if (localRetry.isNullOrBlank()) {
                    return@withContext Result.failure(Exception("stt_failed"))
                }
                if (token.isNotBlank()) {
                    runCatching {
                        api.transcribeSave(
                            token,
                            uploadId,
                            conversationId,
                            localRetry,
                            "android-whisper-${tier.id}",
                            languageCode,
                        )
                    }
                }
                logBench(started, "local_retry")
                Result.success(TranscribeResult(localRetry, TranscribeSource.LOCAL))
            } catch (e: Throwable) {
                Log.e(TAG, "transcribe", e)
                Result.failure(e)
            }
        }

    private fun logBench(startedMs: Long, path: String) {
        val ms = System.currentTimeMillis() - startedMs
        Log.i(TAG, "stt_bench path=$path ms=$ms tier=${readModelTier().id}")
    }

    private suspend fun tryServerTranscribe(
        token: String,
        uploadId: String,
        conversationId: Int,
        poll: Boolean,
    ): String? {
        return try {
            fun readyText(j: org.json.JSONObject?): String? {
                if (j?.optString("status") == "ready") {
                    return j.optString("text").trim().takeIf { it.isNotEmpty() }
                }
                return null
            }
            readyText(api.transcribeGet(token, uploadId))?.let { return it }
            val enq = api.transcribeEnqueue(token, uploadId, conversationId) ?: return null
            readyText(enq)?.let { return it }
            if (enq.optBoolean("fallback", false) || !poll) return null
            var waited = 0
            while (waited < SERVER_POLL_MAX_MS) {
                delay(SERVER_POLL_MS.toLong())
                waited += SERVER_POLL_MS
                readyText(api.transcribeGet(token, uploadId))?.let { return it }
            }
            null
        } catch (e: Throwable) {
            Log.w(TAG, "server transcribe", e)
            null
        }
    }

    private suspend fun heavyDownloadBlocked(tier: WhisperModelTier): Boolean {
        if (tier.level < WhisperModelTier.SMALL_Q5.level) return false
        val wifiOnly =
            if (prefs != null) {
                prefs.sttWifiOnlyHeavyModels.first()
            } else {
                true
            }
        if (!wifiOnly) return false
        return network?.isOnWifi() != true
    }

    fun deletePack() {
        ProtoWhisperLocal.release()
        runCatching {
            WhisperModelTier.LADDER.map { it.fileName }.distinct().forEach { name ->
                File(sttRoot, name).delete()
                File(sttRoot, "$name.download").delete()
            }
            legacyVoskDir.deleteRecursively()
        }
        removeProp(KEY_PACK_READY)
        refreshPackState()
    }

    fun installedModelCount(): Int =
        WhisperModelTier.LADDER.count { tier ->
            val f = File(sttRoot, tier.fileName)
            f.exists() && f.length() >= tier.minBytes
        }

    private suspend fun tryLocalTranscribeWithFallback(
        mediaFile: File,
        languageCode: String,
        onProgress: ((phase: String, partialText: String?) -> Unit)?,
    ): String? {
        var level = readModelTier().level
        val startLevel = level
        while (level >= WhisperModelTier.MIN_LEVEL) {
            val tier = WhisperModelTier.fromLevel(level)
            if (!isPackReady(tier)) {
                level--
                continue
            }
            val text =
                try {
                    tryLocalTranscribe(mediaFile, languageCode, tier, onProgress)
                } catch (e: OutOfMemoryError) {
                    Log.w(TAG, "OOM at tier ${tier.id}, downgrade", e)
                    ProtoWhisperLocal.release()
                    null
                }
            if (!text.isNullOrBlank()) {
                if (level < startLevel) Log.i(TAG, "fallback tier ${tier.id}")
                return text
            }
            ProtoWhisperLocal.release()
            if (level <= WhisperModelTier.MIN_LEVEL) break
            level--
        }
        return null
    }

    private suspend fun tryLocalTranscribe(
        mediaFile: File,
        languageCode: String,
        tier: WhisperModelTier,
        onProgress: ((phase: String, partialText: String?) -> Unit)? = null,
    ): String? =
        withTimeoutOrNull(LOCAL_TIMEOUT_MS) {
            onProgress?.invoke("decode", null)
            val decoded =
                runCatching { ProtoAudioDecoder.decodeTo16kMonoFloat(mediaFile) }
                    .onFailure { e ->
                        Log.w(TAG, "decode failed", e)
                        if (e is OutOfMemoryError) throw e
                    }
                    .getOrNull()
            val audio = decoded?.let { ProtoAudioVad.trimSilence(it) }
            if (audio == null || audio.isEmpty()) {
                Log.w(TAG, "decode failed for ${mediaFile.name} len=${mediaFile.length()}")
                return@withTimeoutOrNull null
            }
            val lang = mapLanguage(languageCode)
            val modelFile = modelFileFor(tier)
            val threads = effectiveThreads(tier)
            onProgress?.invoke("writing", null)
            if (audio.size > PARTIAL_SAMPLE_COUNT) {
                val preview = FloatArray(PARTIAL_SAMPLE_COUNT) { audio[it] }
                runCatching {
                    if (ProtoWhisperLocal.ensureModel(modelFile, tier.minBytes)) {
                        ProtoWhisperLocal.transcribe(preview, lang, threads)?.let { p ->
                            if (p.isNotBlank()) onProgress?.invoke("partial", p)
                        }
                    }
                }.onFailure { e ->
                    if (e is OutOfMemoryError) throw e
                }
            }
            val padded = padForWhisper(audio)
            runCatching {
                if (!ProtoWhisperLocal.ensureModel(modelFile, tier.minBytes)) return@runCatching null
                ProtoWhisperLocal.transcribe(padded, lang, threads)
            }.onFailure { e ->
                Log.w(TAG, "whisper local", e)
                if (e is OutOfMemoryError) throw e
            }.getOrNull()
        }

    private fun effectiveThreads(tier: WhisperModelTier): Int {
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        return tier.maxThreads.coerceAtMost(cores).coerceAtLeast(1)
    }

    private fun mapLanguage(code: String): String =
        when (code.lowercase()) {
            "ru", "it", "en", "uk", "de", "fr", "es" -> code.lowercase()
            else -> "auto"
        }

    private fun padForWhisper(samples: FloatArray): FloatArray {
        val min = 8_000
        if (samples.size >= min) return samples
        return FloatArray(min) { i -> if (i < samples.size) samples[i] else 0f }
    }

    private fun copyModelFromAssetsIfPresent(tier: WhisperModelTier, dest: File): Boolean {
        return try {
            sttRoot.mkdirs()
            context.assets.open("stt/${tier.fileName}").use { input ->
                streamToFile(input, dest, tier.approxDownloadBytes)
            }
            val ok = dest.length() >= tier.minBytes
            if (ok) {
                setProp(KEY_PACK_READY, "true")
                setProp(KEY_PACK_ENGINE, ENGINE_WHISPER.toString())
            } else {
                dest.delete()
            }
            ok
        } catch (_: Exception) {
            false
        }
    }

    private fun downloadWhisperModelSafe(tier: WhisperModelTier, modelFile: File): Boolean {
        return try {
            sttRoot.mkdirs()
            val tmp = File(sttRoot, "${tier.fileName}.download")
            tmp.delete()
            val origin = ProtoApiOrigin.primary()
            val urls =
                listOf(
                    "$origin/stt/models/${tier.fileName}",
                    "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/${tier.fileName}",
                )
            val client =
                okhttp3.OkHttpClient.Builder()
                    .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(600, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
            for (url in urls) {
                val req = okhttp3.Request.Builder().url(url).get().build()
                try {
                    client.newCall(req).execute().use { res ->
                        if (!res.isSuccessful) return@use
                        val body = res.body ?: return@use
                        val total = body.contentLength().coerceAtLeast(-1L)
                        body.byteStream().use { input ->
                            streamToFile(input, tmp, totalBytes = total, expectedBytes = tier.approxDownloadBytes)
                        }
                    }
                    if (tmp.length() >= tier.minBytes) {
                        if (modelFile.exists()) modelFile.delete()
                        if (!tmp.renameTo(modelFile)) {
                            tmp.copyTo(modelFile, overwrite = true)
                            tmp.delete()
                        }
                        setProp(KEY_PACK_READY, "true")
                        setProp(KEY_PACK_ENGINE, ENGINE_WHISPER.toString())
                        return true
                    }
                    tmp.delete()
                } catch (e: Exception) {
                    Log.w(TAG, "model download $url", e)
                    tmp.delete()
                }
            }
            false
        } catch (e: Throwable) {
            Log.e(TAG, "downloadWhisperModelSafe", e)
            false
        }
    }

    private fun streamToFile(
        input: InputStream,
        dest: File,
        expectedBytes: Long,
        totalBytes: Long = -1L,
    ) {
        val parent = dest.parentFile
        if (parent != null && !parent.exists()) parent.mkdirs()
        val tmp = File(dest.absolutePath + ".part")
        tmp.delete()
        var written = 0L
        FileOutputStream(tmp).use { out ->
            val buf = ByteArray(STREAM_BUFFER)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                out.write(buf, 0, n)
                written += n
                if (totalBytes > 0L) {
                    _downloadProgress.value = (written.toFloat() / totalBytes).coerceIn(0f, 0.98f)
                } else if (written % (512 * 1024) < STREAM_BUFFER) {
                    val est = (written.toFloat() / expectedBytes).coerceIn(0.05f, 0.95f)
                    _downloadProgress.value = est
                }
            }
            runCatching { out.fd.sync() }
        }
        if (dest.exists()) dest.delete()
        if (!tmp.renameTo(dest)) {
            tmp.copyTo(dest, overwrite = true)
            tmp.delete()
        }
    }

    private fun migrateLegacySttPrefs() {
        if (settingsFile.exists()) return
        val legacy = context.getSharedPreferences(PREFS_NAME_LEGACY, Context.MODE_PRIVATE)
        if (legacy.all.isEmpty()) return
        synchronized(settingsLock) {
            val props = Properties()
            val tierId = legacy.getString(KEY_MODEL_TIER_LEGACY, null)
            if (!tierId.isNullOrBlank()) {
                val tier = WhisperModelTier.fromId(tierId)
                props.setProperty(KEY_MODEL_LEVEL, tier.level.toString())
                props.setProperty(KEY_MODEL_TIER_LEGACY, tierId)
            }
            if (legacy.getBoolean("model_tier_user_set", false)) {
                props.setProperty(KEY_MODEL_LEVEL_USER_SET, "true")
            }
            if (legacy.getBoolean(KEY_PACK_READY, false)) {
                props.setProperty(KEY_PACK_READY, "true")
            }
            settingsFile.parentFile?.mkdirs()
            FileOutputStream(settingsFile).use { props.store(it, "PROTO STT") }
        }
    }

    private fun loadProps(): Properties {
        val props = Properties()
        if (settingsFile.exists()) {
            runCatching { FileInputStream(settingsFile).use { props.load(it) } }
        }
        return props
    }

    private fun getProp(key: String, default: String = ""): String =
        synchronized(settingsLock) {
            loadProps().getProperty(key, default) ?: default
        }

    private fun setProp(key: String, value: String) {
        synchronized(settingsLock) {
            val props = loadProps()
            props.setProperty(key, value)
            settingsFile.parentFile?.mkdirs()
            FileOutputStream(settingsFile).use { props.store(it, "PROTO STT") }
        }
    }

    private fun removeProp(key: String) {
        synchronized(settingsLock) {
            val props = loadProps()
            props.remove(key)
            settingsFile.parentFile?.mkdirs()
            FileOutputStream(settingsFile).use { props.store(it, "PROTO STT") }
        }
    }

    companion object {
        private const val TAG = "ProtoStt"
        private const val PREFS_NAME_LEGACY = "proto_stt"
        private const val KEY_OFFLINE_ENABLED = "offline_enabled"
        private const val KEY_MODEL_LEVEL = "model_level"
        private const val KEY_MODEL_LEVEL_USER_SET = "model_level_user_set"
        private const val KEY_MODEL_TIER_LEGACY = "model_tier"
        private const val KEY_PACK_READY = "pack_ready"
        private const val KEY_PACK_ENGINE = "pack_engine"
        private const val ENGINE_WHISPER = 2
        private const val LOCAL_TIMEOUT_MS = 180_000L
        private const val STREAM_BUFFER = 64 * 1024
        /** ~18 s preview at 16 kHz for partial transcript while full decode runs. */
        private const val PARTIAL_SAMPLE_COUNT = 18 * 16_000
        private const val SERVER_POLL_MS = 2_000
        private const val SERVER_POLL_MAX_MS = 28_000
    }
}
