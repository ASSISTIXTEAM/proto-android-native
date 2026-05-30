package org.assistix.proto.nativeapp.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll

object WidgetUpdateCoordinator {
    suspend fun updateAll(context: Context) {
        val app = context.applicationContext
        ProtoUnreadWidget().updateAll(app)
        ProtoChatsCompactWidget().updateAll(app)
        ProtoChatsListWidget().updateAll(app)
        ProtoChatsWideWidget().updateAll(app)
        ProtoAiBriefWidget().updateAll(app)
        ProtoQuickChatWidget().updateAll(app)
    }

    suspend fun hasAnyWidgets(context: Context): Boolean {
        val mgr = GlanceAppWidgetManager(context)
        return ProtoUnreadWidget::class.java.let { mgr.getGlanceIds(it).isNotEmpty() } ||
            ProtoChatsCompactWidget::class.java.let { mgr.getGlanceIds(it).isNotEmpty() } ||
            ProtoChatsListWidget::class.java.let { mgr.getGlanceIds(it).isNotEmpty() } ||
            ProtoChatsWideWidget::class.java.let { mgr.getGlanceIds(it).isNotEmpty() } ||
            ProtoAiBriefWidget::class.java.let { mgr.getGlanceIds(it).isNotEmpty() } ||
            ProtoQuickChatWidget::class.java.let { mgr.getGlanceIds(it).isNotEmpty() }
    }
}
