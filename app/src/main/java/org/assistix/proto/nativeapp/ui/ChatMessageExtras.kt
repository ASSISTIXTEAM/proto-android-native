package org.assistix.proto.nativeapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.assistix.proto.nativeapp.data.AlbumMeta
import org.assistix.proto.nativeapp.data.CallMeta
import org.assistix.proto.nativeapp.data.ConvItem
import org.assistix.proto.nativeapp.data.PollMeta
import org.assistix.proto.nativeapp.data.MsgItem
import org.assistix.proto.nativeapp.data.MsgReaction
import org.assistix.proto.nativeapp.data.ReplyMeta

@Composable
fun ReplyQuoteBlock(
    reply: ReplyMeta,
    textColor: Color,
    onClick: (() -> Unit)? = null,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (onClick != null && reply.messageId > 0) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                },
            )
            .background(textColor.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            UiStrings.replyingTo,
            style = MaterialTheme.typography.labelSmall,
            color = textColor.copy(alpha = 0.75f),
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            reply.preview,
            style = MaterialTheme.typography.bodySmall,
            color = textColor.copy(alpha = 0.9f),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReactionChipsRow(
    reactions: List<MsgReaction>,
    textColor: Color,
    onToggle: (String) -> Unit,
    onMyReactionLongPress: () -> Unit = {},
) {
    if (reactions.isEmpty()) return
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        reactions.forEach { r ->
            val bg = if (r.mine) ProtoOrange.copy(alpha = 0.35f) else textColor.copy(alpha = 0.14f)
            val mod =
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(bg)
                    .then(
                        if (r.mine) {
                            Modifier.combinedClickable(
                                onClick = { onToggle(r.emoji) },
                                onLongClick = onMyReactionLongPress,
                            )
                        } else {
                            Modifier.clickable { onToggle(r.emoji) }
                        },
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            Row(
                mod,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(r.emoji, style = MaterialTheme.typography.labelLarge)
                if (r.count > 1) {
                    Text(
                        r.count.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor.copy(alpha = 0.85f),
                    )
                }
            }
        }
    }
}

@Composable
fun ComposerReplyBar(reply: ReplyMeta, onDismiss: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(UiStrings.replyingTo, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text(reply.preview, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
        }
        Text(
            UiStrings.cancel,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable { onDismiss() }.padding(4.dp),
            style = MaterialTheme.typography.labelLarge,
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
}

fun formatCallPreview(meta: CallMeta): String {
    val icon = if (meta.video) "📹" else "📞"
    val status =
        when (meta.status) {
            "answered" -> UiStrings.callDurationLabel(meta.durationSec)
            "missed" -> UiStrings.callMissed
            "cancelled" -> UiStrings.callCancelled
            "declined" -> UiStrings.callDeclined
            else -> meta.status
        }
    return "$icon $status"
}

fun formatCallListPreview(raw: String, lastSenderId: Int, myUserId: Int): String? =
    CallMeta.fromJson(raw.trim())?.let { formatCallPreview(it) }

fun formatPollListPreview(raw: String): String? =
    PollMeta.fromJson(raw.trim())?.let { poll ->
        val q = poll.question.trim()
        if (q.isNotBlank()) "📊 $q" else "📊 ${UiStrings.createPoll}"
    }

/** Turns stored last_message body / preview into human text for chat list & widgets. */
fun formatStoredMessagePreview(raw: String, lastSenderId: Int = 0, myUserId: Int = 0): String {
    val trim = raw.trim()
    if (trim.isEmpty()) return trim
    org.assistix.proto.nativeapp.data.ChannelCardMeta.fromJson(trim)?.let { card ->
        return "📢 @${card.nick}"
    }
    org.assistix.proto.nativeapp.data.ChannelPostMeta.fromJson(trim)?.let { post ->
        return post.text.ifBlank { "📢" }
    }
    formatCallListPreview(trim, lastSenderId, myUserId)?.let { return it }
    formatPollListPreview(trim)?.let { return it }
    AlbumMeta.fromJson(trim)?.let { a ->
        val cap = AlbumMeta.captionFromJson(trim)
        return if (cap.isNotBlank()) cap else "📷 ${a.items.size}"
    }
    return trim
}

fun previewForList(c: ConvItem, myUserId: Int): String {
    val raw = c.preview.trim()
    if (raw.isEmpty()) {
        return if (c.kind == "group" && c.groupMemberCount > 0) {
            UiStrings.groupMembersCount(c.groupMemberCount)
        } else {
            UiStrings.noMessages
        }
    }
    val formatted = formatStoredMessagePreview(raw, c.lastSenderId, myUserId)
    if (c.kind == "group" && c.lastSenderId > 0 && c.lastSenderId != myUserId) {
        return "• $formatted"
    }
    return formatted
}
