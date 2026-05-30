package org.assistix.proto.nativeapp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import org.assistix.proto.nativeapp.data.AlbumItem
import org.assistix.proto.nativeapp.data.AlbumMeta
import org.assistix.proto.nativeapp.data.ProtoApi

@Composable
fun ChatAlbumGrid(
    album: AlbumMeta,
    token: String,
    api: ProtoApi,
    textColor: Color,
    messageId: Long,
    onMediaOpen: (messageId: Long, uploadId: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = album.items.take(4)
    if (items.isEmpty()) return
    val cols = if (items.size == 1) 1 else 2
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.chunked(cols).forEach { rowItems ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                rowItems.forEach { item ->
                    AlbumThumb(
                        item = item,
                        token = token,
                        api = api,
                        onTap = { onMediaOpen(messageId, item.uploadId) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (rowItems.size == 1 && cols > 1) {
                    Box(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun AlbumThumb(
    item: AlbumItem,
    token: String,
    api: ProtoApi,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val request =
        remember(item.uploadId, token) {
            ImageRequest.Builder(ctx)
                .data(api.mediaUrl(item.uploadId))
                .apply { api.authHeaders(token).forEach { (k, v) -> addHeader(k, v) } }
                .crossfade(true)
                .build()
        }
    AsyncImage(
        model = request,
        contentDescription = null,
        modifier =
            modifier
                .aspectRatio(1f)
                .heightIn(max = 160.dp)
                .clip(ProtoShapes.media)
                .clickable(onClick = onTap),
        contentScale = ContentScale.Crop,
    )
}
