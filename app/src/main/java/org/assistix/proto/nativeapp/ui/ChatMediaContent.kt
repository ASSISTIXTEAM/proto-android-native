package org.assistix.proto.nativeapp.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.widget.Toast
import android.widget.VideoView
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme

import org.assistix.proto.nativeapp.data.ProtoAudioWaveform
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.File
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.assistix.proto.nativeapp.ProtoApplication
import org.assistix.proto.nativeapp.data.MediaFetchState
import org.assistix.proto.nativeapp.data.ProtoApi
import org.assistix.proto.nativeapp.data.ProtoCacheManager
import org.assistix.proto.nativeapp.data.ProtoSttCoordinator
import org.assistix.proto.nativeapp.data.ProtoSttQueue
import org.assistix.proto.nativeapp.data.SttJob
import org.assistix.proto.nativeapp.data.VoiceSttUiState
import org.assistix.proto.nativeapp.data.cacheExtForMime
import org.assistix.proto.nativeapp.data.mediaKindFromMime
import org.assistix.proto.nativeapp.data.normalizeUploadId

@Composable
fun ChatMediaContent(
    uploadId: String,
    mime: String?,
    name: String?,
    token: String,
    api: ProtoApi,
    textColor: Color,
    onDownload: (String, String) -> Unit,
    conversationId: Int = 0,
    messageId: Long = 0L,
    stt: ProtoSttCoordinator? = null,
    sttQueue: ProtoSttQueue? = null,
    initialVoiceTranscript: String? = null,
    voiceTranscriptSource: String? = null,
    voiceSearchHighlight: String = "",
    languageCode: String = "auto",
    aiEnabled: Boolean = false,
    mediaKind: String? = null,
    onOpenViewer: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val cleanId = normalizeUploadId(uploadId) ?: return
    val cleanName = name?.trim()?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val effectiveMime =
        mime?.trim()?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
            ?: guessMime(cleanName)
    val kind = mediaKind ?: mediaKindFromMime(effectiveMime, cleanName)
    val openModifier =
        if (onOpenViewer != null && (kind == "image" || kind == "video")) {
            modifier.clickable { onOpenViewer() }
        } else {
            modifier
        }
    val cache = remember(ctx) { appCache(ctx) }
    val mediaUrl = api.mediaUrl(cleanId)
    val imageRequest =
        remember(cleanId, token) {
            ImageRequest.Builder(ctx)
                .data(mediaUrl)
                .apply { api.authHeaders(token).forEach { (k, v) -> addHeader(k, v) } }
                .crossfade(true)
                .build()
        }
    var localPhoto by remember(cleanId) { mutableStateOf<File?>(null) }
    var mediaExpired by remember(cleanId) { mutableStateOf(false) }
    val app = remember(ctx) { ctx.applicationContext as ProtoApplication }
    val mediaResolver = remember(app) { app.mediaResolver }
    LaunchedEffect(cleanId, token, conversationId) {
        mediaExpired = false
        val result =
            withContext(Dispatchers.IO) {
                mediaResolver.fetch(token, cleanId, effectiveMime, cleanName, conversationId)
            }
        when (result.state) {
            MediaFetchState.LOCAL, MediaFetchState.DOWNLOADED -> localPhoto = result.file
            MediaFetchState.EXPIRED -> mediaExpired = true
            else -> Unit
        }
    }
    val photoModel =
        remember(localPhoto, imageRequest) {
            val local = localPhoto
            if (local != null) {
                ImageRequest.Builder(ctx).data(local).crossfade(true).build()
            } else {
                imageRequest
            }
        }

    when (kind) {
        "image" -> {
            if (mediaExpired && localPhoto == null) {
                Column(openModifier.fillMaxWidth()) {
                    Text(UiStrings.mediaRelayExpired, color = textColor.copy(0.85f), style = MaterialTheme.typography.bodySmall)
                    TextButton(
                        onClick = {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    api.requestMediaRelay(token, cleanId, conversationId)
                                }
                                Toast.makeText(ctx, UiStrings.mediaRelayRequested, Toast.LENGTH_SHORT).show()
                            }
                        },
                    ) {
                        Text(UiStrings.mediaRequestResend, color = textColor)
                    }
                }
            } else {
                AsyncImage(
                    model = photoModel,
                    contentDescription = null,
                    modifier =
                        openModifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .clip(ProtoShapes.media),
                    contentScale = ContentScale.Fit,
                )
            }
        }
        "video" -> {
            var localFile by remember { mutableStateOf<File?>(null) }
            var loading by remember { mutableStateOf(false) }
            var showPlayer by remember { mutableStateOf(false) }

            if (showPlayer && localFile != null && onOpenViewer == null) {
                AndroidView(
                    factory = { c ->
                        VideoView(c).apply {
                            setVideoURI(Uri.fromFile(localFile))
                            setOnPreparedListener { mp -> mp.isLooping = false; start() }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(220.dp).clip(ProtoShapes.media),
                )
            } else {
                Box(
                    openModifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(ProtoShapes.media)
                        .background(Color.Black.copy(0.35f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (loading) {
                        CircularProgressIndicator(Modifier.size(36.dp), color = textColor)
                    } else {
                        IconButton(
                            onClick = {
                                if (onOpenViewer != null) {
                                    onOpenViewer()
                                    return@IconButton
                                }
                                scope.launch {
                                    loading = true
                                    val app = ctx.applicationContext as ProtoApplication
                                    val result =
                                        withContext(Dispatchers.IO) {
                                            app.mediaResolver.fetch(token, cleanId, effectiveMime, cleanName, conversationId)
                                        }
                                    if (result.file != null) {
                                        localFile = result.file
                                        loading = false
                                        showPlayer = true
                                        return@launch
                                    }
                                    loading = false
                                    onDownload(cleanId, cleanName ?: "video.mp4")
                                }
                            },
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = UiStrings.play, tint = Color.White, modifier = Modifier.size(48.dp))
                        }
                    }
                }
            }
        }
        "voice" -> {
            VoiceMessagePlayer(
                cleanId,
                effectiveMime,
                token,
                api,
                textColor,
                onDownload,
                conversationId,
                messageId,
                stt,
                sttQueue,
                initialVoiceTranscript,
                voiceTranscriptSource,
                voiceSearchHighlight,
                languageCode,
                aiEnabled,
            )
        }
        else -> {
            when {
                effectiveMime.startsWith("audio/") ->
                    VoiceMessagePlayer(
                        cleanId,
                        effectiveMime,
                        token,
                        api,
                        textColor,
                        onDownload,
                        conversationId,
                        messageId,
                        stt,
                        sttQueue,
                        initialVoiceTranscript,
                        voiceTranscriptSource,
                        voiceSearchHighlight,
                        languageCode,
                        aiEnabled,
                    )
                effectiveMime.startsWith("image/") ->
                    AsyncImage(
                        model = photoModel,
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp).clip(ProtoShapes.media),
                        contentScale = ContentScale.Fit,
                    )
                else ->
                    Text("📎 ${cleanName ?: UiStrings.file}", color = textColor, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
            }
        }
    }
}

private fun appCache(ctx: android.content.Context): ProtoCacheManager {
    val app = ctx.applicationContext as? ProtoApplication
    return app?.cache ?: ProtoCacheManager(ctx)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VoiceMessagePlayer(
    uploadId: String,
    mime: String,
    token: String,
    api: ProtoApi,
    textColor: Color,
    onDownload: (String, String) -> Unit,
    conversationId: Int,
    messageId: Long,
    stt: ProtoSttCoordinator?,
    sttQueue: ProtoSttQueue?,
    initialVoiceTranscript: String?,
    transcriptSource: String? = null,
    searchHighlight: String = "",
    languageCode: String,
    aiEnabled: Boolean,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var playing by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }
    var transcript by remember(initialVoiceTranscript) { mutableStateOf(initialVoiceTranscript) }
    val sttState by
        (sttQueue?.stateFor(conversationId, messageId, uploadId)?.collectAsState()
            ?: remember { mutableStateOf(VoiceSttUiState()) })
    val sttBusy = sttState.isBusy
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var scrubbing by remember { mutableStateOf(false) }
    var waveform by remember(uploadId) { mutableStateOf<FloatArray?>(null) }
    val app = ctx.applicationContext as org.assistix.proto.nativeapp.ProtoApplication
    val speedIdxPref by app.prefs.voicePlaybackSpeedIdx.collectAsState(initial = 0)
    var speedIdx by remember { mutableIntStateOf(speedIdxPref) }
    LaunchedEffect(speedIdxPref) { speedIdx = speedIdxPref }
    val speeds = remember { floatArrayOf(1f, 1.5f, 2f) }
    val ext = remember(mime) { cacheExtForMime(mime) }
    val cache = remember(ctx) { appCache(ctx) }
    val audioFile = remember(uploadId, ext) { cache.audioFile(uploadId, ext) }
    val player = remember { MediaPlayer() }

    fun applySpeed() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && ready) {
            try {
                player.playbackParams = player.playbackParams.setSpeed(speeds[speedIdx])
            } catch (_: Exception) {
            }
        }
    }

    LaunchedEffect(playing, ready) {
        while (isActive && playing && ready && !scrubbing) {
            try {
                positionMs = player.currentPosition.toLong().coerceAtLeast(0L)
                if (durationMs <= 0 && player.duration > 0) durationMs = player.duration.toLong()
            } catch (_: Exception) {
            }
            delay(120)
        }
    }

    LaunchedEffect(audioFile, uploadId) {
        if (audioFile.exists() && audioFile.length() > 0L) {
            waveform = ProtoAudioWaveform.load(ctx, audioFile)
        }
    }

    LaunchedEffect(initialVoiceTranscript) {
        if (!initialVoiceTranscript.isNullOrBlank()) transcript = initialVoiceTranscript
    }

    LaunchedEffect(sttState.text, sttState.partialText) {
        when {
            !sttState.text.isNullOrBlank() -> transcript = sttState.text
            !sttState.partialText.isNullOrBlank() -> transcript = sttState.partialText
        }
    }

    DisposableEffect(Unit) {
        player.setOnCompletionListener {
            playing = false
            positionMs = 0L
            try {
                player.seekTo(0)
            } catch (_: Exception) {
            }
        }
        onDispose {
            try {
                if (player.isPlaying) player.stop()
                player.release()
            } catch (_: Exception) {
            }
        }
    }

    suspend fun ensureFile(): Boolean {
        if (audioFile.exists() && audioFile.length() > 0) return true
        val legacy = File(ctx.cacheDir, "proto_aud_${uploadId}.$ext")
        if (legacy.exists() && legacy.length() > 0L) {
            legacy.copyTo(audioFile, overwrite = true)
            return true
        }
        val app = ctx.applicationContext as ProtoApplication
        val result =
            withContext(Dispatchers.IO) {
                app.mediaResolver.fetch(token, uploadId, mime, "audio.$ext", conversationId)
            }
        if (result.file != null) {
            if (result.file.absolutePath != audioFile.absolutePath) {
                runCatching { result.file.copyTo(audioFile, overwrite = true) }
            }
            waveform = ProtoAudioWaveform.load(ctx, audioFile)
            return audioFile.exists() && audioFile.length() > 0L
        }
        return false
    }

    LaunchedEffect(uploadId, messageId, initialVoiceTranscript, sttQueue) {
        if (!initialVoiceTranscript.isNullOrBlank() || transcript?.isNotBlank() == true) return@LaunchedEffect
        val queue = sttQueue ?: return@LaunchedEffect
        if (stt == null) return@LaunchedEffect
        if (!ensureFile()) return@LaunchedEffect
        queue.enqueue(
            SttJob(
                conversationId = conversationId,
                messageId = messageId,
                uploadId = uploadId,
                mediaFile = audioFile,
                token = token,
                languageCode = languageCode,
                mime = mime,
            ),
        )
    }

    val samples = waveform ?: ProtoAudioWaveform.placeholder()
    val speedLabel = "${speeds[speedIdx]}×"
    val progress =
        if (durationMs > 0) {
            (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
        } else {
            0f
        }

    Column(
        Modifier
            .fillMaxWidth()
            .width(260.dp)
            .clip(ProtoShapes.media)
            .background(Color.Black.copy(0.14f))
            .padding(horizontal = 8.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = {
                    scope.launch {
                        if (playing) {
                            try {
                                player.pause()
                            } catch (_: Exception) {
                            }
                            playing = false
                            return@launch
                        }
                        loading = true
                        if (!ensureFile()) {
                            loading = false
                            onDownload(uploadId, "audio.$ext")
                            return@launch
                        }
                        try {
                            if (!ready) {
                                player.reset()
                                player.setDataSource(audioFile.absolutePath)
                                player.prepare()
                                durationMs = player.duration.toLong().coerceAtLeast(0L)
                                ready = true
                                applySpeed()
                            }
                            player.start()
                            playing = true
                        } catch (_: Exception) {
                            onDownload(uploadId, "audio.$ext")
                        }
                        loading = false
                    }
                },
                enabled = !loading,
                modifier = Modifier.size(44.dp),
            ) {
                if (loading) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = textColor)
                } else {
                    Icon(
                        if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (playing) UiStrings.pause else UiStrings.play,
                        tint = textColor,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                VoiceStaticWaveform(
                    samples = samples,
                    progress = progress,
                    activeColor = ProtoOrange,
                    inactiveColor = textColor.copy(0.32f),
                    speedLabel = speedLabel,
                    onSpeedTap = {
                        speedIdx = (speedIdx + 1) % speeds.size
                        applySpeed()
                        scope.launch { app.prefs.setVoicePlaybackSpeedIdx(speedIdx) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    onSeek =
                        if (ready && durationMs > 0) {
                            { frac ->
                                val target = (durationMs * frac).toLong()
                                positionMs = target
                                scrubbing = true
                                try {
                                    player.seekTo(target.toInt())
                                } catch (_: Exception) {
                                }
                                scrubbing = false
                            }
                        } else {
                            null
                        },
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatMs(positionMs), style = MaterialTheme.typography.labelSmall, color = textColor.copy(0.85f))
                    Text(
                        formatMs(if (durationMs > 0) durationMs else 0L),
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor.copy(0.6f),
                    )
                }
            }
            TextButton(
                onClick = {
                    speedIdx = (speedIdx + 1) % speeds.size
                    applySpeed()
                },
            ) {
                Text("${speeds[speedIdx]}×", color = textColor, style = MaterialTheme.typography.labelLarge)
            }
        }
        if (stt != null) {
            val phaseLabel =
                when (sttState.phase) {
                    "decode" -> UiStrings.sttPhaseListening
                    "writing" -> UiStrings.sttPhaseWriting
                    "partial" -> UiStrings.sttPhaseWriting
                    "server" -> UiStrings.sttServerBusy
                    "queued" -> UiStrings.sttAutoQueued
                    "failed" -> UiStrings.sttFailed
                    else -> null
                }
            if (sttBusy || sttState.phase == "failed") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 2.dp),
                ) {
                    if (sttBusy) {
                        CircularProgressIndicator(
                            Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = textColor.copy(0.85f),
                        )
                    }
                    phaseLabel?.let { label ->
                        Text(label, color = textColor.copy(0.75f), style = MaterialTheme.typography.labelSmall)
                    }
                    if (sttState.phase == "failed") {
                        TextButton(
                            onClick = {
                                val queue = sttQueue ?: return@TextButton
                                scope.launch {
                                    if (!ensureFile()) {
                                        onDownload(uploadId, "audio.$ext")
                                        return@launch
                                    }
                                    queue.enqueue(
                                        SttJob(
                                            conversationId,
                                            messageId,
                                            uploadId,
                                            audioFile,
                                            token,
                                            languageCode,
                                            mime,
                                        ),
                                        force = true,
                                    )
                                }
                            },
                        ) {
                            Text(UiStrings.transcribe, style = MaterialTheme.typography.labelSmall, color = textColor.copy(0.9f))
                        }
                    }
                }
            }
        }
        transcript?.takeIf { it.isNotBlank() }?.let { t ->
            val isPartial = sttState.phase == "partial" || (sttBusy && sttState.partialText == t)
            val badge =
                when (transcriptSource?.lowercase()) {
                    "server" -> UiStrings.sttBadgeServer
                    else -> UiStrings.sttBadgeLocal
                }
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(badge, style = MaterialTheme.typography.labelSmall, color = textColor.copy(0.55f))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = {
                            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("voice", t))
                            Toast.makeText(ctx, UiStrings.copied, Toast.LENGTH_SHORT).show()
                        },
                    ) {
                        Text(UiStrings.sttCopyTranscript, style = MaterialTheme.typography.labelSmall, color = textColor.copy(0.85f))
                    }
                    TextButton(
                        onClick = {
                            scope.launch {
                                val target = languageCode.lowercase().let { if (it in setOf("ru", "it")) it else "en" }
                                val reply =
                                    withContext(Dispatchers.IO) { api.assistixTranslate(token, t, target) }
                                if (reply.ok && reply.text.isNotBlank()) {
                                    transcript = reply.text
                                } else {
                                    Toast.makeText(ctx, UiStrings.assistixError, Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                    ) {
                        Text(UiStrings.sttTranslateTranscript, style = MaterialTheme.typography.labelSmall, color = textColor.copy(0.85f))
                    }
                }
            }
            VoiceTranscriptText(
                text = t,
                query = searchHighlight,
                color = textColor.copy(if (isPartial) 0.72f else 0.9f),
                modifier =
                    Modifier.combinedClickable(
                        onClick = {},
                        onLongClick = {
                            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("voice", t))
                            Toast.makeText(ctx, UiStrings.copied, Toast.LENGTH_SHORT).show()
                        },
                    ),
            )
            if (aiEnabled && t.isNotBlank()) {
                var voiceSummary by remember(t) { mutableStateOf<String?>(null) }
                var voiceSummaryBusy by remember { mutableStateOf(false) }
                TextButton(
                    onClick = {
                        if (voiceSummaryBusy) return@TextButton
                        scope.launch {
                            voiceSummaryBusy = true
                            voiceSummary = assistixSummarizeVoice(api, token, t, languageCode)
                            voiceSummaryBusy = false
                            if (voiceSummary == null) {
                                android.widget.Toast.makeText(ctx, UiStrings.assistixError, android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    enabled = !voiceSummaryBusy,
                ) {
                    Text(UiStrings.assistixSummarizeVoice, style = MaterialTheme.typography.labelMedium, color = textColor.copy(0.9f))
                }
                voiceSummary?.let { s ->
                    Text(s, color = textColor.copy(0.85f), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
    }
}

@Composable
private fun VoiceTranscriptText(
    text: String,
    query: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val style = MaterialTheme.typography.bodySmall
    val q = query.trim()
    if (q.isBlank()) {
        Text(text, color = color, style = style, modifier = modifier)
        return
    }
    val lower = text.lowercase()
    val qLower = q.lowercase()
    Text(
        text =
            buildAnnotatedString {
                var i = 0
                while (i < text.length) {
                    val idx = lower.indexOf(qLower, i)
                    if (idx < 0) {
                        append(text.substring(i))
                        break
                    }
                    if (idx > i) append(text.substring(i, idx))
                    withStyle(SpanStyle(background = ProtoOrange.copy(alpha = 0.38f))) {
                        append(text.substring(idx, (idx + q.length).coerceAtMost(text.length)))
                    }
                    i = idx + q.length
                }
            },
        color = color,
        style = style,
        modifier = modifier,
    )
}

private fun formatMs(ms: Long): String {
    val totalSec = (ms / 1000).toInt()
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}

private fun guessMime(name: String?): String {
    val n = name?.lowercase() ?: return "application/octet-stream"
    return when {
        n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp") -> "image/jpeg"
        n.endsWith(".mp4") || n.endsWith(".webm") -> "video/mp4"
        n.endsWith(".m4a") || n.endsWith(".mp3") || n.endsWith(".ogg") || n.contains("voice") -> "audio/mp4"
        else -> "application/octet-stream"
    }
}
