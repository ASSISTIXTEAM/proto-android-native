package org.assistix.proto.nativeapp.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.assistix.proto.nativeapp.data.AssistixRateLimit
import org.assistix.proto.nativeapp.data.AssistixThread

@Composable
fun AssistixHeroBanner(
    rateLimit: AssistixRateLimit?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(ProtoShapes.card)
            .background(
                Brush.linearGradient(
                    listOf(
                        ProtoOrange.copy(0.55f),
                        ProtoOrange.copy(0.22f),
                        MaterialTheme.colorScheme.surfaceVariant.copy(0.45f),
                    ),
                ),
            )
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(ProtoShapes.button)
                    .background(Brush.linearGradient(listOf(ProtoOrange, ProtoOrange.copy(0.65f)))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Psychology, null, tint = Color.White, modifier = Modifier.size(30.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    "PROTO",
                    color = Color.White.copy(0.85f),
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    fontSize = 11.sp,
                )
                Text(
                    UiStrings.assistixAiTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Text(
                    UiStrings.assistixAiSubtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(0.88f),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        rateLimit?.let { limit ->
            Spacer(Modifier.height(16.dp))
            AssistixRateMeter(limit)
        }
    }
}

@Composable
fun AssistixRateMeter(limit: AssistixRateLimit) {
    val exhausted = limit.isExhausted()
    val fraction =
        if (limit.limit <= 0) {
            0f
        } else {
            (limit.remaining.toFloat() / limit.limit.toFloat()).coerceIn(0f, 1f)
        }
    val animated by animateFloatAsState(fraction, animationSpec = ProtoMotion.gentleSpring(false), label = "aiRate")
    Column(Modifier.fillMaxWidth()) {
        Text(
            UiStrings.assistixRateRemainingFmt(limit.remaining, limit.limit),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (exhausted) Color(0xFFFFCDD2) else Color.White,
        )
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { animated },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(ProtoShapes.pill),
            color = if (exhausted) MaterialTheme.colorScheme.error else Color.White,
            trackColor = Color.White.copy(0.25f),
        )
    }
}

@Composable
fun AssistixThreadGlassRow(
    thread: AssistixThread,
    selected: Boolean,
    selectionMode: Boolean,
    modifier: Modifier = Modifier,
) {
    val borderColor =
        when {
            selectionMode && selected -> ProtoOrange.copy(0.65f)
            else -> MaterialTheme.colorScheme.outline.copy(0.08f)
        }
    ProfileGlassCard(
        modifier =
            modifier
                .fillMaxWidth()
                .border(1.5.dp, borderColor, ProtoShapes.card),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(ProtoOrange.copy(0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Psychology, null, tint = ProtoOrange, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    thread.title.ifBlank { UiStrings.assistixChat },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (thread.preview.isNotBlank()) {
                    Text(
                        thread.preview,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            if (selectionMode) {
                Box(
                    Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(if (selected) ProtoOrange else MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) {
                        Text("✓", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun AssistixEmptyState(
    onNewChat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ProfileGlassCard(modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Default.Psychology, null, tint = ProtoOrange, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(12.dp))
            Text(
                UiStrings.assistixThreadsEmpty,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                UiStrings.assistixHint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            ProtoPrimaryButton(UiStrings.assistixNewChat, onNewChat, Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun AssistixChatGlassBar(
    title: String,
    onBack: () -> Unit,
    onExport: (() -> Unit)?,
    onClear: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    ProfileGlassCard(modifier.fillMaxWidth()) {
        Row(
            modifier.padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = UiStrings.back,
                    tint = ProtoOrange,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    UiStrings.assistixAiSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (onExport != null) {
                IconButton(onClick = onExport) {
                    Icon(Icons.Outlined.FileDownload, contentDescription = UiStrings.assistixExport)
                }
            }
            if (onClear != null) {
                IconButton(onClick = onClear) {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        contentDescription = UiStrings.assistixDeleteHistory,
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
fun AssistixAiBubble(line: AssistixAiLine) {
    val mine = line.role == "user"
    val transition = rememberInfiniteTransition(label = "aiPulse")
    val pulse by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pulse",
    )
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
    ) {
        if (!mine) {
            Box(
                Modifier
                    .padding(end = 8.dp, top = 4.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(ProtoOrange.copy(0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Psychology, null, tint = ProtoOrange, modifier = Modifier.size(18.dp))
            }
        }
        Column(
            Modifier
                .fillMaxWidth(if (mine) 0.88f else 0.92f)
                .clip(ProtoShapes.bubble)
                .background(
                    if (mine) {
                        Brush.linearGradient(listOf(ProtoOrange.copy(0.28f), ProtoOrange.copy(0.14f)))
                    } else {
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.surface.copy(0.95f),
                                MaterialTheme.colorScheme.surfaceVariant.copy(0.55f),
                            ),
                        )
                    },
                )
                .border(
                    1.dp,
                    when {
                        mine -> ProtoOrange.copy(0.4f)
                        line.streaming -> ProtoOrange.copy(pulse * 0.55f)
                        else -> MaterialTheme.colorScheme.outline.copy(0.12f)
                    },
                    ProtoShapes.bubble,
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            if (line.streaming && line.text.isEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = ProtoOrange)
                    Text(
                        UiStrings.assistixThinking,
                        modifier = Modifier.padding(start = 10.dp).alpha(pulse),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Text(
                    line.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

data class AssistixAiLine(
    val role: String,
    val text: String,
    val streaming: Boolean = false,
)

@Composable
fun AssistixComposerGlass(
    rateLimit: AssistixRateLimit?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    ProfileGlassCard(modifier.fillMaxWidth()) {
        rateLimit?.let { limit ->
            AssistixRateMeterCompact(limit)
            Spacer(Modifier.height(8.dp))
        }
        content()
    }
}

@Composable
private fun AssistixRateMeterCompact(limit: AssistixRateLimit) {
    Text(
        UiStrings.assistixRateRemainingFmt(limit.remaining, limit.limit),
        style = MaterialTheme.typography.labelMedium,
        color =
            if (limit.isExhausted()) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
    )
}
