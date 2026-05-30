package org.assistix.proto.nativeapp.ui



import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage

import androidx.compose.foundation.layout.Column

import androidx.compose.foundation.layout.Row

import androidx.compose.foundation.layout.Spacer

import androidx.compose.foundation.layout.fillMaxWidth

import androidx.compose.foundation.layout.height

import androidx.compose.foundation.layout.padding

import androidx.compose.foundation.layout.size

import androidx.compose.foundation.layout.widthIn

import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.filled.Verified

import androidx.compose.material3.ExperimentalMaterial3Api

import androidx.compose.material3.Icon

import androidx.compose.material3.MaterialTheme

import androidx.compose.material3.PlainTooltip

import androidx.compose.material3.Text

import androidx.compose.material3.TextButton

import androidx.compose.material3.TooltipBox

import androidx.compose.material3.TooltipDefaults

import androidx.compose.material3.rememberTooltipState

import androidx.compose.runtime.Composable

import androidx.compose.ui.Alignment

import androidx.compose.ui.Modifier

import androidx.compose.ui.draw.clip

import androidx.compose.ui.graphics.Color

import androidx.compose.ui.text.font.FontWeight

import androidx.compose.ui.unit.dp

import org.assistix.proto.nativeapp.data.ChannelPostMeta

import org.assistix.proto.nativeapp.data.ProtoApi



@OptIn(ExperimentalMaterial3Api::class)

@Composable

fun VerifiedBadge(

    modifier: Modifier = Modifier,

    showTooltip: Boolean = false,

) {

    if (!showTooltip) {

        Icon(

            Icons.Default.Verified,

            contentDescription = UiStrings.channelVerified,

            tint = Color(0xFF22C55E),

            modifier = modifier.size(18.dp),

        )

        return

    }

    TooltipBox(

        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),

        tooltip = { PlainTooltip { Text(UiStrings.channelVerifiedTooltip) } },

        state = rememberTooltipState(),

    ) {

        Icon(

            Icons.Default.Verified,

            contentDescription = UiStrings.channelVerified,

            tint = Color(0xFF22C55E),

            modifier = modifier.size(18.dp),

        )

    }

}



@Composable

fun ChannelTitleRow(

    title: String,

    nick: String,

    verified: Boolean,

    modifier: Modifier = Modifier,

    showVerifiedTooltip: Boolean = false,

) {

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {

        Column(Modifier.weight(1f)) {

            Row(verticalAlignment = Alignment.CenterVertically) {

                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)

                if (verified) {

                    Spacer(Modifier.size(4.dp))

                    VerifiedBadge(showTooltip = showVerifiedTooltip)

                }

            }

            if (nick.isNotBlank()) {

                Text("@$nick", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            }

        }

    }

}



@Composable

fun ChannelSubscribeChip(

    busy: Boolean,

    onSubscribe: () -> Unit,

    modifier: Modifier = Modifier,

) {

    TextButton(

        onClick = onSubscribe,

        enabled = !busy,

        modifier = modifier,

    ) {

        Text(if (busy) "…" else UiStrings.channelSubscribe, fontWeight = FontWeight.SemiBold)

    }

}



@Composable

fun ChannelPostCard(

    post: ChannelPostMeta,

    token: String?,

    api: ProtoApi,

    modifier: Modifier = Modifier,

    onOpenMedia: (() -> Unit)? = null,

) {

    Column(

        modifier

            .fillMaxWidth()

            .clip(RoundedCornerShape(16.dp))

            .background(MaterialTheme.colorScheme.surface)

            .padding(14.dp),

    ) {

        val imgUrl = post.imageUrl?.takeIf { it.isNotBlank() }
        if (imgUrl != null) {
            AsyncImage(
                model = imgUrl,
                contentDescription = null,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .then(if (onOpenMedia != null) Modifier.clickable { onOpenMedia() } else Modifier),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.height(10.dp))
        } else {
            post.imageUploadId?.let { id ->
                if (!token.isNullOrBlank()) {
                    ChatMediaContent(
                        uploadId = id,
                        mime = "image/jpeg",
                        name = "",
                        token = token,
                        api = api,
                        textColor = MaterialTheme.colorScheme.onSurface,
                        onDownload = { _, _ -> },
                        onOpenViewer = onOpenMedia,
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
        }

        if (post.text.isNotBlank()) {

            LinkifiedMessageText(

                text = post.text,

                color = MaterialTheme.colorScheme.onSurface,

                linkColor = MaterialTheme.colorScheme.primary,

                onLinkClick = {},

                highlightMentions = false,

                mentionColor = MaterialTheme.colorScheme.tertiary,

            )

        }

    }

}



@Composable

fun ChannelSubscribeBar(

    description: String,

    subscriberCount: Int,

    busy: Boolean,

    onSubscribe: () -> Unit,

    modifier: Modifier = Modifier,

) {

    Column(

        modifier

            .fillMaxWidth()

            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f), ProtoShapes.field)

            .padding(16.dp),

    ) {

        if (description.isNotBlank()) {

            Text(description, style = MaterialTheme.typography.bodyMedium)

            Spacer(Modifier.height(8.dp))

        }

        if (subscriberCount > 0) {

            Text(

                UiStrings.channelSubscribersFmt(subscriberCount),

                style = MaterialTheme.typography.labelMedium,

                color = MaterialTheme.colorScheme.onSurfaceVariant,

            )

            Spacer(Modifier.height(12.dp))

        }

        ProtoPrimaryButton(if (busy) "…" else UiStrings.channelSubscribe, onSubscribe)

    }

}


