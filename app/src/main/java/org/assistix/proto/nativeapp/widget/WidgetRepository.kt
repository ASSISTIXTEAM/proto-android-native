package org.assistix.proto.nativeapp.widget

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.assistix.proto.nativeapp.data.ProtoApi
import org.assistix.proto.nativeapp.data.ProtoDataStoreFactory
import org.assistix.proto.nativeapp.data.ProtoSessionStore

object WidgetRepository {
    private fun widgetStore(context: Context) = ProtoDataStoreFactory.preferences(context, "proto_widget_cache")
    private val cacheKey = stringPreferencesKey("snapshot_json")

    suspend fun load(context: Context): WidgetSnapshot =
        WidgetSnapshotCodec.decode(
            widgetStore(context).data.map { it[cacheKey] }.first(),
        )

    suspend fun refresh(context: Context): WidgetSnapshot =
        withContext(Dispatchers.IO) {
            val session = ProtoSessionStore(context.applicationContext)
            val token = session.token()
            val snapshot =
                if (token.isNullOrBlank()) {
                    WidgetSnapshot.empty()
                } else {
                    val api = ProtoApi()
                    val chats = runCatching { api.conversations(token) }.getOrElse { emptyList() }
                    val nick = runCatching { session.nick() }.getOrNull()
                    val base = WidgetSnapshot.fromConversations(nick, chats)
                    fetchAiBriefIfNeeded(token, api, base)
                }
            widgetStore(context).edit { it[cacheKey] = WidgetSnapshotCodec.encode(snapshot) }
            WidgetUpdateCoordinator.updateAll(context)
            snapshot
        }

    suspend fun clear(context: Context) {
        widgetStore(context).edit { it.remove(cacheKey) }
        WidgetUpdateCoordinator.updateAll(context)
    }

    private suspend fun fetchAiBriefIfNeeded(token: String, api: ProtoApi, snap: WidgetSnapshot): WidgetSnapshot {
        if (!snap.loggedIn || snap.totalUnread <= 0) return snap
        val lines =
            snap.chats
                .filter { it.unreadCount > 0 }
                .take(6)
                .map { c -> "${c.displayTitle()}: ${c.previewShort(90)}" }
        if (lines.isEmpty()) return snap
        val lang = java.util.Locale.getDefault().language.ifBlank { "ru" }
        val reply =
            runCatching {
                api.assistixRequest(
                    token = token,
                    action = "widget_brief",
                    previewLines = lines,
                    language = lang,
                )
            }.getOrNull()
        val text = reply?.text?.trim().orEmpty()
        return if (reply?.ok == true && text.isNotBlank()) {
            WidgetSnapshot.withAiBrief(snap, text)
        } else {
            snap
        }
    }
}
