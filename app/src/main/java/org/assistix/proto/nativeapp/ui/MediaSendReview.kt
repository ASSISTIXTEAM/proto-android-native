package org.assistix.proto.nativeapp.ui

import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import org.assistix.proto.nativeapp.data.ProtoAudioWaveform
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.File
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.assistix.proto.nativeapp.data.PendingOutgoingMedia
import org.assistix.proto.nativeapp.data.mediaKindFromMime

@Composable
fun MediaSendReviewPanel(
    pending: PendingOutgoingMedia,
    caption: String,
    onCaptionChange: (String) -> Unit,
    onReplace: () -> Unit,
    onCancel: () -> Unit,
    onConfirm: (PendingOutgoingMedia) -> Unit,
    modifier: Modifier = Modifier,
) {
    var voiceStart by remember(pending) { mutableFloatStateOf(0f) }
    var voiceEnd by remember(pending) { mutableFloatStateOf(1f) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = ProtoShapes.field,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
        tonalElevation = 2.dp,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(UiStrings.mediaSendPreview, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                IconButton(onClick = onCancel, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Close, contentDescription = UiStrings.cancel)
                }
            }
            when (val p = pending) {
                is PendingOutgoingMedia.Single -> SinglePreview(p)
                is PendingOutgoingMedia.Album -> AlbumPreview(p)
                is PendingOutgoingMedia.Voice ->
                    VoicePreview(
                        file = p.file,
                        startFraction = voiceStart,
                        endFraction = voiceEnd,
                        onRangeChange = { s, e ->
                            voiceStart = s
                            voiceEnd = e
                        },
                    )
            }
            if (pending !is PendingOutgoingMedia.Voice) {
                OutlinedTextField(
                    value = caption,
                    onValueChange = onCaptionChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(UiStrings.message) },
                    maxLines = 3,
                    shape = ProtoShapes.field,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onReplace, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                    Text(UiStrings.mediaReplace, modifier = Modifier.padding(start = 4.dp))
                }
                TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                    Text(UiStrings.cancel)
                }
                TextButton(
                    onClick = {
                        val out =
                            when (val p = pending) {
                                is PendingOutgoingMedia.Voice -> {
                                    val dur = voiceDurationMs(p.file).coerceAtLeast(1L)
                                    val startMs = (voiceStart * dur).toLong()
                                    val endMs = (voiceEnd * dur).toLong().coerceAtLeast(startMs + 300L)
                                    p.copy(trimStartMs = startMs, trimEndMs = endMs)
                                }
                                else -> p
                            }
                        onConfirm(out)
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(UiStrings.mediaSendConfirm, color = ProtoOrange, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun SinglePreview(item: PendingOutgoingMedia.Single) {
    val ctx = LocalContext.current
    val kind = mediaKindFromMime(item.mime, item.displayName)
    when (kind) {
        "image" -> {
            AsyncImage(
                model = ImageRequest.Builder(ctx).data(Uri.fromFile(item.file)).crossfade(true).build(),
                contentDescription = null,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .clip(ProtoShapes.media),
                contentScale = ContentScale.Fit,
            )
        }
        "video" -> {
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp)
                    .clip(ProtoShapes.media),
                contentAlignment = Alignment.Center,
            ) {
                Text("🎬 ${item.displayName}", style = MaterialTheme.typography.bodyLarge)
            }
        }
        else -> {
            Text("📎 ${item.displayName}", style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun AlbumPreview(item: PendingOutgoingMedia.Album) {
    val ctx = LocalContext.current
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.heightIn(max = 240.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(item.files, key = { it.absolutePath }) { f ->
            AsyncImage(
                model = ImageRequest.Builder(ctx).data(Uri.fromFile(f)).crossfade(true).build(),
                contentDescription = null,
                modifier =
                    Modifier
                        .aspectRatio(1f)
                        .clip(ProtoShapes.media),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
private fun VoicePreview(
    file: File,
    startFraction: Float,
    endFraction: Float,
    onRangeChange: (Float, Float) -> Unit,
) {
    val ctx = LocalContext.current
    val player = remember { MediaPlayer() }
    var ready by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var waveform by remember(file) { mutableStateOf<FloatArray?>(null) }

    LaunchedEffect(file) {
        waveform = withContext(Dispatchers.IO) { ProtoAudioWaveform.load(ctx, file) }
        runCatching {
            player.reset()
            player.setDataSource(file.absolutePath)
            player.prepare()
            durationMs = player.duration.toLong().coerceAtLeast(1L)
            ready = true
        }
    }

    LaunchedEffect(playing, ready) {
        while (isActive && playing && ready) {
            positionMs = player.currentPosition.toLong()
            val endMs = (endFraction * durationMs).toLong()
            if (positionMs >= endMs) {
                player.pause()
                playing = false
                runCatching { player.seekTo((startFraction * durationMs).toInt()) }
            }
            delay(80)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                if (player.isPlaying) player.stop()
                player.release()
            }
        }
    }

    val samples = waveform ?: ProtoAudioWaveform.placeholder()
    val playProgress =
        if (durationMs > 0) {
            (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
        } else {
            0f
        }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = {
                    if (!ready) return@IconButton
                    if (playing) {
                        player.pause()
                        playing = false
                    } else {
                        runCatching {
                            player.seekTo((startFraction * durationMs).toInt())
                            player.start()
                            playing = true
                        }
                    }
                },
            ) {
                Icon(
                    if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = UiStrings.play,
                )
            }
            Column(Modifier.weight(1f)) {
                VoiceTrimWaveform(
                    samples = samples,
                    playProgress = playProgress,
                    trimStart = startFraction,
                    trimEnd = endFraction,
                    onTrimChange = onRangeChange,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "${formatMs(positionMs)} / ${formatMs(durationMs)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(UiStrings.voiceTrimHint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider()
    }
}

private fun voiceDurationMs(file: File): Long =
    runCatching {
        val p = MediaPlayer()
        try {
            p.setDataSource(file.absolutePath)
            p.prepare()
            p.duration.toLong().coerceAtLeast(1L)
        } finally {
            runCatching { p.release() }
        }
    }.getOrDefault(1000L)

private fun formatMs(ms: Long): String {
    val s = (ms / 1000).toInt()
    return "%d:%02d".format(s / 60, s % 60)
}
