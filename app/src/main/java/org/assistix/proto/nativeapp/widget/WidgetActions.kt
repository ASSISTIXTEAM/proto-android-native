package org.assistix.proto.nativeapp.widget

import android.content.Context
import android.content.Intent
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import org.assistix.proto.nativeapp.MainActivity

object WidgetActionKeys {
    val conversationId = ActionParameters.Key<Int>("conversation_id")
    val conversationTitle = ActionParameters.Key<String>("conversation_title")
    val conversationKind = ActionParameters.Key<String>("conversation_kind")
    val peerUserId = ActionParameters.Key<Int>("peer_user_id")
}

class WidgetRefreshAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        WidgetRepository.refresh(context.applicationContext)
    }
}

class WidgetOpenAppAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val intent =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        context.startActivity(intent)
    }
}

class WidgetOpenChatAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val cid = parameters[WidgetActionKeys.conversationId] ?: return
        val title = parameters[WidgetActionKeys.conversationTitle] ?: ""
        val kind = parameters[WidgetActionKeys.conversationKind] ?: "dm"
        val peer = parameters[WidgetActionKeys.peerUserId] ?: 0
        val intent =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("open_conversation_id", cid)
                putExtra("open_conversation_title", title)
                putExtra("open_conversation_kind", kind)
                putExtra("open_conversation_peer_id", peer)
            }
        context.startActivity(intent)
    }
}
