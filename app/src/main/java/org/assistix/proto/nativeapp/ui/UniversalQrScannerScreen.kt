package org.assistix.proto.nativeapp.ui

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.assistix.proto.nativeapp.data.ProtoApi
import org.assistix.proto.nativeapp.data.ProtoQrPayload
import org.assistix.proto.nativeapp.data.ProtoQrResolver
import org.assistix.proto.nativeapp.data.ProtoSessionStore
import org.assistix.proto.nativeapp.data.resolveDisplayName
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Composable
fun UniversalQrScannerScreen(
    session: ProtoSessionStore,
    api: ProtoApi,
    onBack: () -> Unit,
    onDeviceLinked: () -> Unit,
    onOpenChat: (conversationId: Int, title: String, kind: String, peerUserId: Int) -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    LinkQrScannerScreen(
        session = session,
        api = api,
        onBack = onBack,
        onLinked = onDeviceLinked,
        onRawQr = { raw ->
            when (val payload = ProtoQrResolver.parse(raw)) {
                is ProtoQrPayload.DeviceLink -> {
                    scope.launch {
                        val t = session.token() ?: return@launch
                        val (ok, _) = withContext(Dispatchers.IO) { api.approveDeviceLink(t, payload.pairId, payload.secret) }
                        if (ok) {
                            Toast.makeText(ctx, UiStrings.linkWebApproved, Toast.LENGTH_SHORT).show()
                            onDeviceLinked()
                        } else {
                            Toast.makeText(ctx, UiStrings.linkQrInvalid, Toast.LENGTH_SHORT).show()
                        }
                    }
                    true
                }
                is ProtoQrPayload.PublicLink -> {
                    scope.launch {
                        val t = session.token() ?: return@launch
                        val joined = withContext(Dispatchers.IO) { api.joinPublicLink(t, payload.code) }
                        if (joined == null || joined.conversationId <= 0) {
                            Toast.makeText(ctx, UiStrings.linkQrInvalid, Toast.LENGTH_SHORT).show()
                            return@launch
                        }
                        val title =
                            when (joined.kind) {
                                "dm" -> UiStrings.chatDefault
                                else -> joined.kind
                            }
                        onOpenChat(joined.conversationId, title, joined.kind, joined.peerUserId)
                    }
                    true
                }
                is ProtoQrPayload.ProfileNick -> {
                    scope.launch {
                        val t = session.token() ?: return@launch
                        val hits = withContext(Dispatchers.IO) { api.searchUsers(t, payload.nick) }
                        val user = hits.firstOrNull { it.nick.equals(payload.nick, ignoreCase = true) } ?: hits.firstOrNull()
                        if (user == null) {
                            Toast.makeText(ctx, UiStrings.genericError, Toast.LENGTH_SHORT).show()
                            return@launch
                        }
                        val cid = withContext(Dispatchers.IO) { api.startDm(t, user.id) } ?: return@launch
                        val name = resolveDisplayName(user.displayName, user.nick)
                        onOpenChat(cid, name, "dm", user.id)
                    }
                    true
                }
                null -> false
            }
        },
    )
}
