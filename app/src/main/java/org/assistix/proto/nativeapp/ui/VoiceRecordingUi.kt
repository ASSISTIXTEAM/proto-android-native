package org.assistix.proto.nativeapp.ui

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp
import java.io.File
import org.assistix.proto.nativeapp.data.ProtoAudioWaveform

internal class VoiceRecorderHandle(
    val file: File,
    val recorder: MediaRecorder,
    val startedAtMs: Long,
)

internal object VoiceRecorderFactory {
    fun start(context: Context): VoiceRecorderHandle? {
        val file = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
        val rec =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
        return runCatching {
            rec.setAudioSource(MediaRecorder.AudioSource.MIC)
            rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            try {
                rec.setAudioSamplingRate(48_000)
                rec.setAudioEncodingBitRate(256_000)
            } catch (_: Exception) {
            }
            rec.setOutputFile(file.absolutePath)
            rec.prepare()
            rec.start()
            VoiceRecorderHandle(file, rec, System.currentTimeMillis())
        }.getOrNull()
    }

    fun stop(handle: VoiceRecorderHandle?): File? {
        val h = handle ?: return null
        runCatching { h.recorder.stop() }
        runCatching { h.recorder.release() }
        return h.file.takeIf { it.exists() && it.length() > 0L }
    }

    fun cancel(handle: VoiceRecorderHandle?) {
        val h = handle ?: return
        runCatching { h.recorder.stop() }
        runCatching { h.recorder.release() }
        runCatching { h.file.delete() }
    }

    fun pollLevel(handle: VoiceRecorderHandle?): Float {
        val amp =
            runCatching { handle?.recorder?.maxAmplitude ?: 0 }.getOrDefault(0)
        return ProtoAudioWaveform.normalizeRecorderLevel(amp)
    }
}

@Composable
internal fun VoiceRecordingBar(
    elapsedMs: Long,
    levels: List<Float>,
    cancelArmed: Boolean,
    locked: Boolean = false,
    onStop: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val bg by animateColorAsState(
        when {
            cancelArmed -> MaterialTheme.colorScheme.errorContainer
            locked -> ProtoOrange.copy(0.14f)
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
        label = "recBarBg",
    )
    val tint by animateColorAsState(
        when {
            cancelArmed -> MaterialTheme.colorScheme.error
            locked -> ProtoOrange
            else -> ProtoOrange
        },
        label = "recBarTint",
    )
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = ProtoShapes.field,
        color = bg.copy(alpha = 0.95f),
        tonalElevation = 2.dp,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = null,
                        tint = if (cancelArmed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        when {
                            cancelArmed -> UiStrings.slideToCancelArmed
                            locked -> UiStrings.recordLocked
                            else -> UiStrings.slideToCancel
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (cancelArmed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!cancelArmed && !locked) {
                        Text(
                            " · ${UiStrings.slideUpToLock}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.75f),
                        )
                    }
                    if (locked) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            tint = ProtoOrange,
                            modifier = Modifier.size(16.dp).padding(start = 4.dp),
                        )
                    }
                }
                Text(
                    formatVoiceDuration(elapsedMs),
                    style = MaterialTheme.typography.titleSmall,
                    color = tint,
                )
            }
            if (locked && onStop != null) {
                FilledTonalButton(
                    onClick = onStop,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(UiStrings.tapToStopRecord, modifier = Modifier.padding(start = 6.dp))
                }
            }
            VoiceLiveWaveform(
                levels = levels,
                tint = tint,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
    }
}

@Composable
internal fun VoiceMicHoldButton(
    enabled: Boolean,
    recording: Boolean,
    cancelArmed: Boolean,
    onPress: () -> Unit,
    locked: Boolean = false,
    onCancelArmedChange: (Boolean) -> Unit = {},
    onLockChange: (Boolean) -> Unit = {},
    onRelease: (cancelled: Boolean, keepLocked: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val micTint by animateColorAsState(
        when {
            cancelArmed -> MaterialTheme.colorScheme.error
            recording -> ProtoOrange
            else -> MaterialTheme.colorScheme.onSurface
        },
        label = "micTint",
    )
    val bgTint by animateColorAsState(
        when {
            cancelArmed -> MaterialTheme.colorScheme.error.copy(0.18f)
            recording -> ProtoOrange.copy(0.2f)
            else -> Color.Transparent
        },
        label = "micBg",
    )
    Box(
        modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(bgTint)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    onPress()
                    var cancelled = false
                    var isLocked = locked
                    try {
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            val dx = change.position.x - down.position.x
                            val dy = change.position.y - down.position.y
                            if (dx < -100f) {
                                if (!cancelled) {
                                    cancelled = true
                                    onCancelArmedChange(true)
                                }
                            } else if (dx > -72f && cancelled) {
                                cancelled = false
                                onCancelArmedChange(false)
                            }
                            if (dy < -88f && !cancelled && !isLocked) {
                                isLocked = true
                                onLockChange(true)
                            }
                            if (change.positionChange().x != 0f || change.positionChange().y != 0f) {
                                change.consume()
                            }
                        } while (true)
                    } finally {
                        onRelease(cancelled, isLocked && !cancelled)
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Default.Mic,
            contentDescription = UiStrings.voiceMessage,
            tint = micTint,
            modifier = Modifier.size(26.dp),
        )
    }
}

internal fun formatVoiceDuration(ms: Long): String {
    val totalSec = (ms / 1000).toInt().coerceAtLeast(0)
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}
