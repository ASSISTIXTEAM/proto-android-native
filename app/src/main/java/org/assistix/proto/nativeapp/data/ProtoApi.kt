package org.assistix.proto.nativeapp.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.assistix.proto.nativeapp.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.File
import java.util.concurrent.TimeUnit

enum class MediaDownloadResult {
    Ok,
    ExpiredRelay,
    Failed,
}

class ProtoApi(private val appContext: android.content.Context? = null) {
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    @Volatile var lastHttpOk: Boolean = true
        private set
    @Volatile var lastApiOrigin: String = ProtoApiOrigin.primary()
        private set

    private val client =
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(90, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

    private val streamClient =
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .writeTimeout(90, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

    private val sttClient =
        OkHttpClient.Builder()
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(90, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

    fun mediaUrl(uploadId: String) = url("/api/media.php?id=$uploadId")

    fun authHeaders(token: String): Map<String, String> =
        mapOf("Authorization" to "Bearer $token", "X-Proto-Session" to token)

    private fun url(path: String) = ProtoApiOrigin.url(path, lastApiOrigin)

    private fun rememberOrigin(origin: String) {
        lastApiOrigin = origin.trimEnd('/')
        ProtoApiOrigin.rememberWorking(lastApiOrigin)
        appContext?.let { ProtoApiOrigin.persist(it, lastApiOrigin) }
    }

    private fun nonJsonAuthMessage(body: String): String {
        if (ProtoApiOrigin.looksLikeHtml(body)) {
            return org.assistix.proto.nativeapp.ui.UiStrings.apiHostMisconfigured
        }
        return authErrorMessage(body, org.assistix.proto.nativeapp.ui.UiStrings.serverNotJson)
    }

    private fun parse(body: String): JSONObject {
        val s = body.trim()
        if (s.isEmpty()) return JSONObject()
        val first = s.first()
        if (first != '{' && first != '[') {
            throw org.json.JSONException("non_json_response")
        }
        return JSONObject(s)
    }

    private fun authErrorMessage(body: String, fallback: String): String {
        val s = body.trim()
        if (s.startsWith("{")) {
            return try {
                val j = JSONObject(s)
                j.optString("message", "").ifBlank { j.optString("error", fallback) }
            } catch (_: Exception) {
                fallback
            }
        }
        return fallback
    }

    private fun authedGet(token: String, path: String): JSONObject? =
        withApiOrigins { origin ->
            val req =
                Request.Builder()
                    .url(ProtoApiOrigin.url(path, origin))
                    .header("Authorization", "Bearer $token")
                    .header("X-Proto-Session", token)
                    .get()
                    .build()
            try {
                client.newCall(req).execute().use { res ->
                    lastHttpOk = res.isSuccessful
                    val raw = res.body?.string() ?: ""
                    if (!res.isSuccessful || ProtoApiOrigin.looksLikeHtml(raw)) return@withApiOrigins null
                    parse(raw)
                }
            } catch (_: Exception) {
                lastHttpOk = false
                null
            }
        }

    private fun authedPost(token: String, path: String, payload: JSONObject): JSONObject? =
        withApiOrigins { origin ->
            val req =
                Request.Builder()
                    .url(ProtoApiOrigin.url(path, origin))
                    .header("Authorization", "Bearer $token")
                    .header("X-Proto-Session", token)
                    .post(payload.toString().toRequestBody(jsonType))
                    .build()
            try {
                client.newCall(req).execute().use { res ->
                    lastHttpOk = res.isSuccessful
                    val raw = res.body?.string() ?: ""
                    if (!res.isSuccessful || ProtoApiOrigin.looksLikeHtml(raw)) return@withApiOrigins null
                    parse(raw)
                }
            } catch (_: Exception) {
                lastHttpOk = false
                null
            }
        }

    private inline fun <T> withApiOrigins(block: (String) -> T?): T? {
        for (origin in ProtoApiOrigin.orderedOrigins()) {
            val value = block(origin) ?: continue
            rememberOrigin(origin)
            return value
        }
        return null
    }

    fun login(login: String, password: String): AuthResult = postAuth(
        JSONObject().put("action", "login").put("login", login.trim()).put("password", password).put("device_label", "Android"),
    )

    fun sendRegistrationCode(email: String): AuthResult =
        postAuth(
            JSONObject()
                .put("action", "send_registration_code")
                .put("email", email.trim().lowercase())
                .put("device_label", "Android"),
        )

    fun verifyRegistrationCode(email: String, code: String): AuthResult =
        postAuth(
            JSONObject()
                .put("action", "verify_registration_code")
                .put("email", email.trim().lowercase())
                .put("code", code.trim())
                .put("device_label", "Android"),
        )

    fun suggestNick(displayName: String): NickSuggestion? = suggestNickFromServer(displayName)

    private fun suggestNickFromServer(displayName: String): NickSuggestion? {
        var last: NickSuggestion? = null
        for (origin in ProtoApiOrigin.orderedOrigins()) {
            val req =
                Request.Builder()
                    .url(ProtoApiOrigin.url("/api/auth.php", origin))
                    .post(
                        JSONObject()
                            .put("action", "suggest_nick")
                            .put("display_name", displayName.trim())
                            .toString()
                            .toRequestBody(jsonType),
                    )
                    .build()
            val parsed =
                try {
                    client.newCall(req).execute().use { res ->
                        val raw = res.body?.string() ?: ""
                        if (!res.isSuccessful || ProtoApiOrigin.looksLikeHtml(raw)) return@use null
                        val j = parse(raw)
                        if (!j.optBoolean("ok", false)) return@use null
                        NickSuggestion(
                            nick = j.optString("nick", ""),
                            alternatives =
                                buildList {
                                    val arr = j.optJSONArray("alternatives") ?: return@buildList
                                    for (i in 0 until arr.length()) {
                                        arr.optString(i)?.takeIf { it.isNotBlank() }?.let { add(it) }
                                    }
                                },
                        )
                    }
                } catch (_: Exception) {
                    null
                }
            if (parsed != null && parsed.nick.isNotBlank()) {
                rememberOrigin(origin)
                return parsed
            }
        }
        return last
    }

    fun checkNickAvailable(nick: String): Boolean {
        val n = nick.trim().removePrefix("@")
        for (origin in ProtoApiOrigin.orderedOrigins()) {
            val req =
                Request.Builder()
                    .url(ProtoApiOrigin.url("/api/auth.php", origin))
                    .post(
                        JSONObject()
                            .put("action", "check_nick")
                            .put("nick", n)
                            .toString()
                            .toRequestBody(jsonType),
                    )
                    .build()
            try {
                client.newCall(req).execute().use { res ->
                    val raw = res.body?.string() ?: ""
                    if (!res.isSuccessful || ProtoApiOrigin.looksLikeHtml(raw)) return@use false
                    val j = parse(raw)
                    if (j.optBoolean("ok", false)) {
                        rememberOrigin(origin)
                        return j.optBoolean("available", false)
                    }
                }
            } catch (_: Exception) {
            }
        }
        return false
    }

    fun register(
        nick: String,
        password: String,
        email: String,
        displayName: String,
        acceptPolicy: Boolean,
        registrationProof: String,
    ): AuthResult =
        postAuth(
            JSONObject()
                .put("action", "register")
                .put("nick", nick.trim())
                .put("password", password)
                .put("display_name", displayName.trim())
                .put("email", email.trim().lowercase())
                .put("registration_proof", registrationProof)
                .put("accept_policy", acceptPolicy)
                .put("policy_version", org.assistix.proto.nativeapp.ProtoLegal.POLICY_VERSION)
                .put("device_label", "Android"),
        )

    fun blockUser(token: String, userId: Int): Boolean =
        authedPost(token, "/api/safety.php", JSONObject().put("action", "block").put("user_id", userId))
            ?.optBoolean("ok", false) == true

    fun reportUser(token: String, targetUserId: Int, reason: String, details: String): String? =
        report(token, targetUserId, 0, reason, details, emptyList())

    fun reportMessage(
        token: String,
        targetUserId: Int,
        messageId: Long,
        conversationId: Int,
        reason: String,
        details: String,
    ): String? = report(token, targetUserId, conversationId, reason, details, listOfNotNull(messageId.takeIf { it > 0 }))

    fun report(
        token: String,
        targetUserId: Int,
        conversationId: Int,
        reason: String,
        details: String,
        messageIds: List<Long>,
    ): String? {
        val ids = messageIds.filter { it > 0 }.distinct()
        val body =
            JSONObject()
                .put("action", "report")
                .put("target_user_id", targetUserId)
                .put("reason", reason)
                .put("details", details)
                .put("device_label", "Android")
        if (conversationId > 0) body.put("conversation_id", conversationId)
        if (ids.isNotEmpty()) {
            body.put("message_id", ids.first())
            val arr = org.json.JSONArray()
            ids.forEach { arr.put(it) }
            body.put("message_ids", arr)
        }
        val j = authedPost(token, "/api/safety.php", body) ?: return null
        return j.optString("message", "").ifBlank { if (j.optBoolean("ok")) "OK" else null }
    }

    fun assistixTranslate(token: String, text: String, targetLanguage: String): AssistixReply =
        assistixRequest(token, "translate", text, language = targetLanguage, targetLanguage = targetLanguage)

    fun assistixTranslateBatch(
        token: String,
        items: List<Pair<Long, String>>,
        targetLanguage: String,
    ): Map<Long, String> {
        if (items.isEmpty()) return emptyMap()
        val arr = org.json.JSONArray()
        items.take(24).forEach { (id, txt) ->
            if (id > 0 && txt.isNotBlank()) {
                arr.put(org.json.JSONObject().put("id", id).put("text", txt.take(500)))
            }
        }
        if (arr.length() == 0) return emptyMap()
        val payload =
            org.json.JSONObject()
                .put("action", "translate_batch")
                .put("target_language", targetLanguage)
                .put("language", targetLanguage)
                .put("items", arr)
        val j =
            try {
                authedPost(token, "/api/assistix.php", payload)
            } catch (_: Exception) {
                null
            } ?: return emptyMap()
        if (!j.optBoolean("ok", false)) return emptyMap()
        parseAssistixRateLimit(j)?.let { AssistixUsageHub.apply(it) }
        val out = mutableMapOf<Long, String>()
        val tr = j.optJSONArray("translations") ?: return emptyMap()
        for (i in 0 until tr.length()) {
            val o = tr.optJSONObject(i) ?: continue
            val id = o.optLong("id", 0)
            val t = o.optString("text", "").trim()
            if (id > 0 && t.isNotBlank()) out[id] = t
        }
        return out
    }

    fun revokeOtherDevices(token: String): Boolean =
        authedPost(token, "/api/devices.php", org.json.JSONObject().put("action", "revoke_others"))
            ?.optBoolean("ok", false) == true

    fun clearChatHistory(token: String, conversationId: Int): Boolean =
        authedPost(
            token,
            "/api/conversations.php",
            JSONObject().put("action", "clear_history").put("conversation_id", conversationId),
        )?.optBoolean("ok", false) == true

    fun hideChat(token: String, conversationId: Int): Boolean =
        authedPost(
            token,
            "/api/conversations.php",
            JSONObject().put("action", "hide_chat").put("conversation_id", conversationId),
        )?.optBoolean("ok", false) == true

    fun verifyEmail(userId: Int, code: String): AuthResult =
        postAuth(
            JSONObject()
                .put("action", "verify_email")
                .put("user_id", userId)
                .put("code", code.trim())
                .put("device_label", "Android"),
        )

    fun resendVerification(userId: Int): AuthResult =
        postAuth(
            JSONObject()
                .put("action", "resend_verification")
                .put("user_id", userId)
                .put("device_label", "Android"),
        )

    fun forgotPassword(login: String): AuthResult =
        postAuth(
            JSONObject()
                .put("action", "forgot_password")
                .put("login", login.trim())
                .put("device_label", "Android"),
        )

    fun logout(token: String) {
        runCatching {
            authedPost(token, "/api/auth.php", JSONObject().put("action", "logout").put("device_label", "Android"))
        }
    }

    private fun postAuth(payload: JSONObject): AuthResult {
        var lastFail: AuthResult.Fail? = null
        for (origin in ProtoApiOrigin.orderedOrigins()) {
            val req =
                Request.Builder()
                    .url(ProtoApiOrigin.url("/api/auth.php", origin))
                    .post(payload.toString().toRequestBody(jsonType))
                    .build()
            val result =
                try {
                    client.newCall(req).execute().use { res ->
                        val raw = res.body?.string() ?: ""
                        parseAuthResponse(res, raw, payload)
                    }
                } catch (e: Exception) {
                    lastHttpOk = false
                    val msg = e.message ?: ""
                    val friendly =
                        when {
                            e is android.os.NetworkOnMainThreadException ->
                                org.assistix.proto.nativeapp.ui.UiStrings.genericError
                            msg.contains("JSONObject", ignoreCase = true) || msg.contains("non_json", ignoreCase = true) ->
                                org.assistix.proto.nativeapp.ui.UiStrings.serverNotJson
                            msg.contains("Unable to resolve host", ignoreCase = true) ||
                                msg.contains("failed to connect", ignoreCase = true) ||
                                msg.contains("timeout", ignoreCase = true) ->
                                org.assistix.proto.nativeapp.ui.UiStrings.networkUnavailable
                            msg.isNotBlank() -> msg
                            else -> org.assistix.proto.nativeapp.ui.UiStrings.networkUnavailable
                        }
                    AuthResult.Fail(friendly, retryable = e !is android.os.NetworkOnMainThreadException)
                }
            when (result) {
                is AuthResult.Fail -> {
                    lastFail = result
                    if (result.retryable) continue
                    return result
                }
                else -> {
                    rememberOrigin(origin)
                    return result
                }
            }
        }
        return lastFail ?: AuthResult.Fail(org.assistix.proto.nativeapp.ui.UiStrings.networkUnavailable)
    }

    private fun parseAuthResponse(res: okhttp3.Response, raw: String, payload: JSONObject): AuthResult {
        lastHttpOk = res.isSuccessful
        if (ProtoApiOrigin.looksLikeHtml(raw)) {
            return AuthResult.Fail(org.assistix.proto.nativeapp.ui.UiStrings.apiHostMisconfigured, retryable = true)
        }
        val j =
            try {
                parse(raw)
            } catch (_: org.json.JSONException) {
                lastHttpOk = false
                return AuthResult.Fail(nonJsonAuthMessage(raw), retryable = true)
            }
        val action = payload.optString("action")
        val pendingId =
            j.optInt("pending_user_id", 0).takeIf { it > 0 }
                ?: j.optInt("user_id", 0).takeIf { it > 0 }
                ?: 0
        val needsVerify =
            j.optBoolean("needs_email_verification", false)
                || j.optString("error", "") == "email_not_verified"
        val verifyMessage =
            j.optString("message", "").ifBlank {
                org.assistix.proto.nativeapp.ui.UiStrings.verifyEmailHint
            }
        val emailHint = j.optString("email_hint", "")
        val errCode = j.optString("error", "")

        if (action == "send_registration_code" && j.optBoolean("ok", false)) {
            val mailMode = j.optString("mail_delivery_mode", "")
            val mailHint = j.optString("mail_hint", "")
            val baseMsg =
                j.optString("message", "").ifBlank {
                    org.assistix.proto.nativeapp.ui.UiStrings.verifyEmailHint
                }
            val msg =
                if (mailMode == "log" && mailHint.isNotBlank()) {
                    "$baseMsg $mailHint"
                } else {
                    baseMsg
                }
            return AuthResult.MessageOk(msg)
        }

        if (action == "verify_registration_code") {
            val proof = j.optString("registration_proof", "")
            if (j.optBoolean("ok", false) && proof.isNotBlank()) {
                return AuthResult.RegistrationProof(
                    proof,
                    j.optString("email_hint", emailHint),
                    j.optString("message", ""),
                )
            }
            return AuthResult.Fail(
                j.optString("message", "").ifBlank {
                    org.assistix.proto.nativeapp.ui.UiStrings.signInFailed
                },
                retryable = errCode == "rate_limited",
                retryAfterSec = j.optInt("retry_after_sec", 0).coerceAtLeast(0),
            )
        }

        fun pendingVerification(): AuthResult.PendingEmailVerification? {
            if (pendingId < 1) return null
            if (
                action == "register" ||
                needsVerify ||
                errCode == "email_not_verified" ||
                errCode == "mail_failed"
            ) {
                return AuthResult.PendingEmailVerification(pendingId, emailHint, verifyMessage)
            }
            return null
        }

        pendingVerification()?.let { return it }

        if (action == "register" && needsVerify && pendingId < 1) {
            return AuthResult.Fail(
                j.optString("message", "").ifBlank {
                    org.assistix.proto.nativeapp.ui.UiStrings.verifyEmailRequiredBanner
                },
            )
        }

        val token = j.optString("token", "")
        if (token.isNotBlank()) {
            val user = j.optJSONObject("user") ?: JSONObject()
            return AuthResult.Ok(token, user.optInt("id"), user.optString("nick"))
        }
        if (needsVerify) {
            return AuthResult.Fail(
                j.optString("message", "").ifBlank {
                    org.assistix.proto.nativeapp.ui.UiStrings.verifyEmailMissingUserId
                },
            )
        }
        if (j.optBoolean("ok", false) && j.has("message") && !j.has("needs_email_verification")) {
            val msg = j.optString("message", "")
            if (msg.isNotBlank() && payload.optString("action") in setOf("forgot_password", "resend_verification")) {
                return AuthResult.MessageOk(msg)
            }
        }
        if (!res.isSuccessful || !j.optBoolean("ok", false)) {
            val retrySec = j.optInt("retry_after_sec", 0).takeIf { it > 0 }
            val mailDetail = j.optString("mail_detail", "")
            val errKey = j.optString("error", "")
            val baseMsg =
                when {
                    errKey == "bootstrap_failed" ->
                        j.optString("message", "").ifBlank {
                            org.assistix.proto.nativeapp.ui.UiStrings.apiHostMisconfigured
                        }
                    else ->
                        j.optString("message", "").ifBlank {
                            j.optString("error", org.assistix.proto.nativeapp.ui.UiStrings.signInFailed)
                        }
                }
            val msg =
                if (errCode == "mail_failed" && mailDetail.isNotBlank()) {
                    "$baseMsg ($mailDetail)"
                } else {
                    baseMsg
                }
            return AuthResult.Fail(
                msg,
                retryAfterSec = retrySec ?: 0,
            )
        }
        return AuthResult.Fail(org.assistix.proto.nativeapp.ui.UiStrings.emptyToken)
    }

    data class MeLoad(
        val profile: MeProfile,
        val restriction: AccountRestriction?,
    )

    fun me(token: String): MeLoad? {
        val j = authedGet(token, "/api/me.php") ?: return null
        val u = j.optJSONObject("user") ?: return null
        return MeLoad(parseMeProfile(u), j.optRestriction("account_restriction"))
    }

    /** Round-trip latency to API origin in ms, or -1 if unreachable. */
    fun measureApiLatencyMs(): Long {
        val start = System.nanoTime()
        return try {
            val req =
                Request.Builder()
                    .url(url("/api/geo.php"))
                    .get()
                    .build()
            client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return -1L
            }
            ((System.nanoTime() - start) / 1_000_000L).coerceAtLeast(0L)
        } catch (_: Exception) {
            -1L
        }
    }

    /** Public geo hint (Cloudflare country header on proto.su). Empty = unknown. */
    fun fetchGeoCountry(): String? =
        try {
            val req =
                Request.Builder()
                    .url(url("/api/geo.php"))
                    .get()
                    .build()
            client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return null
                val j = parse(res.body?.string() ?: "")
                if (!j.optBoolean("ok", false)) return null
                j.optString("country", "").trim().uppercase().ifBlank { null }
            }
        } catch (_: Exception) {
            null
        }

    fun ensureSavedConversation(token: String): Int? {
        val j =
            authedPost(
                token,
                "/api/conversations.php",
                JSONObject().put("action", "ensure_saved"),
            ) ?: return null
        val cid = j.optInt("conversation_id", 0)
        return cid.takeIf { it > 0 }
    }

    fun fetchLinkPreview(token: String?, url: String): LinkPreview? {
        if (token.isNullOrBlank()) return null
        val encoded = java.net.URLEncoder.encode(url, Charsets.UTF_8.name())
        val j = authedGet(token, "/api/link_preview.php?url=$encoded") ?: return null
        parseAssistixRateLimit(j)?.let { AssistixUsageHub.apply(it) }
        val p = j.optJSONObject("preview") ?: return null
        return LinkPreview(
            url = j.optString("url", url),
            title = p.optString("title", ""),
            description = p.optString("description", ""),
            imageUrl = p.optString("image", ""),
            siteName = p.optString("site_name", ""),
            aiSummary = p.optString("ai_summary", ""),
        )
    }

    fun conversations(token: String): List<ConvItem> {
        val arr = authedGet(token, "/api/conversations.php")?.optJSONArray("conversations") ?: JSONArray()
        return (0 until arr.length()).mapNotNull { i ->
            val c = arr.optJSONObject(i) ?: return@mapNotNull null
            val peer = c.optJSONObject("peer")
            val kind = c.optString("kind", "dm")
            val isGroup = kind == "group"
            val isChannel = kind == "channel"
            val isSaved = kind == "saved" || c.optBoolean("is_saved", false)
            val g = c.optJSONObject("group")
            val ch = c.optJSONObject("channel")
            ConvItem(
                id = c.optInt("id"),
                kind = if (isSaved) "saved" else kind,
                title =
                    when {
                        isSaved -> ""
                        isChannel -> c.optString("title", "Channel")
                        isGroup -> c.optString("title", "Group")
                        else -> peer?.let { peerDisplay(it) } ?: "Chat"
                    },
                channelNick = if (isChannel) ch?.optString("nick", "") ?: "" else "",
                channelDescription = if (isChannel) ch?.optString("description", "") ?: "" else "",
                channelVerified = isChannel && ch?.optBoolean("verified", false) == true,
                channelAvatarUploadId = if (isChannel) ch?.optCleanString("avatar_upload_id") else null,
                preview = c.optString("preview", ""),
                updatedAt =
                    maxOf(
                        c.optLong("updated_at", 0),
                        c.optLong("last_message_at", 0),
                    ) * 1000L,
                peerUserId = peer?.optInt("id", 0) ?: 0,
                peerDisplayName = if (isGroup) "" else peer?.optString("display_name", "") ?: "",
                peerStatusEmoji = if (isGroup) "" else peer?.optString("status_emoji", "") ?: "",
                peerAvatarUploadId = if (isGroup) null else peer?.optCleanString("avatar_upload_id"),
                unreadCount = c.optInt("unread_count", 0),
                myLastReadMessageId = c.optLong("my_last_read_message_id", 0),
                groupOwnerId = g?.optInt("owner_id", 0) ?: 0,
                groupMyRole = g?.optString("my_role", "") ?: "",
                groupMemberCount = g?.optInt("member_count", 0) ?: 0,
                lastSenderId = c.optInt("last_sender_id", 0),
                lastMessageId = c.optLong("last_message_id", 0).takeIf { it > 0 } ?: 0L,
            )
        }
    }

    fun listDevices(token: String): List<DeviceSession> {
        val arr = authedGet(token, "/api/devices.php")?.optJSONArray("sessions") ?: JSONArray()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            DeviceSession(
                id = o.optInt("id"),
                label = o.optString("device_label", ""),
                createdAt = o.optLong("created_at", 0),
                lastActive = o.optLong("last_active", 0),
                revoked = o.optBoolean("revoked", false),
                current = o.optBoolean("current", false),
                lastIp = o.optString("last_ip", ""),
                lastCountry = o.optString("last_country", ""),
            )
        }
    }

    fun revokeDevice(token: String, sessionId: Int): Boolean {
        val j =
            authedPost(
                token,
                "/api/devices.php",
                JSONObject().put("action", "revoke").put("session_id", sessionId),
            )
        return j?.optBoolean("revoked", false) == true
    }

    fun approveDeviceLink(token: String, pairId: String, secret: String): Pair<Boolean, String?> {
        val j =
            authedPost(
                token,
                "/api/link.php",
                JSONObject()
                    .put("action", "approve")
                    .put("pair_id", pairId)
                    .put("secret", secret),
            )
        if (j?.optBoolean("ok", false) == true) return true to null
        val msg = j?.optString("message", "")?.ifBlank { j.optString("error", "") } ?: ""
        return false to msg.ifBlank { "Не удалось подключить устройство" }
    }

    data class PresenceState(val typingUserIds: Set<Int> = emptySet(), val recordingUserIds: Set<Int> = emptySet())

    fun presenceState(token: String, conversationId: Int): PresenceState {
        val j = authedGet(token, "/api/presence.php?conversation_id=$conversationId") ?: return PresenceState()
        fun parseArr(key: String): Set<Int> {
            val arr = j.optJSONArray(key) ?: JSONArray()
            val out = mutableSetOf<Int>()
            for (i in 0 until arr.length()) {
                val id = arr.optInt(i, 0)
                if (id > 0) out.add(id)
            }
            return out
        }
        return PresenceState(parseArr("typing_user_ids"), parseArr("recording_user_ids"))
    }

    fun typingUserIds(token: String, conversationId: Int): Set<Int> = presenceState(token, conversationId).typingUserIds

    fun searchUsers(token: String, query: String): List<UserHit> {
        var q = query.trim()
        if (q.startsWith("@")) q = q.removePrefix("@").trim()
        if (q.length < 1) return emptyList()
        val enc = java.net.URLEncoder.encode(q, Charsets.UTF_8.name())
        val arr = authedGet(token, "/api/users.php?q=$enc")?.optJSONArray("users") ?: JSONArray()
        return (0 until arr.length()).mapNotNull { i ->
            val u = arr.optJSONObject(i) ?: return@mapNotNull null
            UserHit(
                u.optInt("id"),
                u.optString("nick"),
                u.optString("display_name", ""),
                u.optString("status_emoji", ""),
                u.optCleanString("avatar_upload_id"),
            )
        }
    }

    fun messages(token: String, conversationId: Int, myUserId: Int, since: Long = 0): List<MsgItem> {
        val arr = authedGet(token, "/api/messages.php?conversation_id=$conversationId&since=$since")?.optJSONArray("messages") ?: JSONArray()
        return (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let { parseMessage(it, myUserId) } }
    }

    fun createPoll(
        token: String,
        conversationId: Int,
        question: String,
        options: List<String>,
        allowMultiple: Boolean,
        anonymous: Boolean = false,
        closesAtSec: Long = 0,
    ): Long? {
        val arr = JSONArray()
        options.forEach { arr.put(it) }
        val body =
            JSONObject()
                .put("action", "create_poll")
                .put("conversation_id", conversationId)
                .put("question", question)
                .put("options", arr)
                .put("allow_multiple", allowMultiple)
                .put("anonymous", anonymous)
        if (closesAtSec > 0) body.put("closes_at", closesAtSec)
        val j = authedPost(token, "/api/messages.php", body) ?: return null
        return j.optLong("message_id").takeIf { it > 0 }
    }

    fun pollVote(token: String, conversationId: Int, messageId: Long, optionIndex: Int): Boolean {
        val j =
            authedPost(
                token,
                "/api/messages.php",
                JSONObject()
                    .put("action", "poll_vote")
                    .put("conversation_id", conversationId)
                    .put("message_id", messageId)
                    .put("option_index", optionIndex),
            ) ?: return false
        return j.optBoolean("ok", false)
    }

    fun parseMessage(m: JSONObject, myUserId: Int): MsgItem {
        val bodyRaw = m.optString("body", "")
        val albumMeta = AlbumMeta.fromJson(bodyRaw)
        val channelCardMeta = if (albumMeta == null) ChannelCardMeta.fromJson(bodyRaw) else null
        val channelPostMeta =
            if (albumMeta == null && channelCardMeta == null) ChannelPostMeta.fromJson(bodyRaw) else null
        val pollMeta =
            if (albumMeta == null && channelPostMeta == null && channelCardMeta == null) {
                PollMeta.fromJson(bodyRaw)
            } else {
                null
            }
        val callMeta =
            if (pollMeta == null && albumMeta == null && channelPostMeta == null && channelCardMeta == null) {
                CallMeta.fromJson(bodyRaw)
            } else {
                null
            }
        val mid = m.optCleanString("media_upload_id")
        val fwdObj = m.optJSONObject("forward")
        val fwd =
            fwdObj?.let {
                ForwardMeta(
                    fromLabel = it.optString("from_label", ""),
                    bodySnippet = it.optString("body_snippet", ""),
                    fromUserId = it.optInt("from_user_id", 0),
                    originalMessageId = it.optLong("original_message_id", 0),
                )
            }
        val type =
            when {
                channelCardMeta != null -> "channel_card"
                channelPostMeta != null -> "channel_post"
                pollMeta != null -> "poll"
                callMeta != null -> "call"
                albumMeta != null -> "album"
                mid != null -> "media"
                fwd != null -> "forward"
                else -> "text"
            }
        val albumCaption = if (albumMeta != null) AlbumMeta.captionFromJson(bodyRaw) else ""
        val displayBody =
            when {
                channelCardMeta != null -> "📢 @${channelCardMeta.nick}"
                channelPostMeta != null -> channelPostMeta.text.ifBlank { "📢" }
                pollMeta != null -> "📊 ${pollMeta.question}"
                callMeta != null -> callMeta.displayText(m.optInt("sender_id") == myUserId)
                albumMeta != null -> {
                    val n = albumMeta.items.size
                    val cap = albumCaption
                    if (cap.isNotBlank()) cap else "📷 $n"
                }
                else -> bodyRaw
            }
        val mime = m.optCleanString("media_mime")
        val mname = m.optCleanString("media_name")
        val senderObj = m.optJSONObject("sender")
        val senderId = m.optInt("sender_id", senderObj?.optInt("id", 0) ?: 0)
        val senderName =
            senderObj?.let { resolveDisplayName(it.optString("display_name", ""), it.optString("nick", "")) }
                ?: ""
        val replyId = m.optLong("reply_to_id", 0)
        val replyPreview = m.optString("reply_preview", "").trim()
        val reply =
            if (replyId > 0 && replyPreview.isNotEmpty()) {
                ReplyMeta(replyId, replyPreview, m.optInt("reply_sender_id", 0))
            } else {
                null
            }
        return MsgItem(
            id = m.optLong("id"),
            localId = "srv-${m.optLong("id")}",
            body = displayBody,
            bodyRaw = bodyRaw,
            mine = m.optInt("sender_id") == myUserId,
            createdAt = m.optLong("created_at") * 1000L,
            editedAt = m.optLong("edited_at", 0) * 1000L,
            isE2e = m.optInt("is_e2e") == 1,
            readByPeer = m.optBoolean("read_by_peer", false),
            peerReadAt = m.optLong("peer_read_at", 0) * 1000L,
            status = "sent",
            mediaUploadId = mid,
            mediaMime = mime,
            mediaName = mname,
            mediaKind = if (mid != null) mediaKindFromMime(mime, mname) else null,
            messageType = type,
            forward = fwd,
            callMeta = callMeta,
            pollMeta = pollMeta,
            channelPostMeta = channelPostMeta,
            channelCardMeta = channelCardMeta,
            albumMeta = albumMeta,
            reactions = parseReactions(m.optJSONArray("reactions"), myUserId),
            senderId = senderId,
            senderName = senderName,
            reply = reply,
        )
    }

    private fun parseReactions(arr: JSONArray?, myUserId: Int): List<MsgReaction> {
        if (arr == null) return emptyList()
        val grouped = linkedMapOf<String, Pair<Int, Boolean>>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val em = o.optString("emoji", "")
            if (em.isBlank()) continue
            val cur = grouped[em] ?: (0 to false)
            val uid = o.optInt("user_id")
            grouped[em] = (cur.first + 1) to (cur.second || uid == myUserId)
        }
        return grouped.map { (emoji, v) -> MsgReaction(emoji, v.first, v.second) }
    }

    fun userById(token: String, userId: Int): UserProfile? {
        if (userId <= 0) return null
        return try {
            val j = authedGet(token, "/api/users.php?id=$userId") ?: return null
            val u = j.optJSONObject("user") ?: return null
            parseUserProfile(u)
        } catch (_: Exception) {
            null
        }
    }

    fun updateProfile(
        token: String,
        displayName: String,
        bio: String,
        statusText: String,
        statusEmoji: String,
        avatarUploadId: String?,
        nick: String? = null,
    ): MeProfile? {
        val payload =
            JSONObject()
                .put("display_name", displayName)
                .put("bio", bio)
                .put("status_text", statusText)
                .put("status_emoji", statusEmoji)
        if (avatarUploadId != null) payload.put("avatar_upload_id", avatarUploadId)
        if (!nick.isNullOrBlank()) payload.put("nick", nick.trim())
        val j = authedPost(token, "/api/me.php", payload) ?: return null
        if (!j.optBoolean("ok", true) && j.has("error")) return null
        val u = j.optJSONObject("user") ?: return null
        return parseMeProfile(u)
    }

    sealed class NickChangeResult {
        data class Ok(val profile: MeProfile) : NickChangeResult()
        data class Fail(val code: String, val message: String) : NickChangeResult()
    }

    fun changeNick(token: String, nick: String): NickChangeResult {
        val payload = JSONObject().put("nick", nick.trim())
        val res =
            runCatching {
                val req =
                    Request.Builder()
                        .url(url("/api/me.php"))
                        .header("Authorization", "Bearer $token")
                        .header("Accept", "application/json")
                        .post(payload.toString().toRequestBody(jsonType))
                        .build()
                client.newCall(req).execute().use { r ->
                    val body = r.body?.string().orEmpty()
                    val j = try { parse(body) } catch (_: Exception) { JSONObject() }
                    Triple(r.code, j, body)
                }
            }.getOrElse {
                return NickChangeResult.Fail("network", "network")
            }
        val (code, j, _) = res
        if (code in 200..299) {
            val u = j.optJSONObject("user") ?: return NickChangeResult.Fail("server", "server")
            return NickChangeResult.Ok(parseMeProfile(u))
        }
        val err = j.optString("error", "server")
        val msg = j.optString("message", err)
        return NickChangeResult.Fail(err, msg)
    }

    private fun parseMeProfile(u: JSONObject): MeProfile =
        MeProfile(
            id = u.optInt("id"),
            nick = u.optString("nick"),
            email = u.optString("email", ""),
            displayName = u.optString("display_name", ""),
            bio = u.optString("bio", ""),
            statusText = u.optString("status_text", ""),
            statusEmoji = u.optString("status_emoji", ""),
            avatarUploadId = u.optCleanString("avatar_upload_id"),
        )

    private fun parseUserProfile(u: JSONObject): UserProfile =
        UserProfile(
            id = u.optInt("id"),
            nick = u.optString("nick", "").ifBlank { "user${u.optInt("id")}" },
            displayName = u.optString("display_name", ""),
            bio = u.optString("bio", ""),
            statusText = u.optString("status_text", ""),
            statusEmoji = u.optString("status_emoji", ""),
            avatarUploadId = u.optCleanString("avatar_upload_id"),
            lastSeenSec = u.optLong("last_seen", 0).coerceAtLeast(0),
            moderationPublic = u.optRestriction("moderation_public"),
            canReport = u.optBoolean("can_report", true),
            canBlock = u.optBoolean("can_block", true),
            canMessage = u.optBoolean("can_message", true),
        )

    fun toggleReaction(token: String, conversationId: Int, messageId: Long, emoji: String): Boolean {
        val j =
            authedPost(
                token,
                "/api/messages.php",
                JSONObject()
                    .put("action", "react")
                    .put("conversation_id", conversationId)
                    .put("message_id", messageId)
                    .put("emoji", emoji),
            ) ?: return false
        return j.has("toggled")
    }

    fun markRead(token: String, conversationId: Int, throughMessageId: Long) {
        authedPost(token, "/api/messages.php", JSONObject().put("action", "mark_read").put("conversation_id", conversationId).put("through_message_id", throughMessageId))
    }

    fun setTyping(token: String, conversationId: Int, typing: Boolean, recording: Boolean = false) {
        authedPost(
            token,
            "/api/presence.php",
            JSONObject()
                .put("conversation_id", conversationId)
                .put("typing", typing)
                .put("recording", recording),
        )
    }

    fun checkAppUpdate(versionCode: Int, versionName: String): org.assistix.proto.nativeapp.update.AppUpdateCheckResult {
        val path =
            "/api/app-update.php?platform=android&version_code=$versionCode&version_name=${java.net.URLEncoder.encode(versionName, "UTF-8")}"
        val origins = ProtoApiOrigin.orderedOrigins()
        var lastFail: org.assistix.proto.nativeapp.update.AppUpdateCheckResult? = null
        for (origin in origins) {
            val req =
                Request.Builder()
                    .url("$origin$path")
                    .header("Accept", "application/json")
                    .get()
                    .build()
            repeat(2) { attempt ->
                val result =
                    runCatching { executeAppUpdateCheck(req, versionCode) }.getOrElse {
                        lastHttpOk = false
                        lastFail = org.assistix.proto.nativeapp.update.AppUpdateCheckResult.Failed("network")
                        return@repeat
                    }
                if (result !is org.assistix.proto.nativeapp.update.AppUpdateCheckResult.Failed) {
                    return result
                }
                lastFail = result
                if (attempt == 0) Thread.sleep(350)
            }
        }
        return lastFail ?: org.assistix.proto.nativeapp.update.AppUpdateCheckResult.Failed("network")
    }

    private fun executeAppUpdateCheck(
        req: Request,
        installedVersionCode: Int,
    ): org.assistix.proto.nativeapp.update.AppUpdateCheckResult {
        client.newCall(req).execute().use { res ->
            lastHttpOk = res.isSuccessful
            val body = res.body?.string().orEmpty()
            if (!res.isSuccessful) {
                val kind = if (res.code >= 500) "server" else "http"
                return org.assistix.proto.nativeapp.update.AppUpdateCheckResult.Failed(kind, res.code)
            }
            val j =
                try {
                    parse(body)
                } catch (_: Exception) {
                    return org.assistix.proto.nativeapp.update.AppUpdateCheckResult.Failed("server")
                }
            if (!j.optBoolean("ok", false)) {
                return org.assistix.proto.nativeapp.update.AppUpdateCheckResult.Failed("server")
            }
            val updateAvailable = j.optBoolean("update_available", false)
            val blockApp = j.optBoolean("block_app", false)
            val serverCode = j.optInt("version_code", 0)
            val minCode = j.optInt("min_version_code", 1)
            val belowMin = installedVersionCode > 0 && installedVersionCode < minCode
            val needsUpdate =
                serverCode > installedVersionCode || belowMin
            if (!needsUpdate && !updateAvailable) {
                return org.assistix.proto.nativeapp.update.AppUpdateCheckResult.UpToDate
            }
            if (!needsUpdate && updateAvailable && !blockApp) {
                // server says update but we're already at/above — trust installed
                if (installedVersionCode >= serverCode && installedVersionCode >= minCode) {
                    return org.assistix.proto.nativeapp.update.AppUpdateCheckResult.UpToDate
                }
            }
            var apkUrl = j.optString("apk_url", "").trim()
            if (apkUrl.isBlank() || !apkUrl.contains("download-apk")) {
                apkUrl = org.assistix.proto.nativeapp.update.ProtoAppUpdateManager.CANONICAL_APK_URL
            }
            if (apkUrl.startsWith("/")) {
                apkUrl = BuildConfig.API_ORIGIN.trimEnd('/') + apkUrl
            }
            val info =
                org.assistix.proto.nativeapp.update.AppUpdateInfo(
                    versionCode = j.optInt("version_code"),
                    versionName = j.optString("version_name"),
                    apkUrl = apkUrl,
                    apkDirectUrl = apkUrl,
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
                    supportEmail =
                        j.optString("support_email").trim().ifBlank { "team@proto.su" },
                )
            if (!info.isNewerThan(installedVersionCode)) {
                return org.assistix.proto.nativeapp.update.AppUpdateCheckResult.UpToDate
            }
            return org.assistix.proto.nativeapp.update.AppUpdateCheckResult.Available(info)
        }
    }

    fun fetchClientPrefs(token: String): ClientPrefsRemote? {
        val j = authedGet(token, "/api/prefs.php") ?: return null
        if (!j.optBoolean("ok", false)) return null
        return ClientPrefsRemote(
            chatFolders = j.optString("chat_folders", ""),
            chatDrafts = j.optString("chat_drafts", ""),
            notifyMentionsOnly = j.optBoolean("notify_mentions_only", false),
            updatedAt = j.optLong("updated_at", 0),
        )
    }

    fun saveClientPrefs(
        token: String,
        chatFolders: String? = null,
        chatDrafts: String? = null,
        notifyMentionsOnly: Boolean? = null,
    ): Boolean {
        val payload = JSONObject()
        if (chatFolders != null) payload.put("chat_folders", chatFolders)
        if (chatDrafts != null) payload.put("chat_drafts", chatDrafts)
        if (notifyMentionsOnly != null) payload.put("notify_mentions_only", notifyMentionsOnly)
        val j = authedPost(token, "/api/prefs.php", payload) ?: return false
        return j.optBoolean("ok", false)
    }

    fun assistixCatalog(token: String): AssistixCatalog? {
        val j = authedGet(token, "/api/assistix.php") ?: return null
        if (!j.optBoolean("ok", false)) return null
        val models = mutableListOf<AssistixModel>()
        val arr = j.optJSONArray("models") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val m = arr.optJSONObject(i) ?: continue
            models +=
                AssistixModel(
                    key = m.optString("key", ""),
                    name = m.optString("name", ""),
                    description = m.optString("description", ""),
                    default = m.optBoolean("default", false),
                )
        }
        return AssistixCatalog(
            configured = j.optBoolean("configured", false),
            defaultModel = j.optString("default_model", "tide"),
            models = models.filter { it.key.isNotBlank() },
            rateLimit = parseAssistixRateLimit(j).also { AssistixUsageHub.apply(it) },
        )
    }

    /** Chat with PROTO AI: streaming first, plain POST fallback if the stream fails. */
    fun assistixChat(
        token: String,
        text: String,
        language: String,
        history: List<AssistixChatTurn>,
        deviceLanguage: String = java.util.Locale.getDefault().language,
        onDelta: (String) -> Unit = {},
    ): AssistixReply {
        val streamed = assistixStreamChat(token, text, language, history, deviceLanguage, onDelta)
        if (streamed.ok && streamed.text.isNotBlank()) return streamed
        val fallback =
            assistixRequest(
                token = token,
                action = "chat",
                text = text,
                history = history,
                language = language,
                deviceLanguage = deviceLanguage,
            )
        if (fallback.ok) return fallback
        val streamErr = streamed.error.orEmpty()
        return if (streamErr == "network" || streamErr.startsWith("http_")) streamed else fallback
    }

    fun assistixStreamChat(
        token: String,
        text: String,
        language: String,
        history: List<AssistixChatTurn>,
        deviceLanguage: String = java.util.Locale.getDefault().language,
        onDelta: (String) -> Unit,
    ): AssistixReply {
        val payload =
            JSONObject()
                .put("action", "chat")
                .put("model", Assistix.MODEL)
                .put("text", text)
                .put("stream", true)
                .put("language", language)
                .put("device_language", deviceLanguage.take(12))
        if (history.isNotEmpty()) {
            val hist = JSONArray()
            history.forEach { turn ->
                hist.put(JSONObject().put("role", turn.role).put("content", turn.content))
            }
            payload.put("messages", hist)
        }
        val req =
            Request.Builder()
                .url(url("/api/assistix.php"))
                .header("Authorization", "Bearer $token")
                .header("X-Proto-Session", token)
                .header("Accept", "text/event-stream, application/json")
                .post(payload.toString().toRequestBody(jsonType))
                .build()
        return try {
            streamClient.newCall(req).execute().use { res ->
                lastHttpOk = res.isSuccessful
                val body = res.body
                if (!res.isSuccessful) {
                    val errJson =
                        try {
                            JSONObject(body?.string().orEmpty())
                        } catch (_: Exception) {
                            JSONObject()
                        }
                    return AssistixReply(
                        ok = false,
                        text = "",
                        error = errJson.optString("error").ifBlank { "http_${res.code}" },
                        message = errJson.optString("message"),
                        rateLimit = parseAssistixRateLimit(errJson),
                    )
                }
                val contentType = body?.contentType()?.toString().orEmpty()
                if (contentType.contains("application/json", ignoreCase = true)) {
                    val j = try { JSONObject(body?.string().orEmpty()) } catch (_: Exception) { JSONObject() }
                    if (j.optBoolean("ok", false)) {
                        val out = j.optString("text", "").trim()
                        if (out.isNotBlank()) {
                            return AssistixReply(
                                ok = true,
                                text = AssistixText.forChat(out),
                                model = Assistix.MODEL,
                                rateLimit = parseAssistixRateLimit(j),
                            )
                        }
                    }
                    return AssistixReply(
                        ok = false,
                        text = "",
                        error = j.optString("error", "error"),
                        message = j.optString("message"),
                        rateLimit = parseAssistixRateLimit(j),
                    )
                }
                val reader =
                    BufferedReader(
                        InputStreamReader(body?.byteStream() ?: return AssistixReply(ok = false, text = "", error = "network", message = "")),
                    )
                var accumulated = StringBuilder()
                var finalText = ""
                var streamError: AssistixReply? = null
                var streamRate: AssistixRateLimit? = null

                fun handleSsePayload(raw: String) {
                    val payload = raw.trim()
                    if (payload.isEmpty() || payload == "[DONE]") return
                    val j =
                        try {
                            JSONObject(payload)
                        } catch (_: Exception) {
                            return
                        }
                    if (j.has("error")) {
                        streamError =
                            AssistixReply(
                                ok = false,
                                text = "",
                                error = j.optString("error", "error"),
                                message = j.optString("message", ""),
                                rateLimit = parseAssistixRateLimit(j),
                            )
                        return
                    }
                    val delta = j.optString("delta", "")
                    if (delta.isNotEmpty()) {
                        accumulated.append(delta)
                        onDelta(delta)
                    }
                    if (j.optBoolean("done", false)) {
                        val doneText = j.optString("text", "").trim()
                        if (doneText.isNotBlank()) finalText = doneText
                        parseAssistixRateLimit(j)?.let { streamRate = it }
                    }
                }

                var line: String?
                while (true) {
                    line = reader.readLine() ?: break
                    if (line.startsWith("data:")) {
                        handleSsePayload(line.removePrefix("data:"))
                    }
                }

                streamError?.let { return stampAssistix(it) }

                val resolved =
                    when {
                        finalText.isNotBlank() -> finalText
                        accumulated.isNotEmpty() -> accumulated.toString()
                        else -> ""
                    }
                if (resolved.isBlank()) {
                    return stampAssistix(
                        AssistixReply(ok = false, text = "", error = "empty_response", message = "", rateLimit = streamRate),
                    )
                }
                return stampAssistix(
                    AssistixReply(
                        ok = true,
                        text = AssistixText.forChat(resolved),
                        model = Assistix.MODEL,
                        rateLimit = streamRate,
                    ),
                )
            }
        } catch (_: Exception) {
            lastHttpOk = false
            AssistixReply(ok = false, text = "", error = "network", message = "")
        }
    }

    private fun stampAssistix(reply: AssistixReply): AssistixReply {
        AssistixUsageHub.applyFromReply(reply)
        return reply
    }

    fun assistixRequest(
        token: String,
        action: String,
        text: String = "",
        style: String = "neutral",
        history: List<AssistixChatTurn> = emptyList(),
        previewLines: List<String> = emptyList(),
        chatContext: String = "",
        searchHits: List<String> = emptyList(),
        language: String = "en",
        targetLanguage: String = language,
        deviceLanguage: String = java.util.Locale.getDefault().language,
    ): AssistixReply {
        val payload =
            JSONObject()
                .put("action", action)
                .put("model", Assistix.MODEL)
                .put("text", text)
                .put("style", style)
                .put("language", language)
                .put("target_language", targetLanguage)
                .put("device_language", deviceLanguage.take(12))
        if (history.isNotEmpty()) {
            val hist = JSONArray()
            history.forEach { turn ->
                hist.put(JSONObject().put("role", turn.role).put("content", turn.content))
            }
            payload.put("messages", hist)
        }
        if (previewLines.isNotEmpty()) {
            val prev = JSONArray()
            previewLines.forEach { prev.put(it) }
            payload.put("messages_preview", prev)
        }
        if (chatContext.isNotBlank()) {
            payload.put("chat_context", chatContext.take(12_000))
        }
        if (searchHits.isNotEmpty()) {
            val hits = JSONArray()
            searchHits.forEach { hits.put(it) }
            payload.put("search_hits", hits)
        }
        val j =
            try {
                authedPost(token, "/api/assistix.php", payload)
            } catch (_: Exception) {
                null
            } ?: return AssistixReply(ok = false, text = "", error = "network", message = "")
        if (j.optBoolean("ok", false)) {
            val replyLines = mutableListOf<String>()
            j.optJSONArray("replies")?.let { arr ->
                for (i in 0 until arr.length()) {
                    arr.optString(i, "").trim().takeIf { it.isNotEmpty() }?.let { replyLines.add(it) }
                }
            }
            val out = j.optString("text", "").trim().ifBlank { replyLines.joinToString("\n") }
            if (out.isNotBlank() || replyLines.isNotEmpty()) {
                val cleaned =
                    when (action) {
                        "chat" -> AssistixText.forChat(out)
                        "fix_text", "rewrite_style", "expand_draft" -> AssistixText.forComposer(out, text)
                        else -> out
                    }
                return stampAssistix(
                    AssistixReply(
                        ok = true,
                        text = cleaned,
                        model = Assistix.MODEL,
                        rateLimit = parseAssistixRateLimit(j),
                        totalTokens = j.optInt("total_tokens", 0),
                        replies = replyLines.ifEmpty { parseAssistixReplyLines(cleaned) },
                    ),
                )
            }
        }
        val err = j.optString("error", "").ifBlank { "error" }
        val msg = j.optString("message", "").ifBlank { j.optString("error", "") }
        return stampAssistix(
            AssistixReply(
                ok = false,
                text = "",
                error = err,
                message = msg,
                rateLimit = parseAssistixRateLimit(j),
                totalTokens = j.optInt("total_tokens", 0),
            ),
        )
    }

    private fun parseAssistixReplyLines(text: String): List<String> =
        text
            .lineSequence()
            .map { it.trim().replace(Regex("""^[\d\.\)\-•]+\s*"""), "") }
            .filter { it.isNotBlank() && it.length <= 120 }
            .take(3)
            .toList()

    fun registerPush(token: String, pushToken: String) {
        authedPost(token, "/api/push.php", JSONObject().put("action", "register").put("token", pushToken).put("platform", "android"))
    }

    fun sendMessage(
        token: String,
        conversationId: Int,
        body: String,
        isE2e: Boolean = false,
        mediaUploadId: String? = null,
        forward: ForwardMeta? = null,
        replyToId: Long? = null,
    ): SendMessageResult {
        val payload = JSONObject().put("conversation_id", conversationId).put("body", body).put("is_e2e", isE2e)
        if (!mediaUploadId.isNullOrBlank()) payload.put("media_upload_id", mediaUploadId)
        if (replyToId != null && replyToId > 0) payload.put("reply_to_id", replyToId)
        if (forward != null) {
            payload.put(
                "forward",
                JSONObject()
                    .put("from_label", forward.fromLabel)
                    .put("body_snippet", forward.bodySnippet)
                    .put("from_user_id", forward.fromUserId)
                    .put("original_message_id", forward.originalMessageId),
            )
        }
        val j = authedPost(token, "/api/messages.php", payload) ?: return SendMessageResult(false, null)
        val msg = j.optJSONObject("message")
        return SendMessageResult(msg != null || j.optBoolean("ok", false), msg?.optLong("id")?.takeIf { it > 0 })
    }

    fun publishChannelPost(
        token: String,
        conversationId: Int,
        text: String,
        imageUploadId: String? = null,
    ): Boolean {
        val body =
            ChannelPostMeta(
                text = text.trim(),
                imageUploadId = imageUploadId?.trim()?.takeIf { it.isNotEmpty() },
            ).toJsonBody()
        if (body.isBlank()) return false
        return sendMessage(token, conversationId, body, false, imageUploadId?.trim()?.takeIf { it.isNotEmpty() }).ok
    }

    fun editMessage(token: String, conversationId: Int, messageId: Long, body: String): Boolean {
        val j =
            authedPost(
                token,
                "/api/messages.php",
                JSONObject().put("action", "edit_message").put("conversation_id", conversationId).put("message_id", messageId).put("body", body),
            ) ?: return false
        return j.optJSONObject("message") != null || j.optBoolean("ok", false)
    }

    fun deleteMessage(
        token: String,
        conversationId: Int,
        messageId: Long,
        scope: String = "all",
    ): Boolean {
        val j =
            authedPost(
                token,
                "/api/messages.php",
                JSONObject()
                    .put("action", "delete_message")
                    .put("conversation_id", conversationId)
                    .put("message_id", messageId)
                    .put("scope", scope),
            ) ?: return false
        return j.optBoolean("deleted", false) || j.optBoolean("ok", false)
    }

    fun exportMyData(token: String): JSONObject? =
        authedPost(token, "/api/me.php", JSONObject().put("action", "export_data"))

    fun deleteMyAccount(token: String, password: String): Boolean {
        val j =
            authedPost(
                token,
                "/api/me.php",
                JSONObject().put("action", "delete_account").put("password", password),
            ) ?: return false
        return j.optBoolean("deleted", false) || j.optBoolean("ok", false)
    }

    fun uploadFile(token: String, file: File, mime: String): String? {
        if (file.length() > ProtoMediaCompressor.MAX_UPLOAD_BYTES) {
            lastHttpOk = false
            return null
        }
        val body =
            MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", file.name, file.asRequestBody(mime.toMediaType()))
                .build()
        val req =
            Request.Builder()
                .url(url("/api/upload.php"))
                .header("Authorization", "Bearer $token")
                .header("X-Proto-Session", token)
                .post(body)
                .build()
        return try {
            client.newCall(req).execute().use { res ->
                lastHttpOk = res.isSuccessful
                val j = parse(res.body?.string() ?: "")
                if (!j.optBoolean("ok", res.isSuccessful)) null
                else j.optJSONObject("upload")?.optString("id")?.takeIf { it.isNotBlank() }
            }
        } catch (_: Exception) {
            lastHttpOk = false
            null
        }
    }

    fun transcribeGet(token: String, uploadId: String): JSONObject? =
        authedGet(token, "/api/transcribe.php?upload_id=$uploadId")

    fun transcribeSave(
        token: String,
        uploadId: String,
        conversationId: Int,
        text: String,
        model: String,
        lang: String = "auto",
    ): JSONObject? =
        authedPost(
            token,
            "/api/transcribe.php",
            JSONObject()
                .put("action", "save")
                .put("upload_id", uploadId)
                .put("conversation_id", conversationId)
                .put("text", text)
                .put("lang", lang)
                .put("model", model),
        )

    fun transcribeEnqueue(token: String, uploadId: String, conversationId: Int): JSONObject? {
        val payload =
            JSONObject()
                .put("action", "enqueue")
                .put("upload_id", uploadId)
                .put("conversation_id", conversationId)
        val req =
            Request.Builder()
                .url(url("/api/transcribe.php"))
                .header("Authorization", "Bearer $token")
                .header("X-Proto-Session", token)
                .post(payload.toString().toRequestBody(jsonType))
                .build()
        return try {
            sttClient.newCall(req).execute().use { res ->
                lastHttpOk = res.isSuccessful
                val raw = res.body?.string() ?: ""
                if (raw.isBlank()) return@use null
                parse(raw)
            }
        } catch (_: Exception) {
            lastHttpOk = false
            null
        }
    }

    fun downloadMediaResult(token: String, uploadId: String, dest: File): MediaDownloadResult {
        val req =
            Request.Builder()
                .url(mediaUrl(uploadId))
                .header("Authorization", "Bearer $token")
                .header("X-Proto-Session", token)
                .get()
                .build()
        return try {
            client.newCall(req).execute().use { res ->
                when {
                    res.code == 410 -> MediaDownloadResult.ExpiredRelay
                    !res.isSuccessful -> MediaDownloadResult.Failed
                    else -> {
                        dest.parentFile?.mkdirs()
                        dest.outputStream().use { out -> res.body?.byteStream()?.copyTo(out) }
                        if (dest.exists() && dest.length() > 0L) MediaDownloadResult.Ok else MediaDownloadResult.Failed
                    }
                }
            }
        } catch (_: Exception) {
            MediaDownloadResult.Failed
        }
    }

    fun downloadMedia(token: String, uploadId: String, dest: File): Boolean =
        downloadMediaResult(token, uploadId, dest) == MediaDownloadResult.Ok

    fun ackMediaRelay(token: String, uploadId: String): Boolean {
        val j =
            authedPost(
                token,
                "/api/media.php",
                JSONObject().put("action", "ack").put("upload_id", uploadId),
            ) ?: return false
        return j.optBoolean("ok", false)
    }

    fun requestMediaRelay(token: String, uploadId: String, conversationId: Int): Boolean {
        val j =
            authedPost(
                token,
                "/api/media-relay.php",
                JSONObject()
                    .put("action", "request")
                    .put("upload_id", uploadId)
                    .put("conversation_id", conversationId),
            ) ?: return false
        return j.optBoolean("ok", false)
    }

    fun refreshMediaRelay(
        token: String,
        uploadId: String,
        conversationId: Int,
        file: File,
        mime: String,
    ): Boolean {
        val body =
            MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("action", "refresh")
                .addFormDataPart("upload_id", uploadId)
                .addFormDataPart("conversation_id", conversationId.toString())
                .addFormDataPart("file", file.name, file.asRequestBody(mime.toMediaType()))
                .build()
        val req =
            Request.Builder()
                .url(url("/api/media-relay.php"))
                .header("Authorization", "Bearer $token")
                .header("X-Proto-Session", token)
                .post(body)
                .build()
        return try {
            client.newCall(req).execute().use { res ->
                lastHttpOk = res.isSuccessful
                val j = parse(res.body?.string() ?: "")
                res.isSuccessful && j.optBoolean("ok", false)
            }
        } catch (_: Exception) {
            lastHttpOk = false
            false
        }
    }

    fun cellsRegisterBlob(
        token: String,
        blobId: String,
        conversationId: Int,
        mime: String,
        plainSize: Int,
        cipherSize: Int,
        shardCount: Int,
        cipherHash: String,
        keyB64: String,
        shardHashes: List<String>,
    ): Boolean {
        val arr = JSONArray()
        shardHashes.forEach { arr.put(it) }
        val j =
            authedPost(
                token,
                "/api/cells.php",
                JSONObject()
                    .put("action", "register_blob")
                    .put("blob_id", blobId)
                    .put("conversation_id", conversationId)
                    .put("mime", mime)
                    .put("plain_size", plainSize)
                    .put("cipher_size", cipherSize)
                    .put("shard_count", shardCount)
                    .put("cipher_hash", cipherHash)
                    .put("key_b64", keyB64)
                    .put("shard_hashes", arr),
            ) ?: return false
        return j.optBoolean("ok", false)
    }

    fun cellsPushShard(
        token: String,
        blobId: String,
        shardIndex: Int,
        shardHash: String,
        data: ByteArray,
    ): Boolean {
        val tmp = File.createTempFile("proto_cell_", ".shard")
        return try {
            tmp.writeBytes(data)
            val body =
                MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("action", "push_shard")
                    .addFormDataPart("blob_id", blobId)
                    .addFormDataPart("shard_index", shardIndex.toString())
                    .addFormDataPart("shard_hash", shardHash)
                    .addFormDataPart(
                        "shard",
                        "s$shardIndex.bin",
                        tmp.asRequestBody("application/octet-stream".toMediaType()),
                    )
                    .build()
            val req =
                Request.Builder()
                    .url(url("/api/cells.php"))
                    .header("Authorization", "Bearer $token")
                    .header("X-Proto-Session", token)
                    .post(body)
                    .build()
            client.newCall(req).execute().use { res ->
                lastHttpOk = res.isSuccessful
                val j = parse(res.body?.string() ?: "")
                res.isSuccessful && j.optBoolean("ok", false)
            }
        } catch (_: Exception) {
            lastHttpOk = false
            false
        } finally {
            tmp.delete()
        }
    }

    fun cellsManifest(token: String, blobId: String): JSONObject? =
        authedGet(token, "/api/cells.php?action=manifest&blob_id=$blobId")

    fun cellsFetchShard(token: String, blobId: String, shardIndex: Int): ByteArray? {
        val req =
            Request.Builder()
                .url(url("/api/cells.php?action=fetch_shard&blob_id=$blobId&shard_index=$shardIndex"))
                .header("Authorization", "Bearer $token")
                .header("X-Proto-Session", token)
                .get()
                .build()
        return try {
            client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return@use null
                res.body?.bytes()
            }
        } catch (_: Exception) {
            null
        }
    }

    fun cellsAckShard(token: String, blobId: String, shardIndex: Int): Boolean {
        val j =
            authedPost(
                token,
                "/api/cells.php",
                JSONObject()
                    .put("action", "ack_shard")
                    .put("blob_id", blobId)
                    .put("shard_index", shardIndex),
            ) ?: return false
        return j.optBoolean("ok", false)
    }

    fun cellsMyStats(token: String): JSONObject? =
        authedGet(token, "/api/cells.php?action=my_stats")

    fun reportCrash(token: String, stack: String, versionCode: Int, versionName: String): Boolean {
        val j =
            authedPost(
                token,
                "/api/crash-report.php",
                JSONObject()
                    .put("stack", stack.take(65536))
                    .put("version_code", versionCode)
                    .put("version_name", versionName),
            ) ?: return false
        return j.optBoolean("ok", false)
    }

    fun cellsMyHolds(token: String): JSONArray? =
        authedGet(token, "/api/cells.php?action=my_holds")?.optJSONArray("holds")

    fun cellsVolunteer(token: String, enabled: Boolean, quotaBytes: Long): Boolean {
        val j =
            authedPost(
                token,
                "/api/cells.php",
                JSONObject()
                    .put("action", "volunteer")
                    .put("enabled", if (enabled) 1 else 0)
                    .put("quota_bytes", quotaBytes),
            ) ?: return false
        return j.optBoolean("ok", false)
    }

    fun cellsRepairRequest(
        token: String,
        blobId: String,
        conversationId: Int,
        missing: List<Int>,
    ): Boolean {
        val arr = JSONArray()
        missing.forEach { arr.put(it) }
        val j =
            authedPost(
                token,
                "/api/cells.php",
                JSONObject()
                    .put("action", "repair_request")
                    .put("blob_id", blobId)
                    .put("conversation_id", conversationId)
                    .put("missing_indices", arr),
            ) ?: return false
        return j.optBoolean("ok", false)
    }

    fun cellsHeartbeat(token: String, holds: JSONArray): Boolean {
        val j =
            authedPost(
                token,
                "/api/cells.php",
                JSONObject().put("action", "heartbeat").put("holds", holds),
            ) ?: return false
        return j.optBoolean("ok", false)
    }

    fun rtcConfig(token: String): List<RtcIceServer> {
        fun finish(servers: List<RtcIceServer>): List<RtcIceServer> {
            val merged = ProtoCallConfig.mergeIceServers(servers)
            appContext?.let { ProtoCallIceCache.save(it, merged) }
            return merged
        }
        parseRtcIceJson(authedGet(token, "/api/rtc-config.php"))?.let { return finish(it) }
        for (origin in ProtoApiOrigin.orderedOrigins()) {
            parseRtcIceJson(fetchPublicRtcJson(ProtoApiOrigin.url("/api/rtc-config.php", origin)))?.let {
                return finish(it)
            }
        }
        val host = BuildConfig.TURN_HOST.trim()
        if (host.isNotBlank()) {
            parseRtcIceJson(fetchPublicRtcJson("http://$host/api/rtc-config.php"))?.let { return finish(it) }
            parseRtcIceJson(fetchPublicRtcJson("https://$host/api/rtc-config.php"))?.let { return finish(it) }
        }
        appContext?.let { ctx ->
            ProtoCallIceCache.load(ctx)?.let { cached -> return finish(cached) }
        }
        return finish(ProtoCallConfig.fallbackIceServers())
    }

    private fun fetchPublicRtcJson(url: String): JSONObject? {
        val req = Request.Builder().url(url).get().build()
        return try {
            client.newCall(req).execute().use { res ->
                val raw = res.body?.string() ?: ""
                if (!res.isSuccessful || ProtoApiOrigin.looksLikeHtml(raw)) return@use null
                parse(raw)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseRtcIceJson(j: JSONObject?): List<RtcIceServer>? {
        if (j == null || !j.optBoolean("ok", true)) return null
        val arr = j.optJSONArray("iceServers") ?: return null
        val parsed =
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val urls = o.optJSONArray("urls") ?: return@mapNotNull null
                val list = (0 until urls.length()).mapNotNull { u -> urls.optString(u).takeIf { it.isNotBlank() } }
                if (list.isEmpty()) return@mapNotNull null
                RtcIceServer(
                    list,
                    o.optString("username", "").ifBlank { null },
                    o.optString("credential", "").ifBlank { null },
                )
            }
        return parsed.takeIf { it.isNotEmpty() }
    }

    fun webrtcCursor(token: String, conversationId: Int): Long = authedGet(token, "/api/webrtc.php?conversation_id=$conversationId&cursor=1")?.optLong("max_id", 0) ?: 0

    fun webrtcPoll(token: String, conversationId: Int, since: Long): List<RtcSignal> {
        val arr = authedGet(token, "/api/webrtc.php?conversation_id=$conversationId&since=$since")?.optJSONArray("signals") ?: JSONArray()
        return (0 until arr.length()).mapNotNull { i ->
            val s = arr.optJSONObject(i) ?: return@mapNotNull null
            RtcSignal(
                s.optLong("id"),
                s.optInt("sender_id"),
                s.optString("kind"),
                s.optString("payload"),
                s.optLong("created_at", 0L),
            )
        }
    }

    fun webrtcPost(token: String, conversationId: Int, kind: String, payload: String): Long? {
        val j = authedPost(token, "/api/webrtc.php", JSONObject().put("conversation_id", conversationId).put("kind", kind).put("payload", payload)) ?: return null
        return j.optLong("id", 0).takeIf { it > 0 }
    }

    fun startDm(token: String, peerId: Int): Int? = authedPost(token, "/api/conversations.php", JSONObject().put("peer_id", peerId))?.optJSONObject("conversation")?.optInt("id")

    fun createGroup(token: String, title: String, memberIds: List<Int>): Int? {
        val arr = JSONArray()
        memberIds.filter { it > 0 }.forEach { arr.put(it) }
        val j =
            authedPost(
                token,
                "/api/conversations.php",
                JSONObject().put("action", "create_group").put("title", title.trim()).put("member_ids", arr),
            ) ?: return null
        return j.optJSONObject("conversation")?.optInt("id")
    }

    fun createChannel(token: String, title: String, nick: String, description: String = ""): Int? {
        val j =
            authedPost(
                token,
                "/api/channels.php",
                JSONObject()
                    .put("action", "create")
                    .put("title", title.trim())
                    .put("nick", nick.trim().lowercase())
                    .put("description", description.trim()),
            ) ?: return null
        return j.optJSONObject("channel")?.optInt("conversation_id")
    }

    fun searchChannels(token: String, query: String): List<ChannelHit> {
        val enc = java.net.URLEncoder.encode(query.trim(), Charsets.UTF_8.name())
        val arr = authedGet(token, "/api/channels.php?q=$enc")?.optJSONArray("channels") ?: JSONArray()
        return (0 until arr.length()).mapNotNull { i ->
            parseChannelHit(arr.optJSONObject(i) ?: return@mapNotNull null)
        }
    }

    fun globalSearch(token: String, query: String): GlobalSearchResult {
        val enc = java.net.URLEncoder.encode(query.trim(), Charsets.UTF_8.name())
        val j = authedGet(token, "/api/search.php?q=$enc&limit=30") ?: return GlobalSearchResult.EMPTY
        val userArr = j.optJSONArray("users") ?: JSONArray()
        val users =
            (0 until userArr.length()).mapNotNull { i ->
                val u = userArr.optJSONObject(i) ?: return@mapNotNull null
                UserHit(
                    u.optInt("id"),
                    u.optString("nick"),
                    u.optString("display_name", ""),
                    u.optString("status_emoji", ""),
                    u.optCleanString("avatar_upload_id"),
                )
            }
        val channels =
            (0 until (j.optJSONArray("channels")?.length() ?: 0)).mapNotNull { i ->
                parseChannelHit(j.optJSONArray("channels")?.optJSONObject(i) ?: return@mapNotNull null)
            }
        val messages =
            (0 until (j.optJSONArray("messages")?.length() ?: 0)).mapNotNull { i ->
                val m = j.optJSONArray("messages")?.optJSONObject(i) ?: return@mapNotNull null
                MessageSearchHit(
                    id = m.optLong("id"),
                    conversationId = m.optInt("conversation_id"),
                    bodySnippet = m.optString("body_snippet", ""),
                    conversationTitle = m.optString("conversation_title", ""),
                    conversationKind = m.optString("conversation_kind", "dm"),
                    createdAt = m.optLong("created_at"),
                )
            }
        return GlobalSearchResult(users, channels, messages)
    }

    fun starMessage(token: String, conversationId: Int, messageId: Long): Boolean =
        authedPost(
            token,
            "/api/messages.php",
            JSONObject()
                .put("action", "star_message")
                .put("conversation_id", conversationId)
                .put("message_id", messageId),
        )?.optBoolean("ok", false) == true

    fun unstarMessage(token: String, messageId: Long): Boolean =
        authedPost(
            token,
            "/api/messages.php",
            JSONObject().put("action", "unstar_message").put("message_id", messageId),
        )?.optBoolean("ok", false) == true

    fun channelByNick(token: String, nick: String): ChannelHit? {
        val enc = java.net.URLEncoder.encode(nick.trim().removePrefix("@"), Charsets.UTF_8.name())
        val j = authedGet(token, "/api/channels.php?nick=$enc") ?: return null
        return parseChannelHit(j.optJSONObject("channel") ?: return null)
    }

    fun channelByConversation(token: String, conversationId: Int): ChannelHit? {
        val j = authedGet(token, "/api/channels.php?conversation_id=$conversationId") ?: return null
        return parseChannelHit(j.optJSONObject("channel") ?: return null)
    }

    fun subscribeChannel(token: String, conversationId: Int): Boolean =
        authedPost(
            token,
            "/api/channels.php",
            JSONObject().put("action", "subscribe").put("conversation_id", conversationId),
        )?.optBoolean("ok", false) == true

    fun unsubscribeChannel(token: String, conversationId: Int): Boolean =
        authedPost(
            token,
            "/api/channels.php",
            JSONObject().put("action", "unsubscribe").put("conversation_id", conversationId),
        )?.optBoolean("ok", false) == true

    fun updateChannel(
        token: String,
        conversationId: Int,
        title: String,
        description: String,
        avatarUploadId: String? = null,
    ): ChannelHit? {
        val payload =
            JSONObject()
                .put("action", "update")
                .put("conversation_id", conversationId)
                .put("title", title.trim())
                .put("description", description.trim())
        if (avatarUploadId != null) {
            payload.put("avatar_upload_id", avatarUploadId)
        }
        val j = authedPost(token, "/api/channels.php", payload) ?: return null
        return parseChannelHit(j.optJSONObject("channel") ?: return null)
    }

    fun fetchChannelFeed(
        token: String,
        conversationId: Int,
        lang: String = "",
        before: Long = 0,
        searchQuery: String = "",
        limit: Int = 30,
        myUserId: Int = 0,
    ): ChannelFeedResponse? {
        val langQ = if (lang.isNotBlank()) "&lang=${java.net.URLEncoder.encode(lang, Charsets.UTF_8.name())}" else ""
        val searchQ =
            if (searchQuery.isNotBlank()) {
                "&q=${java.net.URLEncoder.encode(searchQuery.trim(), Charsets.UTF_8.name())}"
            } else {
                ""
            }
        val beforeQ = if (before > 0) "&before=$before" else ""
        val j =
            authedGet(
                token,
                "/api/channel-feed.php?conversation_id=$conversationId&limit=$limit$langQ$searchQ$beforeQ",
            ) ?: return null
        val ch = parseChannelHit(j.optJSONObject("channel") ?: return null)
        val posts = parseChannelFeedPosts(j.optJSONArray("posts"), myUserId)
        return ChannelFeedResponse(
            channel = ch,
            posts = posts,
            hasMore = j.optBoolean("has_more", false),
            nextBefore = j.optLong("next_before", 0),
        )
    }

    private fun parseChannelFeedPosts(arr: JSONArray?, myUserId: Int): List<ChannelFeedPost> {
        val posts = mutableListOf<ChannelFeedPost>()
        val a = arr ?: return posts
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            val bodyRaw = o.optString("body", "")
            val displayText = jsonOptCleanString(o, "display_text")
            val pollMeta = PollMeta.fromJson(bodyRaw)
            var postMeta = if (pollMeta == null) ChannelPostMeta.fromDisplayText(displayText, bodyRaw) else null
            val imgUp = jsonOptCleanString(o, "post_image_upload_id").ifBlank { postMeta?.imageUploadId }
            val imgUrl = jsonOptCleanString(o, "post_image_url").ifBlank { postMeta?.imageUrl }
            if (postMeta != null && (imgUp != null || imgUrl != null)) {
                postMeta = postMeta.copy(imageUploadId = imgUp ?: postMeta.imageUploadId, imageUrl = imgUrl ?: postMeta.imageUrl)
            }
            if (postMeta == null && pollMeta == null) continue
            posts.add(
                ChannelFeedPost(
                    id = o.optLong("id"),
                    bodyRaw = bodyRaw,
                    displayText = displayText.ifBlank { postMeta?.text ?: pollMeta?.question.orEmpty() },
                    postMeta = postMeta,
                    pollMeta = pollMeta,
                    createdAt = o.optLong("created_at") * 1000L,
                    viewCount = o.optInt("view_count", 0),
                    translation = jsonOptCleanString(o, "translation").ifBlank { null },
                    reactions = parseReactions(o.optJSONArray("reactions"), myUserId),
                ),
            )
        }
        return posts
    }

    fun recordChannelPostViews(token: String, conversationId: Int, messageIds: List<Long>): Boolean {
        if (messageIds.isEmpty()) return true
        val arr = JSONArray()
        messageIds.filter { it > 0 }.forEach { arr.put(it) }
        return authedPost(
            token,
            "/api/channel-feed.php",
            JSONObject()
                .put("action", "view_posts")
                .put("conversation_id", conversationId)
                .put("message_ids", arr),
        )?.optBoolean("ok", false) == true
    }

    private fun parseChannelHit(o: JSONObject): ChannelHit =
        ChannelHit(
            conversationId = o.optInt("conversation_id", 0),
            title = o.optString("title", ""),
            nick = o.optString("nick", ""),
            description = o.optString("description", ""),
            avatarUploadId = o.optCleanString("avatar_upload_id"),
            verified = o.optBoolean("verified", false),
            subscriberCount = o.optInt("subscriber_count", 0),
            postCount = o.optInt("post_count", 0),
            pollCount = o.optInt("poll_count", 0),
            subscribed = o.optBoolean("subscribed", false),
            canPost = o.optBoolean("can_post", false),
            publicUrl = o.optString("public_url", ""),
            openCount = o.optInt("open_count", 0),
        )

    fun groupDetail(token: String, conversationId: Int): GroupDetail? {
        val j = authedGet(token, "/api/conversations.php?group_detail=$conversationId") ?: return null
        val members = mutableListOf<GroupMember>()
        val arr = j.optJSONArray("members") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val row = arr.optJSONObject(i) ?: continue
            val u = row.optJSONObject("user") ?: continue
            members.add(
                GroupMember(
                    user =
                        UserHit(
                            u.optInt("id"),
                            u.optString("nick"),
                            u.optString("display_name", ""),
                            u.optString("status_emoji", ""),
                            u.optCleanString("avatar_upload_id"),
                        ),
                    role = row.optString("role", "member"),
                    joinedAt = row.optLong("joined_at") * 1000L,
                ),
            )
        }
        return GroupDetail(
            conversationId = j.optInt("conversation_id"),
            title = j.optString("title", ""),
            ownerId = j.optInt("owner_id"),
            members = members,
        )
    }

    fun groupRename(token: String, conversationId: Int, title: String): Boolean =
        authedPost(
            token,
            "/api/conversations.php",
            JSONObject().put("action", "group_rename").put("conversation_id", conversationId).put("title", title.trim()),
        )?.optBoolean("ok", false) == true

    fun groupAddMembers(token: String, conversationId: Int, userIds: List<Int>): Int {
        val arr = JSONArray()
        userIds.filter { it > 0 }.forEach { arr.put(it) }
        return authedPost(
            token,
            "/api/conversations.php",
            JSONObject().put("action", "group_add_members").put("conversation_id", conversationId).put("user_ids", arr),
        )?.optInt("added", 0) ?: 0
    }

    fun groupSetRole(token: String, conversationId: Int, userId: Int, role: String): Boolean =
        authedPost(
            token,
            "/api/conversations.php",
            JSONObject()
                .put("action", "group_set_role")
                .put("conversation_id", conversationId)
                .put("user_id", userId)
                .put("role", role),
        )?.optBoolean("ok", false) == true

    fun groupRemoveMember(token: String, conversationId: Int, userId: Int): Boolean {
        val j =
            authedPost(
                token,
                "/api/conversations.php",
                JSONObject().put("action", "group_remove_member").put("conversation_id", conversationId).put("user_id", userId),
            ) ?: return false
        return j.optBoolean("removed", false) || j.optBoolean("left", false)
    }

    private fun defaultIce(): List<RtcIceServer> = ProtoCallConfig.fallbackIceServers()

    private fun peerDisplay(peer: JSONObject): String {
        return resolveDisplayName(peer.optString("display_name", ""), peer.optString("nick", "?"))
    }

    fun createPublicLink(token: String, kind: String, conversationId: Int = 0): PublicLinkResult? {
        val payload =
            JSONObject().apply {
                put("action", "create")
                put("kind", kind)
                if (conversationId > 0) put("conversation_id", conversationId)
            }
        val j = authedPost(token, "/api/links.php", payload) ?: return null
        if (!j.optBoolean("ok", false)) return null
        return PublicLinkResult(
            code = j.optString("code", ""),
            url = j.optString("url", ""),
            kind = j.optString("kind", kind),
            conversationId = j.optInt("conversation_id", conversationId),
        )
    }

    fun joinPublicLink(token: String, code: String): JoinLinkResult? {
        val j =
            authedPost(
                token,
                "/api/links.php",
                JSONObject().put("action", "join").put("code", code),
            ) ?: return null
        if (!j.optBoolean("ok", false)) return null
        return JoinLinkResult(
            conversationId = j.optInt("conversation_id", 0),
            kind = j.optString("kind", "dm"),
            peerUserId = j.optInt("peer_user_id", 0),
        )
    }

    fun resolvePublicLink(code: String): PublicLinkResolve? {
        val j =
            try {
                val req =
                    Request.Builder()
                        .url(url("/api/links.php?code=${java.net.URLEncoder.encode(code, "UTF-8")}"))
                        .get()
                        .build()
                client.newCall(req).execute().use { res ->
                    if (!res.isSuccessful) return null
                    parse(res.body?.string() ?: "")
                }
            } catch (_: Exception) {
                null
            } ?: return null
        if (!j.optBoolean("ok", false)) return null
        val user = j.optJSONObject("user")
        val conv = j.optJSONObject("conversation")
        return PublicLinkResolve(
            code = j.optString("code", code),
            kind = j.optString("kind", ""),
            url = j.optString("url", ""),
            userNick = user?.optString("nick", "") ?: "",
            userDisplayName = user?.optString("display_name", "") ?: "",
            conversationId = conv?.optInt("id", 0) ?: 0,
            conversationTitle = conv?.optString("title", "") ?: "",
            conversationKind = conv?.optString("kind", "") ?: "",
        )
    }

    fun fetchPinnedMessage(token: String, conversationId: Int): PinnedMessageInfo? {
        val j = authedGet(token, "/api/pins.php?conversation_id=$conversationId") ?: return null
        if (!j.optBoolean("ok", false)) return null
        val p = j.optJSONObject("pinned") ?: return null
        val mid = p.optLong("message_id", 0)
        if (mid <= 0) return null
        return PinnedMessageInfo(
            messageId = mid,
            preview = p.optString("preview", ""),
            senderId = p.optInt("sender_id", 0),
        )
    }

    fun pinMessage(token: String, conversationId: Int, messageId: Long): Boolean {
        val j =
            authedPost(
                token,
                "/api/pins.php",
                JSONObject()
                    .put("action", "pin")
                    .put("conversation_id", conversationId)
                    .put("message_id", messageId),
            ) ?: return false
        return j.optBoolean("ok", false)
    }

    fun unpinMessage(token: String, conversationId: Int): Boolean {
        val j =
            authedPost(
                token,
                "/api/pins.php",
                JSONObject().put("action", "unpin").put("conversation_id", conversationId),
            ) ?: return false
        return j.optBoolean("ok", false)
    }
}

data class SendMessageResult(val ok: Boolean, val messageId: Long?)
data class RtcIceServer(val urls: List<String>, val username: String?, val credential: String?)
data class RtcSignal(
    val id: Long,
    val senderId: Int,
    val kind: String,
    val payload: String,
    val createdAt: Long = 0L,
)

data class MeProfile(
    val id: Int,
    val nick: String,
    val email: String,
    val displayName: String,
    val bio: String,
    val statusText: String,
    val statusEmoji: String = "",
    val avatarUploadId: String? = null,
)

data class UserProfile(
    val id: Int,
    val nick: String,
    val displayName: String,
    val bio: String,
    val statusText: String,
    val statusEmoji: String = "",
    val avatarUploadId: String?,
    val lastSeenSec: Long,
    val moderationPublic: AccountRestriction? = null,
    val canReport: Boolean = true,
    val canBlock: Boolean = true,
    val canMessage: Boolean = true,
)

data class MsgReaction(val emoji: String, val count: Int, val mine: Boolean)

data class ClientPrefsRemote(
    val chatFolders: String,
    val chatDrafts: String,
    val notifyMentionsOnly: Boolean,
    val updatedAt: Long,
)

data class PublicLinkResult(val code: String, val url: String, val kind: String, val conversationId: Int)

data class JoinLinkResult(val conversationId: Int, val kind: String, val peerUserId: Int)

data class PublicLinkResolve(
    val code: String,
    val kind: String,
    val url: String,
    val userNick: String,
    val userDisplayName: String,
    val conversationId: Int,
    val conversationTitle: String,
    val conversationKind: String,
)

data class PinnedMessageInfo(val messageId: Long, val preview: String, val senderId: Int)

sealed class AuthResult {
    data class Ok(val token: String, val userId: Int, val nick: String) : AuthResult()
    data class Fail(val message: String, val retryable: Boolean = false, val retryAfterSec: Int = 0) : AuthResult()
    data class PendingEmailVerification(val userId: Int, val emailHint: String, val message: String = "") : AuthResult()
    data class RegistrationProof(val proof: String, val emailHint: String, val message: String = "") : AuthResult()
    data class MessageOk(val message: String) : AuthResult()
}

data class NickSuggestion(
    val nick: String,
    val alternatives: List<String> = emptyList(),
)

data class ChannelHit(
    val conversationId: Int,
    val title: String,
    val nick: String,
    val description: String = "",
    val avatarUploadId: String? = null,
    val verified: Boolean = false,
    val subscriberCount: Int = 0,
    val postCount: Int = 0,
    val pollCount: Int = 0,
    val subscribed: Boolean = false,
    val canPost: Boolean = false,
    val publicUrl: String = "",
    val openCount: Int = 0,
)

data class ChannelFeedPost(
    val id: Long,
    val bodyRaw: String,
    val displayText: String = "",
    val postMeta: ChannelPostMeta? = null,
    val pollMeta: PollMeta? = null,
    val createdAt: Long,
    val viewCount: Int = 0,
    val translation: String? = null,
    val reactions: List<MsgReaction> = emptyList(),
) {
    val shownText: String
        get() =
            when {
                translation != null && translation.isNotBlank() -> translation
                displayText.isNotBlank() -> displayText
                postMeta != null -> postMeta.text
                pollMeta != null -> pollMeta.question
                else -> ""
            }
}

private fun jsonOptCleanString(o: JSONObject, key: String): String {
    if (o.isNull(key)) return ""
    val s = o.optString(key, "").trim()
    return if (s.equals("null", ignoreCase = true)) "" else s
}

data class ChannelFeedResponse(
    val channel: ChannelHit,
    val posts: List<ChannelFeedPost>,
    val hasMore: Boolean = false,
    val nextBefore: Long = 0,
)

data class ConvItem(
    val id: Int,
    val kind: String = "dm",
    val title: String,
    val preview: String,
    val updatedAt: Long,
    val peerUserId: Int = 0,
    val peerDisplayName: String = "",
    val peerStatusEmoji: String = "",
    val peerAvatarUploadId: String? = null,
    val unreadCount: Int = 0,
    val myLastReadMessageId: Long = 0,
    val groupOwnerId: Int = 0,
    val groupMyRole: String = "",
    val groupMemberCount: Int = 0,
    val lastSenderId: Int = 0,
    val lastMessageId: Long = 0,
    val channelNick: String = "",
    val channelDescription: String = "",
    val channelVerified: Boolean = false,
    val channelAvatarUploadId: String? = null,
) {
    val channelSubscribed: Boolean
        get() = kind != "channel" || groupMyRole.isNotBlank()
}

data class DeviceSession(
    val id: Int,
    val label: String,
    val createdAt: Long,
    val lastActive: Long,
    val revoked: Boolean,
    val current: Boolean,
    val lastIp: String = "",
    val lastCountry: String = "",
)

data class GroupMember(
    val user: UserHit,
    val role: String,
    val joinedAt: Long = 0,
)

data class GroupDetail(
    val conversationId: Int,
    val title: String,
    val ownerId: Int,
    val members: List<GroupMember>,
)

data class MsgItem(
    val id: Long,
    val localId: String = "",
    val body: String,
    val bodyRaw: String = body,
    val mine: Boolean,
    val createdAt: Long,
    val editedAt: Long = 0,
    val isE2e: Boolean = false,
    val readByPeer: Boolean = false,
    val peerReadAt: Long = 0,
    val status: String = "sent",
    val mediaUploadId: String? = null,
    val mediaMime: String? = null,
    val mediaName: String? = null,
    val mediaKind: String? = null,
    val messageType: String = "text",
    val forward: ForwardMeta? = null,
    val callMeta: CallMeta? = null,
    val pollMeta: PollMeta? = null,
    val channelPostMeta: ChannelPostMeta? = null,
    val channelCardMeta: ChannelCardMeta? = null,
    val reactions: List<MsgReaction> = emptyList(),
    val senderId: Int = 0,
    val senderName: String = "",
    val reply: ReplyMeta? = null,
    val albumMeta: AlbumMeta? = null,
)

data class AssistixModel(
    val key: String,
    val name: String,
    val description: String,
    val default: Boolean,
)

data class AssistixCatalog(
    val configured: Boolean,
    val defaultModel: String,
    val models: List<AssistixModel>,
    val rateLimit: AssistixRateLimit? = null,
)

data class AssistixChatTurn(val role: String, val content: String)

data class AssistixReply(
    val ok: Boolean,
    val text: String,
    val error: String? = null,
    val message: String? = null,
    val model: String? = null,
    val rateLimit: AssistixRateLimit? = null,
    val totalTokens: Int = 0,
    val replies: List<String> = emptyList(),
)

data class GlobalSearchResult(
    val users: List<UserHit>,
    val channels: List<ChannelHit>,
    val messages: List<MessageSearchHit>,
) {
    companion object {
        val EMPTY = GlobalSearchResult(emptyList(), emptyList(), emptyList())
    }
}

data class MessageSearchHit(
    val id: Long,
    val conversationId: Int,
    val bodySnippet: String,
    val conversationTitle: String,
    val conversationKind: String,
    val createdAt: Long,
)

data class UserHit(
    val id: Int,
    val nick: String,
    val displayName: String,
    val statusEmoji: String = "",
    val avatarUploadId: String? = null,
)

/** JSON null becomes optString `"null"` — treat as absent. */
internal fun JSONObject.optCleanString(key: String): String? {
    if (isNull(key)) return null
    val v = optString(key, "").trim()
    return v.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
}

internal fun normalizeUploadId(id: String?): String? {
    val v = id?.trim() ?: return null
    return v.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
}

fun MsgItem.hasMediaAttachment(): Boolean = normalizeUploadId(mediaUploadId) != null || albumMeta != null
