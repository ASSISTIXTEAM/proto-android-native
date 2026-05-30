package org.assistix.proto.nativeapp.data

import kotlinx.coroutines.flow.StateFlow
import org.webrtc.EglBase
import org.webrtc.VideoTrack

interface ProtoCallGateway {
    val state: StateFlow<CallUiState>
    var onCallEnded: ((CallEndInfo) -> Unit)?

    fun bindSession(authToken: String, userId: Int)
    fun clearSession()
    fun prioritizeIncomingPoll(conversationId: Int)
    fun startGroupCall(token: String, conversationId: Int, title: String)
    fun declineIncoming()
    fun startOutgoing(
        conversationId: Int,
        peerLabel: String,
        withVideo: Boolean,
        peerAvatarUploadId: String? = null,
        peerStatusEmoji: String = "",
    )
    fun acceptIncoming()
    fun toggleMute()
    fun toggleSpeaker()
    fun toggleVideo()
    fun flipCamera()
    fun sendCallReaction(emoji: String)
    fun hangup()

    fun localVideoTrack(): VideoTrack? = null
    fun remoteVideoTrack(): VideoTrack? = null
    fun videoEglContext(): EglBase.Context? = null
}
