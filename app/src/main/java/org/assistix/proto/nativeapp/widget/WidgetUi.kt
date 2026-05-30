package org.assistix.proto.nativeapp.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import org.assistix.proto.nativeapp.R
import org.assistix.proto.nativeapp.ui.UiStrings

private val cText = ColorProvider(R.color.widget_text_primary)
private val cMuted = ColorProvider(R.color.widget_text_muted)
private val cDim = ColorProvider(R.color.widget_text_dim)
private val cAccent = ColorProvider(R.color.widget_accent)
private val cOnAccent = ColorProvider(R.color.widget_on_accent)

private val styleBrand =
    TextStyle(color = cAccent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
private val styleTagline =
    TextStyle(color = cMuted, fontSize = 10.sp)
private val styleTitle =
    TextStyle(color = cText, fontSize = 13.sp, fontWeight = FontWeight.Medium)
private val stylePreview =
    TextStyle(color = cMuted, fontSize = 11.sp)
private val styleTime =
    TextStyle(color = cDim, fontSize = 10.sp)
private val styleStatBig =
    TextStyle(color = cAccent, fontSize = 22.sp, fontWeight = FontWeight.Bold)
private val styleStatLabel =
    TextStyle(color = cMuted, fontSize = 10.sp)

@Composable
private fun WidgetShell(content: @Composable () -> Unit) {
    Box(
        modifier =
            GlanceModifier
                .fillMaxSize()
                .cornerRadius(24.dp)
                .background(ImageProvider(R.drawable.widget_bg_dark))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        contentAlignment = Alignment.TopStart,
    ) {
        content()
    }
}

@Composable
private fun WidgetLogo(size: Int = 36) {
    Box(
        modifier =
            GlanceModifier
                .size(size.dp)
                .cornerRadius((size * 0.38f).dp)
                .background(ImageProvider(R.drawable.widget_logo_plate))
                .padding(5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(R.mipmap.ic_launcher),
            contentDescription = "PROTO",
            modifier = GlanceModifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
private fun WidgetBrandHeader(
    snapshot: WidgetSnapshot,
    showRefresh: Boolean = true,
    compact: Boolean = false,
) {
    Box(modifier = GlanceModifier.fillMaxWidth()) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WidgetLogo(if (compact) 32 else 38)
            Spacer(GlanceModifier.width(10.dp))
            Column {
                Text("PROTO", style = styleBrand)
                if (!compact) {
                    Text(
                        if (snapshot.userNick.isNotBlank()) "@${snapshot.userNick}" else UiStrings.widgetMessengerTagline,
                        style = styleTagline,
                        maxLines = 1,
                    )
                }
            }
        }
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            horizontalAlignment = Alignment.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (snapshot.totalUnread > 0) {
                Box(
                    modifier =
                        GlanceModifier
                            .cornerRadius(20.dp)
                            .background(ImageProvider(R.drawable.widget_cta_primary))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        unreadLabel(snapshot.totalUnread),
                        style = TextStyle(color = cOnAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold),
                    )
                }
                Spacer(GlanceModifier.width(6.dp))
            }
            if (showRefresh) {
                Box(
                    modifier =
                        GlanceModifier
                            .size(34.dp)
                            .cornerRadius(17.dp)
                            .background(ImageProvider(R.drawable.widget_surface_card))
                            .clickable(onClick = actionRunCallback<WidgetRefreshAction>()),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.widget_ic_refresh),
                        contentDescription = "Refresh",
                        modifier = GlanceModifier.size(18.dp),
                    )
                }
            }
        }
    }
    Spacer(GlanceModifier.height(10.dp))
}

@Composable
private fun WidgetLoggedOut() {
    Column(
        modifier =
            GlanceModifier
                .fillMaxSize()
                .clickable(onClick = actionRunCallback<WidgetOpenAppAction>()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        WidgetLogo(52)
        Spacer(GlanceModifier.height(10.dp))
        Text("PROTO", style = styleBrand)
        Spacer(GlanceModifier.height(4.dp))
        Text(UiStrings.widgetLoggedOutHint, style = stylePreview)
        Spacer(GlanceModifier.height(10.dp))
        WidgetPrimaryCta("Открыть приложение")
    }
}

@Composable
private fun WidgetPrimaryCta(label: String) {
    Box(
        modifier =
            GlanceModifier
                .fillMaxWidth()
                .cornerRadius(14.dp)
                .background(ImageProvider(R.drawable.widget_cta_primary))
                .clickable(onClick = actionRunCallback<WidgetOpenAppAction>())
                .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = TextStyle(color = cOnAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold),
        )
    }
}

@Composable
private fun WidgetChatRow(
    chat: WidgetChatEntry,
    showPreview: Boolean,
    compact: Boolean = false,
) {
    val hasUnread = chat.unreadCount > 0
    Row(
        modifier =
            GlanceModifier
                .fillMaxWidth()
                .cornerRadius(16.dp)
                .background(
                    ImageProvider(
                        if (hasUnread) R.drawable.widget_surface_card_unread else R.drawable.widget_surface_card,
                    ),
                )
                .padding(horizontal = 10.dp, vertical = if (compact) 7.dp else 9.dp)
                .clickable(onClick = openChatAction(chat)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                GlanceModifier
                    .size(if (compact) 36.dp else 40.dp)
                    .cornerRadius(20.dp)
                    .background(if (hasUnread) cAccent else ColorProvider(R.color.widget_surface_hi)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                chat.displayTitle().firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                style =
                    TextStyle(
                        color = if (hasUnread) cOnAccent else cText,
                        fontSize = if (compact) 14.sp else 15.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    ),
            )
        }
        Spacer(GlanceModifier.width(10.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(chat.displayTitle(), style = styleTitle, maxLines = 1)
            if (showPreview && chat.preview.isNotBlank()) {
                Spacer(GlanceModifier.height(2.dp))
                Text(chat.previewShort(if (compact) 48 else 56), style = stylePreview, maxLines = 2)
            }
        }
        val time = chat.timeAgoLabel()
        if (time.isNotBlank()) {
            Spacer(GlanceModifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                if (hasUnread) {
                    Box(
                        modifier =
                            GlanceModifier
                                .size(20.dp)
                                .cornerRadius(10.dp)
                                .background(cAccent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            unreadLabel(chat.unreadCount),
                            style = TextStyle(color = cOnAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold),
                        )
                    }
                }
                Spacer(GlanceModifier.height(2.dp))
                Text(time, style = styleTime)
            }
        } else if (hasUnread) {
            Spacer(GlanceModifier.width(6.dp))
            Box(
                modifier =
                    GlanceModifier
                        .size(22.dp)
                        .cornerRadius(11.dp)
                        .background(cAccent),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    unreadLabel(chat.unreadCount),
                    style = TextStyle(color = cOnAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold),
                )
            }
        }
    }
}

@Composable
private fun WidgetStatTile(value: String, label: String, modifier: GlanceModifier = GlanceModifier) {
    Column(
        modifier =
            modifier
                .cornerRadius(14.dp)
                .background(ImageProvider(R.drawable.widget_stat_card))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = styleStatBig)
        Text(label, style = styleStatLabel, maxLines = 1)
    }
}

@Composable
private fun WidgetEmptyChats() {
    Column(
        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(UiStrings.widgetNoActiveChats, style = stylePreview)
        Spacer(GlanceModifier.height(8.dp))
        WidgetPrimaryCta("Открыть PROTO")
    }
}

/** 1×1 — логотип + бейдж непрочитанных */
@Composable
fun UnreadOrbContent(snapshot: WidgetSnapshot) {
    val openApp = actionRunCallback<WidgetOpenAppAction>()
    Box(
        modifier =
            GlanceModifier
                .fillMaxSize()
                .cornerRadius(24.dp)
                .background(ImageProvider(R.drawable.widget_bg_dark))
                .clickable(onClick = openApp)
                .padding(10.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (!snapshot.loggedIn) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                WidgetLogo(48)
                Spacer(GlanceModifier.height(6.dp))
                Text("PROTO", style = styleBrand)
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = GlanceModifier.size(60.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    WidgetLogo(52)
                }
                val count = snapshot.totalUnread
                if (count > 0) {
                    Spacer(GlanceModifier.height(4.dp))
                    Box(
                        modifier =
                            GlanceModifier
                                .cornerRadius(14.dp)
                                .background(ImageProvider(R.drawable.widget_cta_primary))
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "${unreadLabel(count)} новых",
                            style = TextStyle(color = cOnAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold),
                        )
                    }
                }
                Spacer(GlanceModifier.height(8.dp))
                Text("PROTO", style = TextStyle(color = cText, fontSize = 11.sp, fontWeight = FontWeight.Bold))
                Text(
                    if (snapshot.totalUnread > 0) "${snapshot.totalUnread} новых" else "всё прочитано",
                    style = styleTime,
                )
            }
        }
    }
}

/** 2×2 — быстрые чаты */
@Composable
fun ChatsCompactContent(snapshot: WidgetSnapshot) {
    WidgetShell {
        if (!snapshot.loggedIn) {
            WidgetLoggedOut()
        } else {
            WidgetBrandHeader(snapshot, compact = true)
            val chats = snapshot.chats.take(2)
            if (chats.isEmpty()) {
                WidgetEmptyChats()
            } else {
                chats.forEachIndexed { i, chat ->
                    WidgetChatRow(chat, showPreview = true, compact = true)
                    if (i < chats.lastIndex) Spacer(GlanceModifier.height(6.dp))
                }
                Spacer(GlanceModifier.height(8.dp))
                WidgetPrimaryCta("Открыть PROTO")
            }
        }
    }
}

/** 4×2 — входящие */
@Composable
fun ChatsListContent(snapshot: WidgetSnapshot, maxRows: Int) {
    WidgetShell {
        if (!snapshot.loggedIn) {
            WidgetLoggedOut()
        } else {
            WidgetBrandHeader(snapshot)
            val chats = snapshot.chats.take(maxRows)
            if (chats.isEmpty()) {
                WidgetEmptyChats()
            } else {
                chats.forEachIndexed { i, chat ->
                    WidgetChatRow(chat, showPreview = true)
                    if (i < chats.lastIndex) Spacer(GlanceModifier.height(6.dp))
                }
            }
        }
    }
}

/** 4×4 — панель со статистикой */
@Composable
fun ChatsWideContent(snapshot: WidgetSnapshot) {
    WidgetShell {
        if (!snapshot.loggedIn) {
            WidgetLoggedOut()
        } else {
            WidgetBrandHeader(snapshot)
            val unreadChats = snapshot.chats.count { it.unreadCount > 0 }
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                WidgetStatTile(
                    value = unreadLabel(snapshot.totalUnread),
                    label = "непрочит.",
                )
                Spacer(GlanceModifier.width(10.dp))
                WidgetStatTile(
                    value = unreadChats.toString(),
                    label = "с новыми",
                )
            }
            Spacer(GlanceModifier.height(10.dp))
            if (snapshot.totalUnread == 0) {
                Text(UiStrings.widgetAllRead, style = TextStyle(color = cMuted, fontSize = 12.sp))
                Spacer(GlanceModifier.height(8.dp))
            }
            val chats = snapshot.chats.take(5)
            if (chats.isEmpty()) {
                WidgetEmptyChats()
            } else {
                chats.forEachIndexed { i, chat ->
                    WidgetChatRow(chat, showPreview = true, compact = true)
                    if (i < chats.lastIndex) Spacer(GlanceModifier.height(5.dp))
                }
                Spacer(GlanceModifier.height(8.dp))
                WidgetPrimaryCta("Открыть PROTO")
            }
        }
    }
}

private fun unreadLabel(count: Int): String = if (count > 99) "99+" else count.toString()

/** 4×2 — AI-сводка непрочитанного */
@Composable
fun AiBriefContent(snapshot: WidgetSnapshot) {
    WidgetShell {
        if (!snapshot.loggedIn) {
            WidgetLoggedOut()
        } else {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WidgetLogo(36)
                Spacer(GlanceModifier.width(8.dp))
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text("PROTO AI", style = styleBrand)
                    Text(UiStrings.widgetAiBriefTitle, style = styleTagline, maxLines = 1)
                }
                if (snapshot.totalUnread > 0) {
                    Box(
                        modifier =
                            GlanceModifier
                                .cornerRadius(12.dp)
                                .background(ImageProvider(R.drawable.widget_cta_primary))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(
                            unreadLabel(snapshot.totalUnread),
                            style = TextStyle(color = cOnAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold),
                        )
                    }
                }
            }
            Spacer(GlanceModifier.height(10.dp))
            val brief = snapshot.aiBrief.trim()
            if (brief.isNotBlank()) {
                Box(
                    modifier =
                        GlanceModifier
                            .fillMaxWidth()
                            .cornerRadius(16.dp)
                            .background(ImageProvider(R.drawable.widget_surface_card_unread))
                            .padding(12.dp),
                ) {
                    Text(brief, style = stylePreview, maxLines = 6)
                }
            } else if (snapshot.totalUnread > 0) {
                Text(UiStrings.widgetAiBriefLoading, style = stylePreview, maxLines = 3)
            } else {
                Text(UiStrings.widgetAllRead, style = stylePreview, maxLines = 2)
            }
            Spacer(GlanceModifier.height(10.dp))
            WidgetPrimaryCta(UiStrings.widgetOpenApp)
        }
    }
}

/** 3×2 — три частых DM */
@Composable
fun QuickChatContent(snapshot: WidgetSnapshot) {
    WidgetShell {
        if (!snapshot.loggedIn) {
            WidgetLoggedOut()
        } else {
            WidgetBrandHeader(snapshot, compact = true)
            val dms =
                snapshot.chats
                    .filter { it.kind == "dm" && it.peerUserId > 0 }
                    .take(3)
            if (dms.isEmpty()) {
                WidgetEmptyChats()
            } else {
                dms.forEachIndexed { i, chat ->
                    WidgetChatRow(chat, showPreview = false, compact = true)
                    if (i < dms.lastIndex) Spacer(GlanceModifier.height(8.dp))
                }
            }
        }
    }
}

private fun openChatAction(chat: WidgetChatEntry) =
    actionRunCallback<WidgetOpenChatAction>(
        actionParametersOf(
            WidgetActionKeys.conversationId to chat.id,
            WidgetActionKeys.conversationTitle to chat.displayTitle(),
            WidgetActionKeys.conversationKind to chat.kind,
            WidgetActionKeys.peerUserId to chat.peerUserId,
        ),
    )
