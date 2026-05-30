package org.assistix.proto.nativeapp.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import org.assistix.proto.nativeapp.data.LinkPreview
import org.assistix.proto.nativeapp.data.ProtoApi
import org.assistix.proto.nativeapp.data.ProtoLinkPreviewCache
import org.assistix.proto.nativeapp.data.extractUrls
import org.assistix.proto.nativeapp.data.firstUrlIn
import org.assistix.proto.nativeapp.data.normalizeUrl

@Composable
fun OpenLinkConfirmDialog(
    url: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = ProtoShapes.dialog,
        title = { Text(UiStrings.openLinkTitle) },
        text = {
            Column {
                Text(UiStrings.openLinkBody, style = MaterialTheme.typography.bodyMedium)
                Text(
                    url,
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(UiStrings.openLinkOpen, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(UiStrings.cancel) }
        },
    )
}

fun openUrlInBrowser(context: android.content.Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

@Composable
fun LinkifiedMessageText(
    text: String,
    color: Color,
    linkColor: Color,
    onLinkClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    highlightMentions: Boolean = false,
    mentionColor: Color = linkColor,
    onMentionClick: (String) -> Unit = {},
) {
    val annotated =
        remember(text, highlightMentions, onMentionClick) {
            buildAnnotatedString {
                val spans = mutableListOf<Triple<Int, Int, String>>()
                Regex("""(?i)\b((?:https?://|www\.)[^\s<>"'`,)\]]+)""").findAll(text).forEach { m ->
                    spans.add(Triple(m.range.first, m.range.last + 1, "url:${normalizeUrl(m.groupValues[1])}"))
                }
                if (highlightMentions) {
                    Regex("""@([a-zA-Z0-9_]{2,32})""").findAll(text).forEach { m ->
                        spans.add(Triple(m.range.first, m.range.last + 1, "mention"))
                    }
                }
                spans.sortBy { it.first }
                var cursor = 0
                spans.forEach { (start, end, kind) ->
                    if (start < cursor) return@forEach
                    if (start > cursor) append(text.substring(cursor, start))
                    val segStart = length
                    append(text.substring(start, end))
                    val segEnd = length
                    when {
                        kind.startsWith("url:") -> {
                            addStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline), segStart, segEnd)
                            addStringAnnotation("URL", kind.removePrefix("url:"), segStart, segEnd)
                        }
                        kind.startsWith("mention:") -> {
                            addStyle(SpanStyle(color = mentionColor, fontWeight = FontWeight.SemiBold), segStart, segEnd)
                            addStringAnnotation("MENTION", kind.removePrefix("mention:"), segStart, segEnd)
                        }
                    }
                    cursor = end
                }
                if (cursor < text.length) append(text.substring(cursor))
            }
        }
    if (annotated.getStringAnnotations("URL", 0, annotated.length).isEmpty() && !highlightMentions) {
        Text(text, color = color, modifier = modifier)
        return
    }
    ClickableText(
        text = annotated,
        style = TextStyle(color = color),
        modifier = modifier,
        onClick = { offset ->
            annotated
                .getStringAnnotations("MENTION", offset, offset)
                .firstOrNull()
                ?.item
                ?.let(onMentionClick)
                ?: annotated
                    .getStringAnnotations("URL", offset, offset)
                    .firstOrNull()
                    ?.item
                    ?.let(onLinkClick)
        },
    )
}

@Composable
fun MessageLinkPreview(
    body: String,
    token: String?,
    api: ProtoApi,
    textColor: Color,
    onLinkClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onDiscussAssistix: ((LinkPreview) -> Unit)? = null,
) {
    if (!enabled) return
    val url = remember(body) { firstUrlIn(body) }
    var preview by remember(url) { mutableStateOf<LinkPreview?>(url?.let { ProtoLinkPreviewCache.peek(it) }) }
    LaunchedEffect(url, token) {
        if (url == null || token.isNullOrBlank()) return@LaunchedEffect
        preview = ProtoLinkPreviewCache.load(url, token, api)
    }
    val card = preview
    if (url == null || card == null || !card.hasCard) return
    val ctx = LocalContext.current
    LinkPreviewCard(
        preview = card,
        textColor = textColor,
        onClick = { onLinkClick(card.url) },
        onCopyLink = {
            val clip = ctx.getSystemService(ClipboardManager::class.java)
            clip.setPrimaryClip(ClipData.newPlainText("link", card.url))
            Toast.makeText(ctx, UiStrings.copied, Toast.LENGTH_SHORT).show()
        },
        onDiscussAssistix = onDiscussAssistix?.let { cb -> { cb(card) } },
        modifier = modifier.padding(top = 8.dp),
    )
}

@Composable
private fun LinkPreviewCard(
    preview: LinkPreview,
    textColor: Color,
    onClick: () -> Unit,
    onCopyLink: () -> Unit,
    onDiscussAssistix: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    val border = textColor.copy(alpha = 0.2f)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        ProtoOrange.copy(alpha = 0.12f),
                        textColor.copy(alpha = 0.06f),
                    ),
                ),
            ),
    ) {
        Column(Modifier.clickable(onClick = onClick)) {
        if (preview.imageUrl.isNotBlank()) {
            AsyncImage(
                model = preview.imageUrl,
                contentDescription = preview.title,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)),
                contentScale = ContentScale.Crop,
            )
        }
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            if (preview.siteName.isNotBlank()) {
                Text(
                    preview.siteName.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = ProtoOrange,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (preview.title.isNotBlank()) {
                Text(
                    preview.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val summary = preview.aiSummary.ifBlank { preview.description }
            if (summary.isNotBlank()) {
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor.copy(alpha = 0.88f),
                    maxLines = if (preview.aiSummary.isNotBlank()) 2 else 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (preview.aiSummary.isNotBlank() && preview.description.isNotBlank() && preview.aiSummary != preview.description) {
                Text(
                    preview.description,
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor.copy(alpha = 0.55f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            HorizontalDivider(Modifier.padding(top = 6.dp), color = border)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    preview.url,
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor.copy(alpha = 0.65f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    UiStrings.copyLink,
                    color = ProtoOrange,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.clickable { onCopyLink() },
                )
            }
        }
        }
        if (onDiscussAssistix != null) {
            Button(
                onClick = onDiscussAssistix,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ProtoOrange),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text(UiStrings.protoLinkDiscuss, color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
