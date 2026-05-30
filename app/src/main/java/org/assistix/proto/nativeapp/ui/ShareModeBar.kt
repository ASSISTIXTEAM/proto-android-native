package org.assistix.proto.nativeapp.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest

@Composable
fun ShareModeBar(
    preview: String,
    selectedCount: Int,
    onCancel: () -> Unit,
    onSendMulti: () -> Unit,
    imagePreviewUri: Uri? = null,
    imageCount: Int = 0,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        ProtoPanelContainerLite {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCancel, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Close, contentDescription = UiStrings.cancel)
                }
                if (imagePreviewUri != null) {
                    val ctx = LocalContext.current
                    AsyncImage(
                        model = ImageRequest.Builder(ctx).data(imagePreviewUri).crossfade(true).build(),
                        contentDescription = null,
                        modifier = Modifier.size(44.dp).clip(ProtoShapes.media),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        tint = ProtoOrange,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Text(
                    when {
                        imageCount > 1 -> UiStrings.shareImagesFmt(imageCount)
                        selectedCount > 0 -> UiStrings.shareModeTitle
                        else -> preview
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
                )
                if (selectedCount > 0) {
                    TextButton(onClick = onSendMulti) {
                        Text(
                            UiStrings.shareModeSendFmt(selectedCount),
                            fontWeight = FontWeight.SemiBold,
                            color = ProtoOrange,
                        )
                    }
                }
            }
        }
    }
}
