package org.assistix.proto.nativeapp.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
class ProtoUnreadWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = WidgetRepository.load(context)
        provideContent { UnreadOrbContent(snapshot) }
    }
}

class ProtoChatsCompactWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = WidgetRepository.load(context)
        provideContent { ChatsCompactContent(snapshot) }
    }
}

class ProtoChatsListWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = WidgetRepository.load(context)
        provideContent { ChatsListContent(snapshot, maxRows = 5) }
    }
}

class ProtoChatsWideWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = WidgetRepository.load(context)
        provideContent { ChatsWideContent(snapshot) }
    }
}

/** 4×2 — AI-сводка непрочитанных без входа в приложение */
class ProtoAiBriefWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = WidgetRepository.load(context)
        provideContent { AiBriefContent(snapshot) }
    }
}

/** 3×2 — быстрый чат: 3 частых контакта */
class ProtoQuickChatWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = WidgetRepository.load(context)
        provideContent { QuickChatContent(snapshot) }
    }
}

class ProtoUnreadWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ProtoUnreadWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetRefreshScheduler.enqueueNow(context)
    }
}

class ProtoChatsCompactWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ProtoChatsCompactWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetRefreshScheduler.enqueueNow(context)
    }
}

class ProtoChatsListWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ProtoChatsListWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetRefreshScheduler.enqueueNow(context)
    }
}

class ProtoChatsWideWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ProtoChatsWideWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetRefreshScheduler.enqueueNow(context)
    }
}

class ProtoAiBriefWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ProtoAiBriefWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetRefreshScheduler.enqueueNow(context)
    }
}

class ProtoQuickChatWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ProtoQuickChatWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetRefreshScheduler.enqueueNow(context)
    }
}
