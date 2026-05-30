package org.assistix.proto.nativeapp.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Sync chat folders, drafts, and notification prefs with `/api/prefs.php`. */
object ProtoClientPrefsSync {
    suspend fun pull(
        token: String,
        api: ProtoApi,
        chatLocalPrefs: ProtoChatLocalPrefs,
        draftPrefs: ProtoDraftPrefs,
        appPrefs: ProtoAppPreferences,
    ) = withContext(Dispatchers.IO) {
        val remote = api.fetchClientPrefs(token) ?: return@withContext
        if (remote.chatFolders.isNotBlank()) {
            chatLocalPrefs.replaceFoldersPayload(remote.chatFolders)
        }
        if (remote.chatDrafts.isNotBlank()) {
            draftPrefs.importPayload(remote.chatDrafts)
        }
        appPrefs.setNotifyMentionsOnly(remote.notifyMentionsOnly)
    }

    suspend fun pushFolders(
        token: String,
        api: ProtoApi,
        folders: List<ChatFolder>,
    ) = withContext(Dispatchers.IO) {
        api.saveClientPrefs(token, chatFolders = ProtoChatLocalPrefs.encodeFoldersPayload(folders))
    }

    suspend fun pushDrafts(
        token: String,
        api: ProtoApi,
        draftPrefs: ProtoDraftPrefs,
    ) = withContext(Dispatchers.IO) {
        val payload = draftPrefs.exportPayload()
        api.saveClientPrefs(token, chatDrafts = payload)
    }

    suspend fun pushNotifyMentionsOnly(
        token: String,
        api: ProtoApi,
        enabled: Boolean,
    ) = withContext(Dispatchers.IO) {
        api.saveClientPrefs(token, notifyMentionsOnly = enabled)
    }
}
