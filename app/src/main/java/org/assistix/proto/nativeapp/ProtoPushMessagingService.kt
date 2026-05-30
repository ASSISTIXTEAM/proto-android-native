package org.assistix.proto.nativeapp

import android.content.Intent
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.assistix.proto.nativeapp.data.ProtoActiveChat
import org.assistix.proto.nativeapp.ui.UiStrings
import kotlinx.coroutines.flow.first
import org.assistix.proto.nativeapp.widget.WidgetRefreshScheduler

class ProtoPushMessagingService : FirebaseMessagingService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val app = application as? ProtoApplication ?: return
        scope.launch {
            val session = runCatching { app.session.token() }.getOrNull() ?: return@launch
            runCatching { app.api.registerPush(session, token) }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val app = application as? ProtoApplication ?: return
        val data = message.data
        val type = data["type"]?.ifBlank { "message" } ?: "message"
        if (type == "login_alert") {
            scope.launch {
                if (!app.prefs.messageNotifications.first()) return@launch
                val title = data["title"]?.takeIf { it.isNotBlank() } ?: UiStrings.loginAlertTitle
                val body = data["preview"]?.takeIf { it.isNotBlank() } ?: ""
                app.notifier.notifyMessage(
                    senderTitle = title,
                    body = body.ifBlank { UiStrings.loginAlertTitle },
                    conversationId = 0,
                )
            }
            WidgetRefreshScheduler.enqueueNow(app)
            return
        }
        val cid = data["conversation_id"]?.toIntOrNull() ?: 0
        val messageId = data["message_id"]?.toLongOrNull() ?: 0L
        if (type == "call" && cid > 0) {
            scope.launch {
                if (!app.prefs.callNotifications.first()) return@launch
                val title = data["title"]?.takeIf { it.isNotBlank() } ?: UiStrings.incomingCall
                val withVideo =
                    data["video"] == "1" ||
                        data["with_video"] == "1" ||
                        data["call_type"]?.equals("video", ignoreCase = true) == true
                ProtoCallService.start(app, title, playRingtone = true)
                app.notifier.notifyIncomingCall(title, cid, withVideo = withVideo)
                app.calls.prioritizeIncomingPoll(cid)
                val open =
                    Intent(app, IncomingCallActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        putExtra(IncomingCallActivity.EXTRA_CID, cid)
                    }
                runCatching { app.startActivity(open) }
            }
            WidgetRefreshScheduler.enqueueNow(app)
            return
        }
        if (cid > 0 && cid == ProtoActiveChat.conversationId) {
            WidgetRefreshScheduler.enqueueNow(app)
            return
        }
        scope.launch {
            if (messageId > 0L) {
                val lastRead = app.conversations.myLastReadMessageId(cid)
                if (messageId <= lastRead) return@launch
            }
            val muted = app.chatLocalPrefs.mutedIds.first()
            if (cid > 0 && cid in muted) return@launch
            if (!app.prefs.messageNotifications.first()) return@launch
            if (app.calls.state.value.active) return@launch
            val title = data["title"]?.takeIf { it.isNotBlank() }
                ?: message.notification?.title
                ?: UiStrings.newMessage
            val body = data["preview"]?.takeIf { it.isNotBlank() }
                ?: message.notification?.body
                ?: ""
            if (cid > 0) {
                app.notifier.notifyMessage(
                    senderTitle = title,
                    body = body.ifBlank { UiStrings.newMessage },
                    conversationId = cid,
                )
            }
        }
        WidgetRefreshScheduler.enqueueNow(app)
        Log.d(TAG, "FCM data=$data")
    }

    companion object {
        private const val TAG = "ProtoFCM"
    }
}
