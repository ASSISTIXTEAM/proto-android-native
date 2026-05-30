package org.assistix.proto.nativeapp.data

/** Deep links from notifications / FCM / invite URLs while app is starting. */
object ProtoAppNavigation {
    @Volatile var pendingConversationId: Int = 0
    @Volatile var pendingKind: String = "dm"
    @Volatile var pendingTitle: String = ""
    @Volatile var pendingPeerId: Int = 0
    @Volatile var pendingInviteCode: String = ""
    @Volatile var pendingVaultGate: OpenChatRequest? = null
    @Volatile var pendingProfileNick: String = ""
    @Volatile var pendingChannelNick: String = ""
    @Volatile var pendingChannelAutoSubscribe: Boolean = false
    @Volatile var pendingDevicePairId: String = ""
    @Volatile var pendingDeviceSecret: String = ""
    @Volatile var pendingOpenQrScan: Boolean = false

    fun consumeOpenChat(): OpenChatRequest? {
        val cid = pendingConversationId
        if (cid <= 0) return null
        pendingConversationId = 0
        return OpenChatRequest(
            conversationId = cid,
            title = pendingTitle.ifBlank { "Chat" },
            kind = pendingKind.ifBlank { "dm" },
            peerUserId = pendingPeerId,
        )
    }

    fun consumeInviteCode(): String? {
        val code = pendingInviteCode.trim()
        if (code.isEmpty()) return null
        pendingInviteCode = ""
        return code
    }

    fun consumeVaultGate(): OpenChatRequest? {
        val req = pendingVaultGate ?: return null
        pendingVaultGate = null
        return req
    }

    fun setPending(conversationId: Int, title: String, kind: String = "dm", peerUserId: Int = 0) {
        pendingConversationId = conversationId
        pendingTitle = title
        pendingKind = kind
        pendingPeerId = peerUserId
    }

    fun setPendingInvite(code: String) {
        pendingInviteCode = code.trim().lowercase()
    }

    fun consumeProfileNick(): String? {
        val nick = pendingProfileNick.trim()
        if (nick.isEmpty()) return null
        pendingProfileNick = ""
        return nick
    }

    fun queueProfileNick(nick: String) {
        pendingProfileNick = nick.trim()
    }

    fun queueChannel(nick: String, autoSubscribe: Boolean = true) {
        pendingChannelNick = nick.trim().removePrefix("@")
        pendingChannelAutoSubscribe = autoSubscribe
    }

    /** @return pair(nick, autoSubscribe) */
    fun consumeChannel(): Pair<String, Boolean>? {
        val nick = pendingChannelNick.trim()
        if (nick.isEmpty()) return null
        pendingChannelNick = ""
        val sub = pendingChannelAutoSubscribe
        pendingChannelAutoSubscribe = false
        return nick to sub
    }

    fun queueDeviceLink(pairId: String, secret: String) {
        pendingDevicePairId = pairId.trim()
        pendingDeviceSecret = secret.trim()
    }

    /** @return pairId to secret */
    fun consumeDeviceLink(): Pair<String, String>? {
        val p = pendingDevicePairId.trim()
        val s = pendingDeviceSecret.trim()
        if (p.isEmpty() || s.isEmpty()) return null
        pendingDevicePairId = ""
        pendingDeviceSecret = ""
        return p to s
    }

    fun queueOpenQrScan() {
        pendingOpenQrScan = true
    }

    fun consumeOpenQrScan(): Boolean {
        if (!pendingOpenQrScan) return false
        pendingOpenQrScan = false
        return true
    }

    /** Сброс отложенных deep link / уведомлений при выходе из аккаунта. */
    fun clearPending() {
        pendingConversationId = 0
        pendingKind = "dm"
        pendingTitle = ""
        pendingPeerId = 0
        pendingInviteCode = ""
        pendingVaultGate = null
        pendingProfileNick = ""
        pendingChannelNick = ""
        pendingChannelAutoSubscribe = false
        pendingDevicePairId = ""
        pendingDeviceSecret = ""
        pendingOpenQrScan = false
    }
}

data class OpenChatRequest(
    val conversationId: Int,
    val title: String,
    val kind: String,
    val peerUserId: Int,
)
