package org.assistix.proto.nativeapp.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import org.assistix.proto.nativeapp.data.CallUiState
import org.assistix.proto.nativeapp.data.ProtoApi
import org.assistix.proto.nativeapp.data.ProtoCallGateway

private val CALL_REACTION_EMOJIS = listOf("👍", "❤️", "😂", "👏", "🔥", "😮", "🎉")

@Composable
fun CallOverlay(
    state: CallUiState,
    api: ProtoApi,
    token: String?,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onHangup: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleVideo: () -> Unit,
    onFlipCamera: () -> Unit,
    onSendReaction: (String) -> Unit,
    calls: ProtoCallGateway? = null,
) {
    if (!state.active) return
    val ctx = LocalContext.current
    val avatarUrl = state.peerAvatarUploadId?.let { api.mediaUrl(it) }
    val showRemoteVideo =
        state.hasRemoteVideo && state.remoteVideoEnabled && !state.incoming
    val egl = calls?.videoEglContext()

    Box(Modifier.fillMaxSize().navigationBarsPadding()) {
        if (showRemoteVideo) {
            CallVideoRenderer(
                track = calls?.remoteVideoTrack(),
                eglContext = egl,
                mirror = false,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.12f)),
            )
        } else if (avatarUrl != null && !token.isNullOrBlank()) {
            AsyncImage(
                model =
                    ImageRequest.Builder(ctx).data(avatarUrl).apply {
                        api.authHeaders(token).forEach { (k, v) -> addHeader(k, v) }
                    }.build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().blur(28.dp),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF3D2914), Color(0xFF121212), Color(0xFF0A0A0A)),
                        ),
                    ),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = if (showRemoteVideo) 0.28f else 0.55f)),
        )

        if (state.withVideo && state.videoEnabled && !state.incoming && egl != null) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 12.dp, end = 16.dp)
                    .size(width = 112.dp, height = 160.dp)
                    .clip(RoundedCornerShape(16.dp)),
            ) {
                CallVideoRenderer(
                    track = calls?.localVideoTrack(),
                    eglContext = egl,
                    mirror = state.cameraFront,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CallTopBar(state)
            if (!state.incoming) {
                Spacer(Modifier.height(10.dp))
                CallReactionRow(onSendReaction)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                when {
                    state.isGroupCall -> UiStrings.groupCall
                    state.withVideo -> UiStrings.videoCall
                    else -> UiStrings.audioCall
                },
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            val statusLine =
                when {
                    state.networkProbing -> UiStrings.callNetworkChecking
                    state.mediaConnected -> UiStrings.callDurationLabel(state.callDurationSec)
                    state.isGroupCall && !state.incoming && !state.mediaConnected ->
                        when {
                            state.joinedParticipantCount > 1 -> UiStrings.groupCallInRoom
                            state.status.isNotBlank() -> state.status
                            else -> UiStrings.groupCallWaiting
                        }
                    else -> state.status
                }
            Text(statusLine, color = Color(0xFFAEAEB2), style = MaterialTheme.typography.bodyMedium)
            if (state.reconnecting) {
                Spacer(Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = ProtoOrange.copy(alpha = 0.22f),
                ) {
                    Text(
                        UiStrings.callReconnecting,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        color = ProtoOrange,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            if (state.networkProbeRttMs > 0 && !state.networkProbing) {
                Text(
                    UiStrings.callNetworkLabel(state.networkProbeBars.coerceIn(0, 4)),
                    color = Color(0xFF8E8E93),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            if (state.mediaConnected || (!state.incoming && !state.networkProbing)) {
                Text(
                    UiStrings.callAudioProcessingHint,
                    color = Color(0xFF6E6E73),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            if (state.remoteMuted) {
                Text(UiStrings.peerMuted, color = Color(0xFFFFAB91), style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.height(8.dp))
            DisplayNameWithEmoji(
                displayName = state.peerLabel,
                statusEmoji = state.peerStatusEmoji,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
            )

            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                if (!showRemoteVideo) {
                    ProtoAvatar(
                        uploadId = state.peerAvatarUploadId,
                        displayName = state.peerLabel,
                        size = 148.dp,
                        api = api,
                        token = token,
                    )
                }
                state.peerReactionEmoji?.let { emoji ->
                    FloatingCallReaction(emoji = emoji, nonce = state.peerReactionNonce)
                }
            }
            if (state.isGroupCall && state.participants.isNotEmpty()) {
                Text(
                    UiStrings.callParticipantsTitle,
                    color = Color(0xFFAEAEB2),
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(state.participants, key = { it.userId }) { p ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = 88.dp)) {
                            Box(
                                Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (p.inCall) MaterialTheme.colorScheme.primary.copy(0.35f)
                                        else Color(0xFF3A3A3C),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    p.label.take(1).uppercase(),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                p.label,
                                fontSize = 11.sp,
                                color = Color(0xFFAEAEB2),
                                maxLines = 1,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            } else {
                Spacer(Modifier.height(8.dp))
            }
        }

        Surface(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 16.dp),
            shape = RoundedCornerShape(28.dp),
            color = Color(0xFF1C1C1E).copy(alpha = 0.94f),
            tonalElevation = 8.dp,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (!state.incoming) {
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        CallControl(
                            if (state.muted) Icons.Default.MicOff else Icons.Default.Mic,
                            if (state.muted) UiStrings.unmute else UiStrings.mute,
                            state.muted,
                            onToggleMute,
                        )
                        CallControl(Icons.Default.VolumeUp, UiStrings.speaker, state.speakerOn, onToggleSpeaker)
                        if (state.withVideo && !state.isGroupCall) {
                            CallControl(
                                if (state.videoEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                                UiStrings.video,
                                !state.videoEnabled,
                                onToggleVideo,
                            )
                            CallControl(Icons.Default.Cameraswitch, UiStrings.flipCamera, false, onFlipCamera)
                        } else if (!state.isGroupCall) {
                            CallControl(
                                Icons.Default.Videocam,
                                UiStrings.video,
                                false,
                                onToggleVideo,
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(32.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (state.incoming) {
                        IconButton(
                            onClick = onDecline,
                            modifier =
                                Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF3A3A3C)),
                        ) {
                            Icon(Icons.Default.CallEnd, contentDescription = UiStrings.decline, tint = Color.White, modifier = Modifier.size(28.dp))
                        }
                        IconButton(
                            onClick = onAccept,
                            modifier =
                                Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                        ) {
                            Icon(Icons.Default.Phone, contentDescription = UiStrings.accept, tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                    } else {
                        IconButton(
                            onClick = onHangup,
                            modifier =
                                Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFC62828)),
                        ) {
                            Icon(Icons.Default.CallEnd, contentDescription = UiStrings.endCall, tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CallTopBar(state: CallUiState) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF2C2C2E).copy(alpha = 0.92f),
        ) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    tint = Color(0xFF81C784),
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    UiStrings.callEncrypted,
                    color = Color(0xFFE8E8ED),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            val bars =
                if (state.networkProbing) 0
                else state.connectionBars.coerceAtLeast(state.networkProbeBars).coerceIn(0, 4)
            CallSignalBars(bars = bars)
            if (state.networkProbing) {
                Text(
                    "…",
                    color = Color(0xFFAEAEB2),
                    fontSize = 12.sp,
                )
            }
            if (state.usingRelay) {
                Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFF3A3A3C)) {
                    Text(
                        UiStrings.callRelay,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 11.sp,
                        color = Color(0xFFAEAEB2),
                    )
                }
            }
        }
    }
}

@Composable
private fun CallSignalBars(bars: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        repeat(4) { i ->
            val h = (6 + i * 4).dp
            val active = i < bars
            Box(
                Modifier
                    .size(width = 4.dp, height = h)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (active) MaterialTheme.colorScheme.primary
                        else Color(0xFF48484A),
                    ),
            )
        }
    }
}

@Composable
private fun CallReactionRow(onSendReaction: (String) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(CALL_REACTION_EMOJIS) { emoji ->
            Surface(
                shape = CircleShape,
                color = Color(0xFF2C2C2E).copy(alpha = 0.9f),
                modifier = Modifier.clickable { onSendReaction(emoji) },
            ) {
                Text(
                    emoji,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    fontSize = 22.sp,
                )
            }
        }
    }
}

@Composable
private fun FloatingCallReaction(emoji: String, nonce: Long) {
    val scale = remember(nonce) { Animatable(0.4f) }
    val alpha = remember(nonce) { Animatable(0f) }
    LaunchedEffect(nonce) {
        scale.snapTo(0.4f)
        alpha.snapTo(0f)
        scale.animateTo(1.15f, tween(220))
        alpha.animateTo(1f, tween(180))
        kotlinx.coroutines.delay(1400)
        alpha.animateTo(0f, tween(400))
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Text(
            emoji,
            fontSize = 56.sp,
            modifier =
                Modifier
                    .scale(scale.value)
                    .alpha(alpha.value)
                    .padding(top = 8.dp),
        )
    }
}

@Composable
private fun CallControl(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = 72.dp)) {
        IconButton(
            onClick = onClick,
            modifier =
                Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(if (active) MaterialTheme.colorScheme.primary.copy(0.85f) else Color(0xFF3A3A3C)),
        ) {
            Icon(icon, contentDescription = label, tint = Color.White)
        }
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 11.sp, color = Color(0xFFAEAEB2), maxLines = 1)
    }
}
