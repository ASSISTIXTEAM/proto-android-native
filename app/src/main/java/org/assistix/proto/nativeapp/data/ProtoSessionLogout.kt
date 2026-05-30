package org.assistix.proto.nativeapp.data

import android.content.Context
import androidx.core.content.pm.ShortcutManagerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Revokes the server session and clears all local auth-related state. */
suspend fun performProtoLogout(
    token: String?,
    api: ProtoApi,
    realtime: ProtoRealtimeHub,
    calls: ProtoCallGateway,
    messages: ProtoMessageRepository,
    notifier: ProtoNotifier,
    session: ProtoSessionStore,
    pendingVerification: ProtoPendingVerificationStore? = null,
    conversations: ProtoConversationRepository? = null,
    eventCursor: ProtoEventCursorStore? = null,
    appContext: Context? = null,
) {
    val userId = withContext(Dispatchers.IO) { session.userId() }
    withContext(Dispatchers.IO) {
        if (!token.isNullOrBlank()) {
            runCatching { api.logout(token) }
        }
        messages.clearAll()
        conversations?.clearLocal()
        if (userId > 0) {
            eventCursor?.clear(userId)
        }
    }
    realtime.stop()
    calls.clearSession()
    ProtoForwardState.clear()
    ProtoMediaViewerState.close()
    ProtoShareState.clear()
    ProtoChatSelectionState.active = false
    ProtoActiveChat.conversationId = 0
    ProtoAppNavigation.clearPending()
    notifier.cancelAllNotifications()
    appContext?.let { ctx ->
        runCatching { ShortcutManagerCompat.removeAllDynamicShortcuts(ctx) }
        runCatching { ProtoUnreadBadge.apply(ctx, 0) }
    }
    session.clear()
    pendingVerification?.let { runCatching { it.clear() } }
}
