package org.assistix.proto.nativeapp.ui

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import org.assistix.proto.nativeapp.data.ProtoApi
import org.assistix.proto.nativeapp.data.resolveDisplayName

enum class ProfileEditorMode { Card, Edit }

@Composable
fun ProfileModeSwitcher(
    mode: ProfileEditorMode,
    onMode: (ProfileEditorMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(ProtoShapes.pill)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(0.55f))
            .padding(4.dp),
    ) {
        ProfileModeTab(
            label = UiStrings.profileTabCard,
            selected = mode == ProfileEditorMode.Card,
            onClick = { onMode(ProfileEditorMode.Card) },
            modifier = Modifier.weight(1f),
        )
        ProfileModeTab(
            label = UiStrings.profileTabEdit,
            selected = mode == ProfileEditorMode.Edit,
            onClick = { onMode(ProfileEditorMode.Edit) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ProfileModeTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg =
        if (selected) {
            Brush.horizontalGradient(listOf(ProtoOrange, ProtoOrange.copy(0.82f)))
        } else {
            Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
        }
    Box(
        modifier
            .clip(ProtoShapes.pill)
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
fun ProfileCompletenessMeter(
    percent: Int,
    modifier: Modifier = Modifier,
) {
    val target = (percent.coerceIn(0, 100) / 100f)
    val animated by animateFloatAsState(target, animationSpec = ProtoMotion.gentleSpring(false), label = "profilePct")
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(UiStrings.profileCompleteness, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text("$percent%", color = ProtoOrange, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { animated },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(ProtoShapes.pill),
            color = ProtoOrange,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            UiStrings.profileCompletenessHint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

fun profileCompletenessPercent(
    hasAvatar: Boolean,
    displayName: String,
    bio: String,
    status: String,
    statusEmoji: String,
    nick: String,
): Int {
    var score = 0
    if (hasAvatar) score += 25
    if (displayName.trim().length >= 2) score += 15
    if (nick.trim().length >= 3) score += 20
    if (status.trim().isNotBlank() || statusEmoji.isNotBlank()) score += 15
    if (bio.trim().length >= 8) score += 25
    return score.coerceIn(0, 100)
}

@Composable
fun ProfileGlassCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(ProtoShapes.card)
            .drawBehind {
                drawRoundRect(
                    brush =
                        Brush.linearGradient(
                            listOf(ProtoOrange.copy(0.45f), Color.Transparent, ProtoOrange.copy(0.25f)),
                            start = Offset.Zero,
                            end = Offset(size.width, size.height),
                        ),
                    size = size,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(20.dp.toPx()),
                )
            }
            .background(MaterialTheme.colorScheme.surface.copy(0.92f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(0.08f), ProtoShapes.card)
            .padding(18.dp),
    ) {
        content()
    }
}

@Composable
fun ProfileImmersiveHeader(
    avatarUploadId: String?,
    displayName: String,
    statusEmoji: String,
    nick: String,
    subtitle: String?,
    token: String?,
    api: ProtoApi,
    modifier: Modifier = Modifier,
    showProtoBadge: Boolean = true,
    onAvatarClick: (() -> Unit)? = null,
) {
    val ctx = LocalContext.current
    val heroUrl = avatarUploadId?.let { api.mediaUrl(it) }
    val label = resolveDisplayName(displayName, nick)

    Box(
        modifier
            .fillMaxWidth()
            .height(268.dp)
            .clip(ProtoShapes.card),
    ) {
        if (heroUrl != null && !token.isNullOrBlank()) {
            AsyncImage(
                model =
                    ImageRequest.Builder(ctx).data(heroUrl).apply {
                        api.authHeaders(token).forEach { (k, v) -> addHeader(k, v) }
                    }.build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(
                                ProtoOrange.copy(0.75f),
                                ProtoOrange.copy(0.35f),
                                MaterialTheme.colorScheme.primaryContainer.copy(0.5f),
                            ),
                        ),
                    ),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(0.15f), Color.Black.copy(0.72f), Color.Black.copy(0.88f)),
                    ),
                ),
        )
        if (showProtoBadge) {
            Text(
                "PROTO",
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp)
                        .clip(ProtoShapes.pill)
                        .background(Color.White.copy(0.12f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                color = Color.White,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                fontSize = 11.sp,
            )
        }
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                contentAlignment = Alignment.BottomEnd,
                modifier = Modifier.clickable(enabled = onAvatarClick != null) { onAvatarClick?.invoke() },
            ) {
                Box(
                    Modifier
                        .size(124.dp)
                        .clip(CircleShape)
                        .border(3.dp, ProtoOrange, CircleShape)
                        .padding(3.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface),
                ) {
                    ProtoAvatar(
                        uploadId = avatarUploadId,
                        displayName = label,
                        size = 118.dp,
                        api = api,
                        token = token,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label,
                    fontWeight = FontWeight.Bold,
                    fontSize = 26.sp,
                    color = Color.White,
                    maxLines = 1,
                )
                if (statusEmoji.isNotBlank()) {
                    Text(statusEmoji, fontSize = 24.sp, modifier = Modifier.padding(start = 6.dp))
                }
            }
            Text(
                "@$nick",
                color = Color.White.copy(0.85f),
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
            subtitle?.takeIf { it.isNotBlank() }?.let { sub ->
                Spacer(Modifier.height(10.dp))
                Text(
                    sub,
                    color = Color.White.copy(0.9f),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            }
        }
    }
}

@Composable
fun ProfileActionTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(ProtoShapes.field)
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(0.4f))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(ProtoShapes.button)
                .background(Brush.linearGradient(listOf(ProtoOrange.copy(0.9f), ProtoOrange.copy(0.55f)))),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Icon(icon, contentDescription = title, tint = Color.White, modifier = Modifier.size(24.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
