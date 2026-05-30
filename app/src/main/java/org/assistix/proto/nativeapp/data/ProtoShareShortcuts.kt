package org.assistix.proto.nativeapp.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assistix.proto.nativeapp.MainActivity
import org.assistix.proto.nativeapp.ProtoApplication

/** Direct-share targets (контакты с аватарками) в системном меню «Поделиться». */
object ProtoShareShortcuts {
    private const val MAX_TARGETS = 4

    suspend fun sync(
        context: Context,
        chats: List<ConvItem>,
        api: ProtoApi,
        token: String?,
    ) {
        if (token.isNullOrBlank()) {
            ShortcutManagerCompat.removeAllDynamicShortcuts(context)
            return
        }
        val app = context.applicationContext as? ProtoApplication ?: return
        val targets =
            chats
                .filter { it.kind != "saved" && it.id > 0 }
                .sortedByDescending { it.updatedAt }
                .take(MAX_TARGETS)
        val shortcuts =
            withContext(Dispatchers.IO) {
                targets.mapNotNull { conv -> buildShortcut(context, app, api, token, conv) }
            }
        ShortcutManagerCompat.removeAllDynamicShortcuts(context)
        shortcuts.forEach { sc ->
            ShortcutManagerCompat.pushDynamicShortcut(context, sc)
        }
    }

    private suspend fun buildShortcut(
        context: Context,
        app: ProtoApplication,
        api: ProtoApi,
        token: String,
        conv: ConvItem,
    ): ShortcutInfoCompat? {
        val avatarId =
            when (conv.kind) {
                "channel" -> conv.channelAvatarUploadId
                else -> conv.peerAvatarUploadId
            }
        val label = conv.title.ifBlank { conv.peerDisplayName }.ifBlank { "PROTO" }
        val icon =
            withContext(Dispatchers.IO) {
                val file = ProtoAvatarCache.localFile(app.cache, api, token, avatarId)
                if (file != null && file.exists()) {
                    val bmp = BitmapFactory.decodeFile(file.absolutePath)
                    if (bmp != null) {
                        return@withContext IconCompat.createWithAdaptiveBitmap(bmp)
                    }
                }
                IconCompat.createWithResource(context, org.assistix.proto.nativeapp.R.mipmap.ic_launcher)
            }
        val intent =
            Intent(context, MainActivity::class.java).apply {
                action = MainActivity.ACTION_SHARE_TO_CHAT
                putExtra(MainActivity.EXTRA_SHARE_CONVERSATION_ID, conv.id)
                type = "text/plain"
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        return ShortcutInfoCompat.Builder(context, "proto_share_${conv.id}")
            .setShortLabel(label.take(24))
            .setLongLabel(label.take(48))
            .setIcon(icon)
            .setIntent(intent)
            .setCategories(setOf("android.shortcut.conversation"))
            .setRank(conv.id)
            .build()
    }

}
