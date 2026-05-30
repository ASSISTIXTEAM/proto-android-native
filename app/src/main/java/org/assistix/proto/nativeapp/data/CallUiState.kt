package org.assistix.proto.nativeapp.data

data class CallParticipant(
    val userId: Int,
    val label: String,
    val inCall: Boolean = true,
)

/** 0 = unknown … 4 = excellent (UI signal bars). */
typealias CallConnectionBars = Int

data class CallUiState(
    val active: Boolean = false,
    val incoming: Boolean = false,
    val isGroupCall: Boolean = false,
    val peerLabel: String = "",
    val peerStatusEmoji: String = "",
    val peerAvatarUploadId: String? = null,
    val status: String = "",
    val withVideo: Boolean = false,
    val conversationId: Int = 0,
    val remoteOfferSdp: String? = null,
    val signalSince: Long = 0,
    val muted: Boolean = false,
    val remoteMuted: Boolean = false,
    val speakerOn: Boolean = false,
    val videoEnabled: Boolean = false,
    val cameraFront: Boolean = true,
    val participants: List<CallParticipant> = emptyList(),
    val mediaConnected: Boolean = false,
    val callDurationSec: Int = 0,
    val encrypted: Boolean = true,
    val usingRelay: Boolean = false,
    val connectionBars: CallConnectionBars = 0,
    val reconnecting: Boolean = false,
    val peerReactionEmoji: String? = null,
    val peerReactionNonce: Long = 0L,
    val hasRemoteVideo: Boolean = false,
    val remoteVideoEnabled: Boolean = false,
    val networkProbing: Boolean = false,
    val networkProbeBars: CallConnectionBars = 0,
    val networkProbeRttMs: Int = -1,
    val groupInitiatorId: Int = 0,
    val joinedParticipantCount: Int = 0,
)
