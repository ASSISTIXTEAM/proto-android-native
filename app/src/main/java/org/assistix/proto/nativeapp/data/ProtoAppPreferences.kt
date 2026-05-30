package org.assistix.proto.nativeapp.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class ProtoAppPreferences(private val context: Context) {
    private val appPrefs get() = ProtoDataStoreFactory.preferences(context, "proto_app_prefs")
    private val onboardingDone = booleanPreferencesKey("onboarding_done")
    private val postRegistrationTourDone = booleanPreferencesKey("post_registration_tour_done")
    private val protoChannelSubscribePromptDone = booleanPreferencesKey("proto_channel_subscribe_prompt_done")
    private val messageNotif = booleanPreferencesKey("message_notifications")
    private val callNotif = booleanPreferencesKey("call_notifications")
    private val readReceipts = booleanPreferencesKey("read_receipts")
    private val typingIndicators = booleanPreferencesKey("typing_indicators")
    private val autoDownloadMedia = booleanPreferencesKey("auto_download_media")
    private val textSize = floatPreferencesKey("text_size_scale")
    private val reduceMotion = booleanPreferencesKey("reduce_motion")
    private val languageCode = stringPreferencesKey("language_code")
    private val assistixModel = stringPreferencesKey("assistix_model")
    private val autoTranslateChatsKey = booleanPreferencesKey("auto_translate_chats")
    private val autoAppUpdateKey = booleanPreferencesKey("auto_app_update")
    private val dismissedUpdateCode = stringPreferencesKey("dismissed_update_code")
    private val lastUpdateCheck = stringPreferencesKey("last_update_check_at")
    private val cachedUpdateJson = stringPreferencesKey("cached_update_json")
    private val updateNotifiedVersionCode = stringPreferencesKey("update_notified_version_code")
    private val notifyMentionsOnlyKey = booleanPreferencesKey("notify_mentions_only")
    private val policyAcceptedVersionKey = stringPreferencesKey("policy_accepted_version")
    private val whatsNew148Seen = booleanPreferencesKey("whats_new_148_seen")
    private val linkPreviewsKey = booleanPreferencesKey("link_previews_in_chat")
    private val sendOnEnterKey = booleanPreferencesKey("send_on_enter")
    private val compactChatListKey = booleanPreferencesKey("compact_chat_list")
    private val hapticFeedbackKey = booleanPreferencesKey("haptic_feedback")
    private val voiceSpeedIdxKey = stringPreferencesKey("voice_playback_speed_idx")
    private val sortUnreadFirstKey = booleanPreferencesKey("sort_unread_first")
    private val sttWifiHeavyKey = booleanPreferencesKey("stt_wifi_heavy_models")
    private val sttChargingOnlyKey = booleanPreferencesKey("stt_only_when_charging")
    private val sttMaxQueueKey = stringPreferencesKey("stt_max_queue")

    val onboardingComplete: Flow<Boolean> =
        appPrefs.data.map { it[onboardingDone] == true }

    val messageNotifications: Flow<Boolean> =
        appPrefs.data.map { it[messageNotif] != false }

    val callNotifications: Flow<Boolean> =
        appPrefs.data.map { it[callNotif] != false }

    val showReadReceipts: Flow<Boolean> =
        appPrefs.data.map { it[readReceipts] != false }

    val showTyping: Flow<Boolean> =
        appPrefs.data.map { it[typingIndicators] != false }

    /** When true, prefetch media on Wi‑Fi only (default on for new installs). */
    val autoDownload: Flow<Boolean> =
        appPrefs.data.map { prefs ->
            when {
                prefs[autoDownloadMedia] == null -> true
                else -> prefs[autoDownloadMedia] == true
            }
        }

    val textSizeScale: Flow<Float> =
        appPrefs.data.map { it[textSize] ?: 1f }

    val reduceMotionEnabled: Flow<Boolean> =
        appPrefs.data.map { it[reduceMotion] == true }

    val languageCodeFlow: Flow<String> =
        appPrefs.data.map { it[languageCode] ?: "en" }

    val assistixModelFlow: Flow<String> =
        appPrefs.data.map { it[assistixModel] ?: "tide" }

    /** Auto-translate incoming messages in open chats to UI language. */
    val autoTranslateChats: Flow<Boolean> =
        appPrefs.data.map { it[autoTranslateChatsKey] == true }

    /** Download PROTO updates in background when available. */
    val autoAppUpdate: Flow<Boolean> =
        appPrefs.data.map { it[autoAppUpdateKey] != false }

    /** In groups, notify only when @mentioned. */
    val notifyMentionsOnly: Flow<Boolean> =
        appPrefs.data.map { it[notifyMentionsOnlyKey] == true }

    val linkPreviewsInChat: Flow<Boolean> =
        appPrefs.data.map { it[linkPreviewsKey] != false }

    val sendOnEnter: Flow<Boolean> =
        appPrefs.data.map { it[sendOnEnterKey] == true }

    val compactChatList: Flow<Boolean> =
        appPrefs.data.map { it[compactChatListKey] == true }

    val hapticFeedback: Flow<Boolean> =
        appPrefs.data.map { it[hapticFeedbackKey] != false }

    val voicePlaybackSpeedIdx: Flow<Int> =
        appPrefs.data.map { prefs ->
            (prefs[voiceSpeedIdxKey]?.toIntOrNull() ?: 0).coerceIn(0, 2)
        }

    val sortUnreadFirst: Flow<Boolean> =
        appPrefs.data.map { it[sortUnreadFirstKey] == true }

    suspend fun setOnboardingComplete() {
        appPrefs.edit { it[onboardingDone] = true }
    }

    suspend fun isPostRegistrationTourDone(): Boolean =
        appPrefs.data.first()[postRegistrationTourDone] == true

    suspend fun setPostRegistrationTourDone() {
        appPrefs.edit { it[postRegistrationTourDone] = true }
    }

    suspend fun isProtoChannelSubscribePromptDone(): Boolean =
        appPrefs.data.first()[protoChannelSubscribePromptDone] == true

    suspend fun setProtoChannelSubscribePromptDone() {
        appPrefs.edit { it[protoChannelSubscribePromptDone] = true }
    }

    suspend fun setPolicyAccepted(version: String) {
        appPrefs.edit { it[policyAcceptedVersionKey] = version }
    }

    suspend fun hasPolicyAccepted(version: String): Boolean {
        val saved = appPrefs.data.first()[policyAcceptedVersionKey].orEmpty()
        return saved == version
    }

    suspend fun setMessageNotifications(on: Boolean) {
        appPrefs.edit { it[messageNotif] = on }
    }

    suspend fun setCallNotifications(on: Boolean) {
        appPrefs.edit { it[callNotif] = on }
    }

    suspend fun setReadReceipts(on: Boolean) {
        appPrefs.edit { it[readReceipts] = on }
    }

    suspend fun setTypingIndicators(on: Boolean) {
        appPrefs.edit { it[typingIndicators] = on }
    }

    suspend fun setAutoDownload(on: Boolean) {
        appPrefs.edit { it[autoDownloadMedia] = on }
    }

    suspend fun setTextSizeScale(scale: Float) {
        appPrefs.edit { it[textSize] = scale.coerceIn(0.85f, 1.25f) }
    }

    suspend fun setReduceMotion(on: Boolean) {
        appPrefs.edit { it[reduceMotion] = on }
    }

    suspend fun setLanguageCode(code: String) {
        val c = code.lowercase().let { if (it in setOf("ru", "it")) it else "en" }
        appPrefs.edit { it[languageCode] = c }
    }

    suspend fun setAssistixModel(key: String) {
        val k = key.lowercase().let { if (it in setOf("tide", "flair", "pine")) it else "tide" }
        appPrefs.edit { it[assistixModel] = k }
    }

    suspend fun setAutoTranslateChats(on: Boolean) {
        appPrefs.edit { it[autoTranslateChatsKey] = on }
    }

    suspend fun setAutoAppUpdate(on: Boolean) {
        appPrefs.edit { it[autoAppUpdateKey] = on }
    }

    suspend fun setNotifyMentionsOnly(on: Boolean) {
        appPrefs.edit { it[notifyMentionsOnlyKey] = on }
    }

    suspend fun setLinkPreviewsInChat(on: Boolean) {
        appPrefs.edit { it[linkPreviewsKey] = on }
    }

    suspend fun setSendOnEnter(on: Boolean) {
        appPrefs.edit { it[sendOnEnterKey] = on }
    }

    suspend fun setCompactChatList(on: Boolean) {
        appPrefs.edit { it[compactChatListKey] = on }
    }

    suspend fun setHapticFeedback(on: Boolean) {
        appPrefs.edit { it[hapticFeedbackKey] = on }
    }

    suspend fun setVoicePlaybackSpeedIdx(idx: Int) {
        appPrefs.edit { it[voiceSpeedIdxKey] = idx.coerceIn(0, 2).toString() }
    }

    suspend fun setSortUnreadFirst(on: Boolean) {
        appPrefs.edit { it[sortUnreadFirstKey] = on }
    }

    suspend fun clearOnboardingComplete() {
        appPrefs.edit { it[onboardingDone] = false }
    }

    suspend fun setDismissedUpdateVersion(code: Int) {
        appPrefs.edit { it[dismissedUpdateCode] = code.toString() }
    }

    suspend fun getDismissedUpdateVersion(): Int =
        appPrefs.data.map { it[dismissedUpdateCode]?.toIntOrNull() ?: 0 }.first()

    suspend fun setLastUpdateCheckAt(at: Long) {
        appPrefs.edit { it[lastUpdateCheck] = at.toString() }
    }

    suspend fun cacheUpdateInfo(info: org.assistix.proto.nativeapp.update.AppUpdateInfo) {
        val j =
            org.json.JSONObject()
                .put("version_code", info.versionCode)
                .put("version_name", info.versionName)
                .put("apk_url", info.apkUrl)
                .put("apk_direct_url", info.apkDirectUrl)
                .put("apk_sha256", info.apkSha256)
                .put("apk_size_bytes", info.apkSizeBytes)
                .put("changelog", info.changelog)
                .put("force", info.force)
                .put("block_app", info.blockApp)
                .put("required_message", info.requiredMessage)
                .put("min_version_code", info.minVersionCode)
                .put("help_url", info.helpUrl)
                .put("support_email", info.supportEmail)
        appPrefs.edit { it[cachedUpdateJson] = j.toString() }
    }

    suspend fun clearCachedUpdateInfo() {
        appPrefs.edit { it.remove(cachedUpdateJson) }
    }

    suspend fun getCachedUpdateInfo(): org.assistix.proto.nativeapp.update.AppUpdateInfo? {
        val raw = appPrefs.data.map { it[cachedUpdateJson] }.first() ?: return null
        return try {
            val j = org.json.JSONObject(raw)
            org.assistix.proto.nativeapp.update.AppUpdateInfo(
                versionCode = j.optInt("version_code"),
                versionName = j.optString("version_name"),
                apkUrl = j.optString("apk_url"),
                apkDirectUrl = j.optString("apk_direct_url"),
                apkSha256 = j.optString("apk_sha256"),
                apkSizeBytes = j.optLong("apk_size_bytes"),
                changelog = j.optString("changelog"),
                force = j.optBoolean("force"),
                blockApp = j.optBoolean("block_app"),
                requiredMessage = j.optString("required_message"),
                minVersionCode = j.optInt("min_version_code", 1),
                helpUrl =
                    j.optString("help_url").trim().ifBlank {
                        "https://proto.su/download.html#help-install"
                    },
                supportEmail = j.optString("support_email").trim().ifBlank { "team@proto.su" },
            )
        } catch (_: Exception) {
            null
        }
    }

    suspend fun getUpdateNotifiedVersionCode(): Int =
        appPrefs.data.map { it[updateNotifiedVersionCode]?.toIntOrNull() ?: 0 }.first()

    suspend fun setUpdateNotifiedVersionCode(code: Int) {
        appPrefs.edit { it[updateNotifiedVersionCode] = code.toString() }
    }

    suspend fun hasSeenWhatsNew148(): Boolean =
        appPrefs.data.map { it[whatsNew148Seen] == true }.first()

    suspend fun setSeenWhatsNew148() {
        appPrefs.edit { it[whatsNew148Seen] = true }
    }

    val sttWifiOnlyHeavyModels: Flow<Boolean> =
        appPrefs.data.map { it[sttWifiHeavyKey] != false }

    val sttOnlyWhenCharging: Flow<Boolean> =
        appPrefs.data.map { it[sttChargingOnlyKey] == true }

    val sttMaxQueuePerBurst: Flow<Int> =
        appPrefs.data.map { it[sttMaxQueueKey]?.toIntOrNull()?.coerceIn(3, 50) ?: 20 }

    suspend fun setSttWifiOnlyHeavyModels(on: Boolean) {
        appPrefs.edit { it[sttWifiHeavyKey] = on }
    }

    suspend fun setSttOnlyWhenCharging(on: Boolean) {
        appPrefs.edit { it[sttChargingOnlyKey] = on }
    }

    suspend fun setSttMaxQueuePerBurst(n: Int) {
        appPrefs.edit { it[sttMaxQueueKey] = n.coerceIn(3, 50).toString() }
    }
}
