package org.assistix.proto.nativeapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import coil.compose.AsyncImage
import coil.request.ImageRequest
import org.assistix.proto.nativeapp.ProtoApplication
import org.assistix.proto.nativeapp.data.ProtoApi
import org.assistix.proto.nativeapp.data.ProtoAvatarCache

@Composable
fun ProtoAvatar(
    uploadId: String?,
    /** Display name (имя) — used for the initial letter when there is no photo. */
    displayName: String,
    size: Dp,
    api: ProtoApi,
    token: String?,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as? ProtoApplication
    val initial = protoAvatarInitial(displayName)
    var cachedFile by remember(uploadId, token) { mutableStateOf<java.io.File?>(null) }
    LaunchedEffect(uploadId, token) {
        cachedFile = null
        val id = uploadId?.trim()?.takeIf { it.isNotEmpty() } ?: return@LaunchedEffect
        val cache = app?.cache ?: return@LaunchedEffect
        val dest = cache.avatarFile(id)
        if (dest.exists() && dest.length() > 0L) {
            cachedFile = dest
            return@LaunchedEffect
        }
        val t = token?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        cachedFile = ProtoAvatarCache.localFile(cache, api, t, id)
    }
    val cached = cachedFile
    if (cached != null && cached.exists()) {
        AsyncImage(
            model = ImageRequest.Builder(ctx).data(cached).build(),
            contentDescription = null,
            modifier = modifier.size(size).clip(ProtoShapes.avatar),
            contentScale = ContentScale.Crop,
        )
    } else {
        val url = uploadId?.let { api.mediaUrl(it) }
        if (url != null && !token.isNullOrBlank()) {
            AsyncImage(
                model =
                    ImageRequest.Builder(ctx).data(url).apply {
                        api.authHeaders(token).forEach { (k, v) -> addHeader(k, v) }
                    }.build(),
                contentDescription = null,
                modifier = modifier.size(size).clip(ProtoShapes.avatar),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier
                    .size(size)
                    .clip(ProtoShapes.avatar)
                    .background(ProtoOrange),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    initial.toString(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = (size.value * 0.38f).sp,
                )
            }
        }
    }
}

@Composable
fun DisplayNameWithEmoji(
    displayName: String,
    statusEmoji: String,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight = FontWeight.SemiBold,
    fontSize: TextUnit = TextUnit.Unspecified,
    maxLines: Int = 1,
) {
    val name = displayName.ifBlank { "?" }
    val emoji = statusEmoji.trim()
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            name,
            fontWeight = fontWeight,
            fontSize = fontSize,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
        if (emoji.isNotEmpty()) {
            Text(
                emoji,
                fontSize = if (fontSize != TextUnit.Unspecified) fontSize else 18.sp,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}
