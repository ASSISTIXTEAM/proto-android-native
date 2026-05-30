package org.assistix.proto.nativeapp.data

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import androidx.core.content.ContextCompat
import android.Manifest
import android.media.AudioFocusRequest
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Capturer
import org.webrtc.CameraVideoCapturer
import org.webrtc.Camera2Enumerator
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.audio.JavaAudioDeviceModule
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import org.assistix.proto.nativeapp.IncomingCallActivity
import org.assistix.proto.nativeapp.ProtoCallService
import org.assistix.proto.nativeapp.ui.UiStrings
import android.content.Intent
import org.webrtc.MediaStreamTrack
import org.webrtc.RtpTransceiver
import java.io.IOException

class ProtoCallManager(
    private val context: Context,
    private val api: ProtoApi,
    private val notifier: ProtoNotifier,
    private val prefs: ProtoAppPreferences,
) : ProtoCallGateway {
    @Volatile
    private var callNotificationsEnabled = true
    override var onCallEnded: ((CallEndInfo) -> Unit)? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val callJob = SupervisorJob()
    private val callScope =
        CoroutineScope(
            callJob + Dispatchers.Default +
                CoroutineExceptionHandler { _, e ->
                    Log.e(TAG, "call scope error", e)
                    mainHandler.post {
                        if (_state.value.active && _state.value.mediaConnected) {
                            _state.value = _state.value.copy(status = UiStrings.callReconnecting, reconnecting = true)
                        }
                    }
                },
        )
    private val pollScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(CallUiState())

    init {
        callScope.launch {
            prefs.callNotifications.collect { callNotificationsEnabled = it }
        }
    }
    override val state: StateFlow<CallUiState> = _state.asStateFlow()

    private var factory: PeerConnectionFactory? = null
    private var audioDeviceModule: JavaAudioDeviceModule? = null
    private var pc: PeerConnection? = null
    private val pendingRemoteIce = mutableListOf<IceCandidate>()
    private var remoteDescSet = false
    private val remoteAudioTracks = mutableListOf<AudioTrack>()
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hadAudioFocus = false
    private var localStream: MediaStream? = null
    private var localAudioTrack: AudioTrack? = null
    private var localVideoTrack: VideoTrack? = null
    private var remoteVideoTrack: VideoTrack? = null
    private var videoSource: VideoSource? = null
    private var cameraCapturer: Camera2Capturer? = null
    private var surfaceHelper: SurfaceTextureHelper? = null
    private var eglBase: EglBase? = null
    private var pollJob: Job? = null
    private var globalPollJob: Job? = null
    private var groupInitiatorUserId = 0
    private var rtcSince = 0L
    private var token: String? = null
    private var myUserId = 0
    private val factoryLock = Any()
    private var ringtone: MediaPlayer? = null
    private var ringbackToneGenerator: ToneGenerator? = null
    private var ringbackActive = false
    private val ringbackPulseRunnable: Runnable =
        object : Runnable {
            override fun run() {
                if (!ringbackActive) return
                try {
                    // TONE_SUP_RINGBACK — стандартные гудки ожидания ответа (ITU supervisory).
                    ringbackToneGenerator?.startTone(TONE_SUP_RINGBACK, 2_800)
                } catch (e: Exception) {
                    Log.w(TAG, "ringback pulse failed", e)
                }
                if (ringbackActive) {
                    mainHandler.postDelayed(this, 4_000L)
                }
            }
        }
    private var callStartedAt = 0L
    private var callWasAnswered = false
    private var callWasIncoming = false
    private var callWasDeclined = false
    /** Игнорировать hangup с id ≤ floor (сигналы до/включая наш offer/answer в этой сессии). */
    private var callSessionSince = 0L
    /** 0 пока offer/answer этой сессии не отправлен — защита от stale hangup между sync и post. */
    private var callLegPostedId = 0L
    @Volatile private var lastOutgoingStartMs = 0L
    private var audioManager: AudioManager? = null
    private var cameraDeviceName: String? = null
    private var iceTimeoutJob: Job? = null
    private var iceRecoveryJob: Job? = null
    private var iceRestartCount = 0
    private var relayOnlyIce = false
    private var isCallOfferer = false
    private var lastRemoteSdp: String? = null
    private var durationJob: Job? = null
    private var mediaAdaptJob: Job? = null
    private var lastVideoStatsBytes = 0L
    private var lastVideoStatsAt = 0L
    private var lastAudioStatsBytes = 0L
    private var lastAudioStatsAt = 0L
    private var audioLossStreak = 0
    private var remoteAnswerApplied = false
    private val globalCursors = mutableMapOf<Int, Long>()

    override fun bindSession(authToken: String, userId: Int) {
        token = authToken
        myUserId = userId
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        pollScope.launch(Dispatchers.IO) { runCatching { api.rtcConfig(authToken) } }
        startGlobalIncomingPoll()
    }

    override fun localVideoTrack(): VideoTrack? = localVideoTrack

    override fun remoteVideoTrack(): VideoTrack? = remoteVideoTrack

    override fun videoEglContext(): EglBase.Context? = eglBase?.eglBaseContext

    override fun clearSession() {
        token = null
        myUserId = 0
        globalPollJob?.cancel()
        stopCall()
    }

    private fun ensureFactory(): Boolean {
        if (factory != null) return true
        synchronized(factoryLock) {
            if (factory != null) return true
            return try {
                PeerConnectionFactory.initialize(
                    PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
                        .setEnableInternalTracer(false)
                        .setFieldTrials(ProtoCallAudio.fieldTrials())
                        .createInitializationOptions(),
                )
                val egl = EglBase.create()
                eglBase = egl
                audioDeviceModule = ProtoCallAudio.createAudioDeviceModule(context)
                // VP8 off — приоритет H.264 (лучше на мобильных и при международных звонках).
                val encoderFactory = DefaultVideoEncoderFactory(egl.eglBaseContext, false, true)
                val decoderFactory = DefaultVideoDecoderFactory(egl.eglBaseContext)
                factory =
                    PeerConnectionFactory.builder()
                        .setAudioDeviceModule(audioDeviceModule)
                        .setVideoEncoderFactory(encoderFactory)
                        .setVideoDecoderFactory(decoderFactory)
                        .createPeerConnectionFactory()
                true
            } catch (e: Throwable) {
                Log.e(TAG, "ensureFactory", e)
                releaseFactoryLocked()
                false
            }
        }
    }

    private fun releaseFactoryLocked() {
        runCatching { factory?.dispose() }
        factory = null
        runCatching { audioDeviceModule?.release() }
        audioDeviceModule = null
        runCatching { eglBase?.release() }
        eglBase = null
    }

    private fun safeStartCallService(title: String, playRingtone: Boolean = false) {
        try {
            ProtoCallService.start(context, title, playRingtone)
        } catch (e: Exception) {
            Log.w(TAG, "Call service start failed", e)
        }
    }

    /** Wake polling for a conversation (e.g. user tapped call notification). */
    override fun prioritizeIncomingPoll(conversationId: Int) {
        val t = token ?: return
        pollScope.launch { pollConversationSignals(t, conversationId, fromUserAction = true) }
    }

    fun startGlobalIncomingPoll() {
        globalPollJob?.cancel()
        val t = token ?: return
        globalPollJob =
            pollScope.launch {
                while (isActive) {
                    val activeCid = _state.value.conversationId
                    if (activeCid > 0 && _state.value.active) {
                        val since = maxOf(rtcSince, globalCursors[activeCid] ?: 0L)
                        val signals = withContext(Dispatchers.IO) { api.webrtcPoll(t, activeCid, since) }
                        val allowHangup = pc != null && (remoteAnswerApplied || _state.value.mediaConnected)
                        ingestCallSignals(signals, allowRemoteHangup = allowHangup)
                        delay(if (ProtoUnifiedRealtime.realtimeConnected) 120L else 80L)
                        continue
                    }
                    val convs =
                        withContext(Dispatchers.IO) { api.conversations(t) }
                            .sortedByDescending { it.updatedAt }
                    val skipCid = if (_state.value.active) _state.value.conversationId else 0
                    for (c in convs) {
                        if (!isActive) break
                        if (c.id == skipCid) continue
                        pollConversationSignals(t, c.id, convMeta = c)
                    }
                    val baseDelay =
                        if (ProtoUnifiedRealtime.realtimeConnected) {
                            if (_state.value.active) 450 else 1000
                        } else {
                            if (_state.value.active) 320 else 480
                        }
                    delay(baseDelay.toLong())
                }
            }
    }

    private suspend fun pollConversationSignals(
        t: String,
        conversationId: Int,
        convMeta: ConvItem? = null,
        fromUserAction: Boolean = false,
    ) {
        if (!globalCursors.containsKey(conversationId)) {
            globalCursors[conversationId] = withContext(Dispatchers.IO) { api.webrtcCursor(t, conversationId) }
        }
        var since = globalCursors[conversationId] ?: 0L
        val signals = withContext(Dispatchers.IO) { api.webrtcPoll(t, conversationId, since) }
        val c =
            convMeta
                ?: withContext(Dispatchers.IO) { api.conversations(t) }.firstOrNull { it.id == conversationId }
                ?: return
        for (sig in signals) {
            globalCursors[conversationId] = maxOf(globalCursors[conversationId] ?: 0L, sig.id)
            since = globalCursors[conversationId] ?: since
            if (sig.senderId == myUserId) continue
            when (sig.kind) {
                "hangup" -> {
                    if (shouldIgnoreRemoteHangup(sig)) continue
                    val st = _state.value
                    if (st.conversationId != conversationId || !st.active) continue
                    val answeredNow =
                        callWasAnswered || remoteAnswerApplied || st.mediaConnected
                    val status =
                        when {
                            answeredNow -> "answered"
                            st.incoming && pc == null -> "missed"
                            callWasIncoming -> "missed"
                            else -> "cancelled"
                        }
                    endCallRemotely(status)
                    return
                }
                "offer" -> {
                    val st = _state.value
                    if (st.isGroupCall && st.active && !st.incoming && st.conversationId == conversationId && pc == null) {
                        syncRtcCursor(t, conversationId)
                        callSessionSince = rtcSince
                        callWasAnswered = true
                        lastRemoteSdp = sig.payload
                        callScope.launch {
                            stopRingtone()
                            runPreCallProbe()
                            prepareAudioRoute(speaker = true)
                            withContext(Dispatchers.Default) {
                                if (!ensureFactory()) return@withContext
                                setupPeer(conversationId, t, withVideo = false, receiveVideo = false)
                                setRemoteDescription(sig.payload, SessionDescription.Type.OFFER)
                                createAnswerAndSend(t, conversationId, false)
                            }
                            startSignalPoll(t, conversationId)
                            startIceConnectTimeout()
                        }
                        return
                    }
                    if (pc == null && (!_state.value.active || fromUserAction)) {
                        syncRtcCursor(t, conversationId)
                        callSessionSince = rtcSince
                        showIncomingCall(c, sig.payload)
                    } else if (
                        pc == null &&
                        _state.value.incoming &&
                        _state.value.conversationId == conversationId
                    ) {
                        _state.value =
                            _state.value.copy(
                                remoteOfferSdp = sig.payload,
                                withVideo = sdpHasVideo(sig.payload),
                            )
                    }
                }
                "signal" -> {
                    runCatching {
                        val o = org.json.JSONObject(sig.payload)
                        if (o.optString("type") == "group_call") {
                            showIncomingGroupCall(c, o)
                        }
                    }
                }
            }
        }
    }

    private suspend fun showIncomingGroupCall(c: ConvItem, payload: org.json.JSONObject) {
        if (_state.value.active && _state.value.conversationId == c.id) return
        val t = token ?: return
        val detail = withContext(Dispatchers.IO) { api.groupDetail(t, c.id) }
        val initiator = payload.optInt("initiator", 0)
        val parts =
            detail?.members?.map { m ->
                val uid = m.user.id
                CallParticipant(
                    userId = uid,
                    label = resolveDisplayName(m.user.displayName, m.user.nick),
                    inCall = uid == initiator || uid == myUserId,
                )
            } ?: emptyList()
        safeStartCallService(c.title, playRingtone = true)
        _state.value =
            CallUiState(
                active = true,
                incoming = true,
                isGroupCall = true,
                peerLabel = c.title,
                status = UiStrings.groupCall,
                withVideo = false,
                conversationId = c.id,
                participants = parts,
                groupInitiatorId = initiator,
                joinedParticipantCount = parts.count { it.inCall },
            )
        groupInitiatorUserId = initiator
        callWasIncoming = true
        callWasAnswered = false
        callWasDeclined = false
        callStartedAt = System.currentTimeMillis()
        startRingtone(incoming = true)
        notifier.notifyIncomingCall(c.title, c.id, withVideo = true)
        launchIncomingScreen(c.id)
    }

    private fun launchIncomingScreen(conversationId: Int) {
        try {
            val intent =
                Intent(context, IncomingCallActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra(IncomingCallActivity.EXTRA_CID, conversationId)
                }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "launchIncomingScreen failed", e)
        }
    }

    private fun showIncomingCall(c: ConvItem, offerSdp: String) {
        if (_state.value.active && _state.value.conversationId == c.id && !_state.value.incoming) return
        safeStartCallService(c.title, playRingtone = true)
        _state.value =
            CallUiState(
                active = true,
                incoming = true,
                peerLabel = c.title,
                peerStatusEmoji = c.peerStatusEmoji,
                peerAvatarUploadId = c.peerAvatarUploadId,
                status = UiStrings.incomingCall,
                withVideo = sdpHasVideo(offerSdp),
                videoEnabled = false,
                conversationId = c.id,
                remoteOfferSdp = offerSdp,
                signalSince = 0L,
            )
        callWasIncoming = true
        callWasAnswered = false
        callWasDeclined = false
        callStartedAt = System.currentTimeMillis()
        startRingtone(incoming = true)
        notifier.notifyIncomingCall(c.title, c.id, withVideo = sdpHasVideo(offerSdp))
        launchIncomingScreen(c.id)
    }

    override fun startGroupCall(token: String, conversationId: Int, title: String) {
        callScope.launch {
            if (!hasMicPermission()) {
                _state.value =
                    CallUiState(
                        active = true,
                        peerLabel = title,
                        status = UiStrings.callError,
                        conversationId = conversationId,
                    )
                delay(1200)
                stopCall()
                return@launch
            }
            val detail = withContext(Dispatchers.IO) { api.groupDetail(token, conversationId) }
            val parts =
                detail?.members?.map { m ->
                    CallParticipant(
                        userId = m.user.id,
                        label = resolveDisplayName(m.user.displayName, m.user.nick),
                        inCall = m.user.id == myUserId,
                    )
                } ?: emptyList()
            groupInitiatorUserId = myUserId
            callWasIncoming = false
            callWasAnswered = false
            callStartedAt = System.currentTimeMillis()
            safeStartCallService(title)
            _state.value =
                CallUiState(
                    active = true,
                    incoming = false,
                    isGroupCall = true,
                    peerLabel = title,
                    status = UiStrings.groupCallWaiting,
                    withVideo = false,
                    videoEnabled = false,
                    speakerOn = true,
                    conversationId = conversationId,
                    participants = parts,
                    groupInitiatorId = myUserId,
                    joinedParticipantCount = 1,
                )
            prepareAudioRoute(speaker = true)
            runPreCallProbe()
            syncRtcCursor(token, conversationId)
            callSessionSince = rtcSince
            withContext(Dispatchers.IO) {
                val payload =
                    JSONObject()
                        .put("type", "group_call")
                        .put("title", title)
                        .put("initiator", myUserId)
                        .toString()
                api.webrtcPost(token, conversationId, "signal", payload)
            }
            startSignalPoll(token, conversationId)
            prioritizeIncomingPoll(conversationId)
        }
    }

    override fun declineIncoming() {
        val cid = _state.value.conversationId
        val t = token
        callWasDeclined = true
        if (cid > 0 && t != null) {
            callScope.launch(Dispatchers.IO) { api.webrtcPost(t, cid, "hangup", "") }
        }
        finishCallLog("declined")
        stopCall()
    }

    override fun startOutgoing(
        conversationId: Int,
        peerLabel: String,
        withVideo: Boolean,
        peerAvatarUploadId: String?,
        peerStatusEmoji: String,
    ) {
        val t = token ?: return
        val now = System.currentTimeMillis()
        if (_state.value.active) return
        if (now - lastOutgoingStartMs < 1_200L) return
        lastOutgoingStartMs = now
        if (!hasMicPermission()) {
            _state.value =
                CallUiState(
                    active = true,
                    incoming = false,
                    peerLabel = peerLabel,
                    status = UiStrings.callError,
                    conversationId = conversationId,
                )
            callScope.launch {
                delay(1500)
                stopCall()
            }
            return
        }
        callScope.launch {
            try {
                callWasIncoming = false
                callWasAnswered = false
                callWasDeclined = false
                callLegPostedId = 0L
                remoteAnswerApplied = false
                releasePeerConnection()
                resetCallMedia()
                iceRestartCount = 0
                relayOnlyIce = false
                isCallOfferer = true
                lastRemoteSdp = null
                syncRtcCursor(t, conversationId)
                callSessionSince = rtcSince
                callStartedAt = System.currentTimeMillis()
                val wantVideo = withVideo
                val sendVideo = withVideo && hasCameraPermission()
                prepareAudioRoute(speaker = !wantVideo)
                _state.value =
                    CallUiState(
                        active = true,
                        incoming = false,
                        peerLabel = peerLabel,
                        peerStatusEmoji = peerStatusEmoji,
                        peerAvatarUploadId = peerAvatarUploadId,
                        status = UiStrings.connecting,
                        withVideo = wantVideo,
                        videoEnabled = sendVideo,
                        speakerOn = !wantVideo,
                        conversationId = conversationId,
                    )
                safeStartCallService(peerLabel)
                runPreCallProbe()
                withContext(Dispatchers.Default) {
                    if (!ensureFactory()) {
                        _state.value = _state.value.copy(status = UiStrings.callError)
                        delay(1200)
                        stopCall()
                        return@withContext
                    }
                    setupPeer(conversationId, t, sendVideo, receiveVideo = wantVideo)
                    createOfferAndSend(t, conversationId, wantVideo)
                }
                prioritizeIncomingPoll(conversationId)
                startSignalPoll(t, conversationId)
                startIceConnectTimeout()
                _state.value = _state.value.copy(status = UiStrings.ringbackTone)
                startRingtone(incoming = false)
            } catch (e: Exception) {
                Log.e(TAG, "startOutgoing", e)
                _state.value = _state.value.copy(status = UiStrings.callError)
                delay(1200)
                stopCall()
            }
        }
    }

    override fun acceptIncoming() {
        if (_state.value.isGroupCall) {
            acceptGroupIncoming()
            return
        }
        val t = token ?: return
        val offer = _state.value.remoteOfferSdp ?: return
        val cid = _state.value.conversationId
        if (!hasMicPermission()) {
            declineIncoming()
            return
        }
        stopRingtone()
        notifier.cancelCallNotification()
        callScope.launch {
            try {
                callWasAnswered = true
                callLegPostedId = 0L
                remoteAnswerApplied = false
                releasePeerConnection()
                resetCallMedia()
                iceRestartCount = 0
                relayOnlyIce = false
                isCallOfferer = false
                lastRemoteSdp = offer
                syncRtcCursor(t, cid)
                callSessionSince = rtcSince
                if (callStartedAt <= 0L) callStartedAt = System.currentTimeMillis()
                val offerHasVideo = sdpHasVideo(offer)
                val sendVideo = offerHasVideo && hasCameraPermission()
                prepareAudioRoute(speaker = !offerHasVideo)
                _state.value =
                    _state.value.copy(
                        incoming = false,
                        status = UiStrings.answering,
                        withVideo = offerHasVideo,
                        videoEnabled = sendVideo,
                        speakerOn = !offerHasVideo,
                    )
                runPreCallProbe()
                withContext(Dispatchers.Default) {
                    if (!ensureFactory()) {
                        _state.value = _state.value.copy(status = UiStrings.callError)
                        delay(800)
                        stopCall()
                        return@withContext
                    }
                    setupPeer(cid, t, sendVideo, receiveVideo = offerHasVideo)
                    setRemoteDescription(offer, SessionDescription.Type.OFFER)
                    createAnswerAndSend(t, cid, offerHasVideo)
                }
                startSignalPoll(t, cid)
                startIceConnectTimeout()
            } catch (e: Exception) {
                Log.e(TAG, "acceptIncoming", e)
                _state.value = _state.value.copy(status = UiStrings.callError)
                delay(800)
                stopCall()
            }
        }
    }

    override fun toggleMute() {
        val muted = !_state.value.muted
        localAudioTrack?.setEnabled(!muted)
        runCatching { audioDeviceModule?.setMicrophoneMute(muted) }
        _state.value = _state.value.copy(muted = muted)
        postSignal(JSONObject().put("type", "mute").put("muted", muted))
    }

    override fun toggleSpeaker() {
        val on = !_state.value.speakerOn
        prepareAudioRoute(on)
        _state.value = _state.value.copy(speakerOn = on)
    }

    override fun toggleVideo() {
        val st = _state.value
        if (!st.withVideo) return
        val enabled = !st.videoEnabled
        localVideoTrack?.setEnabled(enabled)
        _state.value = st.copy(videoEnabled = enabled)
        postSignal(JSONObject().put("type", "video").put("enabled", enabled))
    }

    override fun sendCallReaction(emoji: String) {
        val e = emoji.trim()
        if (e.isEmpty()) return
        postSignal(JSONObject().put("type", "reaction").put("emoji", e))
    }

    override fun flipCamera() {
        val capturer = cameraCapturer ?: return
        val enumerator = Camera2Enumerator(context)
        val current = cameraDeviceName ?: return
        val next =
            enumerator.deviceNames.firstOrNull { name ->
                name != current && enumerator.isFrontFacing(name) != enumerator.isFrontFacing(current)
            } ?: enumerator.deviceNames.firstOrNull { it != current }
        if (next == null) return
        try {
            capturer.switchCamera(null)
            cameraDeviceName = next
            _state.value = _state.value.copy(cameraFront = enumerator.isFrontFacing(next))
        } catch (e: Exception) {
            Log.w(TAG, "flipCamera", e)
        }
    }

    override fun hangup() {
        val t = token
        val cid = _state.value.conversationId
        if (t != null && cid > 0) {
            callScope.launch(Dispatchers.IO) { api.webrtcPost(t, cid, "hangup", "") }
        }
        val status =
            when {
                callWasAnswered || remoteAnswerApplied || _state.value.mediaConnected -> "answered"
                callWasIncoming -> "missed"
                else -> "cancelled"
            }
        finishCallLog(status)
        stopCall()
    }

    fun stopCall() {
        mainHandler.post { stopCallOnMain() }
    }

    private fun endCallRemotely(status: String) {
        mainHandler.post {
            if (!_state.value.active) return@post
            finishCallLog(status)
            stopCallOnMain()
        }
    }

    private fun stopCallOnMain() {
        val endedCid = _state.value.conversationId
        stopRingtone()
        notifier.cancelCallNotification()
        ProtoCallService.stop(context)
        iceTimeoutJob?.cancel()
        iceTimeoutJob = null
        iceRecoveryJob?.cancel()
        iceRecoveryJob = null
        durationJob?.cancel()
        durationJob = null
        stopMediaAdaptation()
        ProtoCallAudio.reset()
        iceRestartCount = 0
        relayOnlyIce = false
        isCallOfferer = false
        lastRemoteSdp = null
        pollJob?.cancel()
        pollJob = null
        releasePeerConnection()
        restoreAudioRoute()
        groupInitiatorUserId = 0
        _state.value = CallUiState()
        if (endedCid > 0) {
            callScope.launch { refreshRtcCursorAfterCall(endedCid) }
        }
    }

    private suspend fun refreshRtcCursorAfterCall(conversationId: Int) {
        val t = token ?: return
        val cur = withContext(Dispatchers.IO) { api.webrtcCursor(t, conversationId) }
        globalCursors[conversationId] = maxOf(globalCursors[conversationId] ?: 0L, cur)
        if (_state.value.active && _state.value.conversationId == conversationId) {
            rtcSince = maxOf(rtcSince, cur)
        }
    }

    private fun shouldIgnoreRemoteHangup(sig: RtcSignal): Boolean {
        if (sig.id <= callSessionSince) return true
        if (callStartedAt > 0) {
            val elapsed = System.currentTimeMillis() - callStartedAt
            if (elapsed < 2_500L) return true
            if (sig.createdAt > 0 && sig.createdAt * 1000L < callStartedAt - 1_500L) return true
        }
        val st = _state.value
        if (!st.incoming && !remoteAnswerApplied && !st.mediaConnected) {
            if (callLegPostedId == 0L) return true
            if (sig.id <= callLegPostedId) return true
            val elapsed = System.currentTimeMillis() - callStartedAt
            if (elapsed < 6_000L) return true
        }
        if (st.incoming && !remoteAnswerApplied && !st.mediaConnected) {
            val elapsed = System.currentTimeMillis() - callStartedAt
            if (elapsed < 3_000L) return true
        }
        return false
    }

    private fun bumpCallSessionFloor(signalId: Long) {
        callSessionSince = maxOf(callSessionSince, signalId)
        callLegPostedId = maxOf(callLegPostedId, signalId)
    }

    private fun finishCallLog(status: String) {
        val st = _state.value
        if (st.conversationId <= 0) return
        val dur = ((System.currentTimeMillis() - callStartedAt) / 1000L).toInt().coerceAtLeast(0)
        val answered =
            status == "answered" ||
                callWasAnswered ||
                remoteAnswerApplied ||
                st.mediaConnected
        val resolved =
            when {
                answered -> "answered"
                callWasDeclined || status == "declined" -> "declined"
                status == "missed" || (callWasIncoming && !answered) -> "missed"
                else -> "cancelled"
            }
        onCallEnded?.invoke(
            CallEndInfo(
                conversationId = st.conversationId,
                peerLabel = st.peerLabel,
                withVideo = st.withVideo,
                incoming = callWasIncoming,
                answered = answered,
                durationSec = if (answered) dur else 0,
                status = resolved,
            ),
        )
        callWasIncoming = false
        callWasAnswered = false
        callWasDeclined = false
        callStartedAt = 0L
    }

    private fun prepareAudioRoute(speaker: Boolean) {
        val am = audioManager ?: return
        requestCallAudioFocus(am)
        am.mode = AudioManager.MODE_IN_COMMUNICATION
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val devices = am.availableCommunicationDevices
            val target =
                if (speaker) {
                    devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                } else {
                    devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES }
                        ?: devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET }
                        ?: devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLE_HEADSET }
                        ?: devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }
                }
            if (target != null) {
                runCatching { am.setCommunicationDevice(target) }
            }
        }
        @Suppress("DEPRECATION")
        am.isSpeakerphoneOn = speaker
    }

    private fun restoreAudioRoute() {
        val am = audioManager ?: return
        abandonCallAudioFocus(am)
        am.isSpeakerphoneOn = false
        am.mode = AudioManager.MODE_NORMAL
    }

    private fun requestCallAudioFocus(am: AudioManager) {
        if (hadAudioFocus) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attrs =
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            val req =
                AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(attrs)
                    .setAcceptsDelayedFocusGain(false)
                    .build()
            audioFocusRequest = req
            hadAudioFocus = am.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            hadAudioFocus =
                am.requestAudioFocus(
                    null,
                    AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT,
                ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonCallAudioFocus(am: AudioManager) {
        if (!hadAudioFocus) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            am.abandonAudioFocus(null)
        }
        audioFocusRequest = null
        hadAudioFocus = false
    }

    private fun releasePeerConnection() {
        pollJob?.cancel()
        pollJob = null
        iceTimeoutJob?.cancel()
        iceTimeoutJob = null
        iceRecoveryJob?.cancel()
        iceRecoveryJob = null
        durationJob?.cancel()
        durationJob = null
        stopMediaAdaptation()
        try {
            localStream?.dispose()
            pc?.close()
            pc?.dispose()
        } catch (_: Exception) {
        }
        localStream = null
        pc = null
        localAudioTrack = null
        localVideoTrack = null
        remoteVideoTrack = null
        releaseCamera()
    }

    private fun resetCallMedia() {
        remoteDescSet = false
        remoteAnswerApplied = false
        synchronized(pendingRemoteIce) { pendingRemoteIce.clear() }
        synchronized(remoteAudioTracks) {
            remoteAudioTracks.forEach { runCatching { it.setEnabled(false) } }
            remoteAudioTracks.clear()
        }
    }

    private fun addRemoteIce(candidate: IceCandidate) {
        val conn = pc
        if (!remoteDescSet || conn == null) {
            synchronized(pendingRemoteIce) { pendingRemoteIce.add(candidate) }
            return
        }
        conn.addIceCandidate(
            candidate,
            object : org.webrtc.AddIceObserver {
                override fun onAddSuccess() {}

                override fun onAddFailure(error: String?) {
                    Log.w(TAG, "addIceCandidate failed: $error")
                }
            },
        )
    }

    private fun flushPendingIce() {
        val conn = pc ?: return
        synchronized(pendingRemoteIce) {
            pendingRemoteIce.forEach { c ->
                conn.addIceCandidate(
                    c,
                    object : org.webrtc.AddIceObserver {
                        override fun onAddSuccess() {}

                        override fun onAddFailure(error: String?) {
                            Log.w(TAG, "flush ice failed: $error")
                        }
                    },
                )
            }
            pendingRemoteIce.clear()
        }
    }

    private suspend fun syncRtcCursor(token: String, conversationId: Int) {
        rtcSince = withContext(Dispatchers.IO) { api.webrtcCursor(token, conversationId) }
        globalCursors[conversationId] = rtcSince
    }

    private suspend fun postRequired(token: String, conversationId: Int, kind: String, payload: String) {
        var lastErr: Exception? = null
        repeat(6) { attempt ->
            try {
                val id =
                    withContext(Dispatchers.IO) { api.webrtcPost(token, conversationId, kind, payload) }
                        ?: throw IOException("webrtc $kind failed")
                rtcSince = maxOf(rtcSince, id)
                globalCursors[conversationId] = maxOf(globalCursors[conversationId] ?: 0L, id)
                bumpCallSessionFloor(id)
                return
            } catch (e: Exception) {
                lastErr = e
                delay(220L * (attempt + 1))
            }
        }
        throw lastErr ?: IOException("webrtc $kind failed")
    }

    private fun postIceCandidate(token: String, conversationId: Int, candidate: IceCandidate) {
        val payload =
            JSONObject()
                .put("sdpMid", candidate.sdpMid)
                .put("sdpMLineIndex", candidate.sdpMLineIndex)
                .put("candidate", candidate.sdp)
                .toString()
        callScope.launch(Dispatchers.IO) {
            repeat(6) { attempt ->
                val id = api.webrtcPost(token, conversationId, "ice", payload)
                if (id != null) {
                    rtcSince = maxOf(rtcSince, id)
                    globalCursors[conversationId] = maxOf(globalCursors[conversationId] ?: 0L, id)
                    return@launch
                }
                delay(180L * (attempt + 1))
            }
            Log.w(TAG, "ice post dropped after retries")
        }
    }

    private fun startIceConnectTimeout() {
        iceTimeoutJob?.cancel()
        iceTimeoutJob =
            callScope.launch {
                delay(12_000)
                val st1 = _state.value
                if (st1.active && !st1.mediaConnected && !relayOnlyIce && pc != null) {
                    Log.w(TAG, "ICE slow — try relay-only path")
                    recreatePeerRelayOnly()
                }
                delay(38_000)
                val st = _state.value
                if (!st.active || st.mediaConnected) return@launch
                Log.w(TAG, "ICE timeout — no connection")
                _state.value = st.copy(status = UiStrings.callError, reconnecting = false)
                delay(1200)
                hangup()
            }
    }

    private fun onIceConnected() {
        iceTimeoutJob?.cancel()
        iceTimeoutJob = null
        iceRestartCount = 0
        callWasAnswered = true
        stopRingtone()
        _state.value =
            _state.value.copy(
                status = UiStrings.connected,
                mediaConnected = true,
                reconnecting = false,
                connectionBars = 4,
                encrypted = true,
            )
        startCallDurationTicker()
        ProtoCallAudio.applyOutboundAudio(pc)
        if (_state.value.withVideo && localVideoTrack != null) {
            ProtoCallVideo.applyOutboundVideo(pc)
        }
        startMediaAdaptation()
        pulseCallConnected()
    }

    private fun pulseCallConnected() {
        runCatching {
            val vibrator =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val mgr = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                    mgr.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(85, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(85)
            }
        }
    }

    private fun stopMediaAdaptation() {
        mediaAdaptJob?.cancel()
        mediaAdaptJob = null
        lastVideoStatsBytes = 0L
        lastVideoStatsAt = 0L
        lastAudioStatsBytes = 0L
        lastAudioStatsAt = 0L
        audioLossStreak = 0
    }

    private fun startMediaAdaptation() {
        stopMediaAdaptation()
        if (localAudioTrack == null && localVideoTrack == null) return
        mediaAdaptJob =
            callScope.launch {
                delay(1_200)
                while (isActive && pc != null) {
                    val conn = pc ?: break
                    val report = collectRtcStatsReport(conn)
                    val now = System.currentTimeMillis()
                    if (localAudioTrack != null) {
                        val audioStats = ProtoCallAudio.parseStats(report)
                        val aElapsed = (now - lastAudioStatsAt).coerceAtLeast(1L)
                        val audioBytesPerSec =
                            if (lastAudioStatsAt > 0L && audioStats.outboundBytes >= lastAudioStatsBytes) {
                                ((audioStats.outboundBytes - lastAudioStatsBytes) * 1000L) / aElapsed
                            } else {
                                0L
                            }
                        lastAudioStatsBytes = audioStats.outboundBytes
                        lastAudioStatsAt = now
                        if (audioStats.packetsLost > 0) audioLossStreak++ else audioLossStreak = 0
                        val audioTier =
                            ProtoCallAudio.pickTier(
                                audioStats,
                                audioBytesPerSec,
                                _state.value.connectionBars,
                                audioLossStreak >= 2,
                                ProtoCallAudio.currentTier,
                            )
                        if (audioTier != ProtoCallAudio.currentTier) {
                            ProtoCallAudio.applyOutboundAudio(conn, audioTier)
                        }
                    }
                    if (_state.value.withVideo && localVideoTrack != null) {
                        val videoStats = ProtoCallVideo.parseStats(report)
                        val vElapsed = (now - lastVideoStatsAt).coerceAtLeast(1L)
                        val videoBytesPerSec =
                            if (lastVideoStatsAt > 0L && videoStats.outboundBitrateBps >= lastVideoStatsBytes) {
                                ((videoStats.outboundBitrateBps - lastVideoStatsBytes) * 1000L) / vElapsed
                            } else {
                                0L
                            }
                        lastVideoStatsBytes = videoStats.outboundBitrateBps
                        lastVideoStatsAt = now
                        val videoTier =
                            ProtoCallVideo.pickTier(
                                videoStats,
                                videoBytesPerSec,
                                _state.value.connectionBars,
                                ProtoCallVideo.currentTier,
                            )
                        if (videoTier != ProtoCallVideo.currentTier) {
                            ProtoCallVideo.applyCaptureTier(cameraCapturer as? CameraVideoCapturer, videoTier)
                            ProtoCallVideo.applyOutboundVideo(conn, videoTier)
                        }
                    }
                    delay(2_400)
                }
            }
    }

    private suspend fun collectRtcStatsReport(conn: PeerConnection): org.webrtc.RTCStatsReport =
        kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            conn.getStats { report ->
                if (cont.isActive) cont.resume(report, onCancellation = {})
            }
        }

    private fun startCallDurationTicker() {
        durationJob?.cancel()
        val started = System.currentTimeMillis()
        durationJob =
            callScope.launch {
                while (isActive && _state.value.mediaConnected) {
                    val sec = ((System.currentTimeMillis() - started) / 1000L).toInt().coerceAtLeast(0)
                    _state.value = _state.value.copy(callDurationSec = sec)
                    delay(1000)
                }
            }
    }

    private fun handleIceStateChange(state: PeerConnection.IceConnectionState?) {
        when (state) {
            PeerConnection.IceConnectionState.CONNECTED,
            PeerConnection.IceConnectionState.COMPLETED,
            -> onIceConnected()
            PeerConnection.IceConnectionState.CHECKING,
            PeerConnection.IceConnectionState.NEW,
            -> {
                _state.value =
                    _state.value.copy(
                        connectionBars = 2,
                        reconnecting = _state.value.mediaConnected,
                    )
            }
            PeerConnection.IceConnectionState.DISCONNECTED -> {
                _state.value =
                    _state.value.copy(
                        connectionBars = 1,
                        reconnecting = true,
                        status =
                            if (_state.value.mediaConnected) UiStrings.callReconnecting
                            else _state.value.status,
                    )
                scheduleIceRecovery(state)
            }
            PeerConnection.IceConnectionState.FAILED -> {
                _state.value =
                    _state.value.copy(
                        connectionBars = 0,
                        reconnecting = true,
                        status = UiStrings.callReconnecting,
                    )
                scheduleIceRecovery(state)
            }
            else -> {}
        }
    }

    private fun scheduleIceRecovery(state: PeerConnection.IceConnectionState?) {
        if (iceRestartCount >= MAX_ICE_RESTARTS && relayOnlyIce) {
            if (_state.value.mediaConnected) {
                _state.value = _state.value.copy(status = UiStrings.callError, reconnecting = false)
            }
            return
        }
        iceRecoveryJob?.cancel()
        iceRecoveryJob =
            callScope.launch {
                val grace =
                    if (_state.value.mediaConnected && state == PeerConnection.IceConnectionState.DISCONNECTED) {
                        5_000L
                    } else {
                        350L
                    }
                delay(grace)
                if (pc == null) return@launch
                if (iceRestartCount < MAX_ICE_RESTARTS) {
                    iceRestartCount++
                    delay(400L + iceRestartCount * 350L)
                    if (pc == null) return@launch
                    Log.w(TAG, "ICE recovery restart #$iceRestartCount ($state)")
                    pc?.restartIce()
                    return@launch
                }
                if (!relayOnlyIce && isCallOfferer) {
                    relayOnlyIce = true
                    iceRestartCount = 0
                    recreatePeerRelayOnly()
                } else if (!relayOnlyIce) {
                    relayOnlyIce = true
                    pc?.restartIce()
                }
            }
    }

    private suspend fun recreatePeerRelayOnly() {
        val t = token ?: return
        val cid = _state.value.conversationId
        if (cid <= 0) return
        val video = _state.value.withVideo && _state.value.videoEnabled
        runCatching { withContext(Dispatchers.IO) { api.rtcConfig(t) } }
        Log.w(TAG, "Recreating peer with TURN-only ICE")
        _state.value = _state.value.copy(status = UiStrings.callReconnecting, reconnecting = true)
        resetCallMedia()
        try {
            pc?.close()
            pc?.dispose()
        } catch (_: Exception) {
        }
        pc = null
        releaseCamera()
        localStream?.dispose()
        localStream = null
        localAudioTrack = null
        localVideoTrack = null
        setupPeer(cid, t, video, relayOnly = true)
        if (isCallOfferer) {
            createOfferAndSend(t, cid, video)
        } else {
            val sdp = lastRemoteSdp ?: return
            setRemoteDescription(sdp, SessionDescription.Type.OFFER)
            createAnswerAndSend(t, cid, video)
        }
    }

    private fun postSignal(payload: JSONObject) {
        val t = token ?: return
        val cid = _state.value.conversationId
        if (cid <= 0) return
        val body = payload.toString()
        callScope.launch(Dispatchers.IO) {
            repeat(5) { attempt ->
                val id = api.webrtcPost(t, cid, "signal", body)
                if (id != null) {
                    rtcSince = maxOf(rtcSince, id)
                    globalCursors[cid] = maxOf(globalCursors[cid] ?: 0L, id)
                    return@launch
                }
                delay(160L * (attempt + 1))
            }
        }
    }

    private suspend fun ingestCallSignals(signals: List<RtcSignal>, allowRemoteHangup: Boolean = true) {
        for (sig in signals) {
            rtcSince = maxOf(rtcSince, sig.id)
            val cid = _state.value.conversationId
            if (cid > 0) {
                globalCursors[cid] = maxOf(globalCursors[cid] ?: 0L, sig.id)
            }
            if (sig.senderId == myUserId) continue
            when (sig.kind) {
                "answer" -> {
                    if (!remoteAnswerApplied) {
                        remoteAnswerApplied = true
                        callWasAnswered = true
                        lastRemoteSdp = sig.payload
                        stopRingtone()
                        _state.value = _state.value.copy(status = UiStrings.peerAnsweredConnecting)
                        try {
                            setRemoteDescription(sig.payload, SessionDescription.Type.ANSWER)
                            ProtoCallAudio.applyOutboundAudio(pc)
                            ProtoCallVideo.applyOutboundVideo(pc)
                        } catch (e: Exception) {
                            Log.e(TAG, "apply remote answer failed", e)
                            mainHandler.post {
                                if (_state.value.active) {
                                    _state.value = _state.value.copy(status = UiStrings.callError)
                                }
                            }
                        }
                    }
                }
                "ice" -> {
                    try {
                        val o = JSONObject(sig.payload)
                        val c =
                            IceCandidate(
                                o.optString("sdpMid"),
                                o.optInt("sdpMLineIndex"),
                                o.optString("candidate"),
                            )
                        addRemoteIce(c)
                    } catch (_: Exception) {
                    }
                }
                "signal" -> handleRemoteSignal(sig.payload, sig.senderId)
                "hangup" -> {
                    if (!allowRemoteHangup || shouldIgnoreRemoteHangup(sig)) continue
                    val answeredNow =
                        callWasAnswered || remoteAnswerApplied || _state.value.mediaConnected
                    val status =
                        when {
                            answeredNow -> "answered"
                            callWasIncoming -> "missed"
                            else -> "cancelled"
                        }
                    endCallRemotely(status)
                    return
                }
            }
        }
    }

    private fun acceptGroupIncoming() {
        val t = token ?: return
        val cid = _state.value.conversationId
        if (!hasMicPermission()) {
            declineIncoming()
            return
        }
        stopRingtone()
        notifier.cancelCallNotification()
        callScope.launch {
            try {
                callWasAnswered = true
                groupInitiatorUserId = _state.value.groupInitiatorId
                _state.value =
                    _state.value.copy(
                        incoming = false,
                        status = UiStrings.groupCallJoining,
                        speakerOn = true,
                    )
                prepareAudioRoute(speaker = true)
                runPreCallProbe()
                postGroupSignal(
                    cid,
                    JSONObject().put("type", "group_join").put("userId", myUserId),
                )
                markGroupParticipantInCall(myUserId, true)
                startSignalPoll(t, cid)
                prioritizeIncomingPoll(cid)
            } catch (e: Exception) {
                Log.e(TAG, "acceptGroupIncoming", e)
                stopCall()
            }
        }
    }

    private suspend fun postGroupSignal(conversationId: Int, payload: JSONObject) {
        val t = token ?: return
        postRequired(t, conversationId, "signal", payload.toString())
    }

    private fun markGroupParticipantInCall(userId: Int, inCall: Boolean) {
        if (userId <= 0) return
        val updated =
            _state.value.participants.map { p ->
                if (p.userId == userId) p.copy(inCall = inCall) else p
            }
        _state.value =
            _state.value.copy(
                participants = updated,
                joinedParticipantCount = updated.count { it.inCall },
            )
    }

    private suspend fun connectGroupJoiner(joinerId: Int) {
        val t = token ?: return
        val cid = _state.value.conversationId
        if (joinerId == myUserId || pc != null) return
        markGroupParticipantInCall(joinerId, true)
        withContext(Dispatchers.Default) {
            if (!ensureFactory()) return@withContext
            setupPeer(cid, t, withVideo = false, receiveVideo = false)
            createOfferAndSend(t, cid, false)
        }
        _state.value = _state.value.copy(status = UiStrings.connecting)
        startIceConnectTimeout()
    }

    private suspend fun runPreCallProbe() {
        _state.value = _state.value.copy(networkProbing = true, status = UiStrings.callNetworkChecking)
        val probe = ProtoCallNetworkProbe.run()
        ProtoCallAudio.applyProbeBars(probe.qualityBars)
        val bars = probe.qualityBars.coerceIn(0, 4)
        _state.value =
            _state.value.copy(
                networkProbing = false,
                networkProbeBars = bars,
                networkProbeRttMs = probe.rttMs,
                connectionBars = bars.coerceAtLeast(_state.value.connectionBars),
                status =
                    if (!probe.ok && !_state.value.mediaConnected) {
                        UiStrings.callNetworkPoor
                    } else if (_state.value.isGroupCall && !_state.value.incoming) {
                        UiStrings.groupCallWaiting
                    } else {
                        _state.value.status
                    },
            )
    }

    private fun handleRemoteSignal(payload: String, senderId: Int = 0) {
        try {
            val o = JSONObject(payload)
            when (o.optString("type")) {
                "group_join" -> {
                    val uid = o.optInt("userId", senderId)
                    markGroupParticipantInCall(uid, true)
                    if (_state.value.groupInitiatorId == myUserId && uid != myUserId) {
                        callScope.launch { connectGroupJoiner(uid) }
                    }
                }
                "mute" -> _state.value = _state.value.copy(remoteMuted = o.optBoolean("muted"))
                "video" -> {
                    val enabled = o.optBoolean("enabled", true)
                    remoteVideoTrack?.setEnabled(enabled)
                    _state.value = _state.value.copy(remoteVideoEnabled = enabled)
                }
                "reaction" -> {
                    val emoji = o.optString("emoji").trim()
                    if (emoji.isNotEmpty()) {
                        _state.value =
                            _state.value.copy(
                                peerReactionEmoji = emoji,
                                peerReactionNonce = System.currentTimeMillis(),
                            )
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun startRingtone(incoming: Boolean) {
        stopRingtone()
        if (incoming) {
            startIncomingRingtone()
        } else {
            startOutgoingRingback()
        }
    }

    /** Входящий — системный рингтон пользователя (в т.ч. из FGS / фона). */
    private fun startIncomingRingtone() {
        ringbackActive = false
        IncomingCallAudio.startIncoming(context)
    }

    /** Исходящий — гудки ожидания (сетевой ringback), не звуки уведомлений. */
    private fun startOutgoingRingback() {
        ringbackActive = true
        audioManager?.let { prepareAudioRoute(speaker = !_state.value.withVideo) }
        try {
            ringbackToneGenerator?.release()
            ringbackToneGenerator =
                ToneGenerator(AudioManager.STREAM_VOICE_CALL, ToneGenerator.MAX_VOLUME / 2)
            mainHandler.removeCallbacks(ringbackPulseRunnable)
            ringbackPulseRunnable.run()
        } catch (e: Exception) {
            Log.w(TAG, "startOutgoingRingback failed", e)
            ringbackActive = false
        }
    }

    private fun stopRingtone() {
        ringbackActive = false
        mainHandler.removeCallbacks(ringbackPulseRunnable)
        try {
            ringbackToneGenerator?.stopTone()
            ringbackToneGenerator?.release()
        } catch (_: Exception) {
        }
        ringbackToneGenerator = null
        try {
            ringtone?.stop()
            ringtone?.release()
        } catch (_: Exception) {
        }
        ringtone = null
        IncomingCallAudio.stop(context)
    }

    private fun releaseCamera() {
        try {
            cameraCapturer?.stopCapture()
            cameraCapturer?.dispose()
        } catch (_: Exception) {
        }
        cameraCapturer = null
        try {
            surfaceHelper?.dispose()
        } catch (_: Exception) {
        }
        surfaceHelper = null
        try {
            videoSource?.dispose()
        } catch (_: Exception) {
        }
        videoSource = null
    }

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun sdpHasVideo(sdp: String): Boolean = sdp.contains("m=video", ignoreCase = true)

    private suspend fun resolveIceServers(token: String): List<RtcIceServer> {
        val fresh =
            runCatching { withContext(Dispatchers.IO) { api.rtcConfig(token) } }.getOrNull()
        if (!fresh.isNullOrEmpty()) return fresh
        ProtoCallIceCache.load(context)?.let { cached ->
            return ProtoCallConfig.mergeIceServers(cached)
        }
        return ProtoCallConfig.fallbackIceServers()
    }

    private suspend fun setupPeer(
        conversationId: Int,
        token: String,
        withVideo: Boolean,
        relayOnly: Boolean = false,
        receiveVideo: Boolean = withVideo,
    ) {
        ProtoCallVideo.reset()
        ProtoCallAudio.reset()
        val ice = resolveIceServers(token)
        val servers = ProtoCallConfig.toPeerIceServers(ice)
        if (servers.isEmpty()) {
            Log.e(TAG, "No ICE servers — call may fail")
        }
        val cfg = PeerConnection.RTCConfiguration(servers)
        ProtoCallConfig.applyRtcTuning(cfg, relayOnly)
        val f = factory ?: return
        pc =
            f.createPeerConnection(cfg, object : PeerConnection.Observer {
                override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}

                override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {
                    Log.d(TAG, "iceConnection=$p0")
                    mainHandler.post { handleIceStateChange(p0) }
                }

                override fun onIceConnectionReceivingChange(p0: Boolean) {}

                override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}

                override fun onIceCandidate(candidate: IceCandidate?) {
                    candidate ?: return
                    if (candidate.sdp.contains(" typ relay ", ignoreCase = true)) {
                        mainHandler.post {
                            _state.value = _state.value.copy(usingRelay = true)
                        }
                    }
                    postIceCandidate(token, conversationId, candidate)
                }

                override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}

                override fun onAddStream(stream: MediaStream?) {}

                override fun onRemoveStream(stream: MediaStream?) {}

                override fun onDataChannel(channel: DataChannel?) {}

                override fun onRenegotiationNeeded() {}

                override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                    val track = receiver?.track() ?: return
                    when (track) {
                        is AudioTrack -> {
                            track.setEnabled(true)
                            track.setVolume(1.0)
                            synchronized(remoteAudioTracks) { remoteAudioTracks.add(track) }
                            callScope.launch {
                                prepareAudioRoute(_state.value.speakerOn || !_state.value.withVideo)
                            }
                        }
                        is VideoTrack -> {
                            track.setEnabled(true)
                            remoteVideoTrack = track
                            mainHandler.post {
                                _state.value =
                                    _state.value.copy(
                                        hasRemoteVideo = true,
                                        remoteVideoEnabled = true,
                                    )
                            }
                        }
                    }
                }
            })
        val stream = f.createLocalMediaStream("proto_local")
        val audioSource = f.createAudioSource(ProtoCallAudio.localAudioConstraints())
        val audioTrack = f.createAudioTrack("proto_audio", audioSource)
        audioTrack.setEnabled(true)
        stream.addTrack(audioTrack)
        pc?.addTrack(audioTrack, listOf("proto_audio"))
        localAudioTrack = audioTrack
        if (withVideo && hasCameraPermission()) {
            startCameraCapture(f)
            localVideoTrack?.let { track ->
                pc?.addTrack(track, listOf("proto_video"))
                _state.value = _state.value.copy(videoEnabled = true, withVideo = true)
            }
        } else if (receiveVideo) {
            runCatching {
                pc?.addTransceiver(
                    MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
                    RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY),
                )
            }
        }
        localStream = stream
    }

    private fun startCameraCapture(f: PeerConnectionFactory) {
        releaseCamera()
        val egl = eglBase ?: EglBase.create().also { eglBase = it }
        val enumerator = Camera2Enumerator(context)
        val front = enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
        val back = enumerator.deviceNames.firstOrNull { !enumerator.isFrontFacing(it) }
        val device = front ?: back ?: enumerator.deviceNames.firstOrNull() ?: return
        cameraDeviceName = device
        val capturer = Camera2Capturer(context, device, null)
        cameraCapturer = capturer
        val helper = SurfaceTextureHelper.create("proto_cam", egl.eglBaseContext)
        surfaceHelper = helper
        val source = f.createVideoSource(capturer.isScreencast)
        videoSource = source
        capturer.initialize(helper, context, source.capturerObserver)
        val tier = ProtoCallVideo.Tier.SD
        capturer.startCapture(tier.width, tier.height, tier.fps)
        val track = f.createVideoTrack("proto_video", source)
        track.setEnabled(true)
        localVideoTrack = track
        _state.value = _state.value.copy(cameraFront = enumerator.isFrontFacing(device))
    }

    private suspend fun createOfferAndSend(token: String, cid: Int, withVideo: Boolean) {
        val conn = pc ?: return
        val raw = conn.createDescription { observer -> conn.createOffer(observer, mediaConstraints(withVideo)) }
        val offer = tunedLocalDescription(conn, raw)
        postRequired(token, cid, "offer", offer.description)
        ProtoCallAudio.applyOutboundAudio(conn)
        ProtoCallVideo.applyOutboundVideo(conn)
        _state.value = _state.value.copy(status = UiStrings.waitingAnswer)
    }

    private suspend fun createAnswerAndSend(token: String, cid: Int, withVideo: Boolean) {
        val conn = pc ?: return
        val raw = conn.createDescription { observer -> conn.createAnswer(observer, mediaConstraints(withVideo)) }
        val answer = tunedLocalDescription(conn, raw)
        postRequired(token, cid, "answer", answer.description)
        ProtoCallAudio.applyOutboundAudio(conn)
        ProtoCallVideo.applyOutboundVideo(conn)
        _state.value = _state.value.copy(status = UiStrings.connecting)
    }

    private suspend fun tunedLocalDescription(conn: PeerConnection, raw: SessionDescription): SessionDescription {
        val tuned = ProtoCallVideo.tunedDescription(raw.type, raw.description)
        return try {
            conn.setDescription { observer -> conn.setLocalDescription(observer, tuned) }
            tuned
        } catch (e: Exception) {
            Log.w(TAG, "setLocal tuned SDP failed, retry raw", e)
            conn.setDescription { observer -> conn.setLocalDescription(observer, raw) }
            raw
        }
    }

    private fun mediaConstraints(withVideo: Boolean): MediaConstraints {
        val mc = MediaConstraints()
        mc.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        if (withVideo) {
            mc.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }
        return mc
    }

    private suspend fun setRemoteDescription(sdp: String, type: SessionDescription.Type) {
        val conn = pc ?: return
        val desc = SessionDescription(type, sdp)
        conn.setDescription { observer -> conn.setRemoteDescription(observer, desc) }
        remoteDescSet = true
        flushPendingIce()
    }

    private fun startSignalPoll(token: String, cid: Int) {
        pollJob?.cancel()
        pollJob =
            callScope.launch {
                while (isActive && pc != null) {
                    val signals = withContext(Dispatchers.IO) { api.webrtcPoll(token, cid, rtcSince) }
                    ingestCallSignals(signals)
                    delay(SIGNAL_POLL_MS)
                }
            }
    }

    private suspend fun PeerConnection.createDescription(block: (SdpObserver) -> Unit): SessionDescription =
        kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            block(
                object : SdpObserver {
                    override fun onCreateSuccess(desc: SessionDescription?) {
                        if (desc != null) cont.resume(desc, onCancellation = {})
                        else cont.cancel(Exception("null sdp"))
                    }

                    override fun onSetSuccess() {}

                    override fun onCreateFailure(p0: String?) {
                        cont.cancel(Exception(p0 ?: "sdp create fail"))
                    }

                    override fun onSetFailure(p0: String?) {}
                },
            )
        }

    private suspend fun PeerConnection.setDescription(block: (SdpObserver) -> Unit) {
        kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            block(
                object : SdpObserver {
                    override fun onCreateSuccess(desc: SessionDescription?) {}

                    override fun onSetSuccess() {
                        cont.resume(Unit, onCancellation = {})
                    }

                    override fun onCreateFailure(p0: String?) {
                        cont.cancel(Exception(p0 ?: "sdp create fail"))
                    }

                    override fun onSetFailure(p0: String?) {
                        cont.cancel(Exception(p0 ?: "sdp set fail"))
                    }
                },
            )
        }
    }

    companion object {
        private const val TAG = "ProtoCall"
        private const val TONE_SUP_RINGBACK = 25
        private const val MAX_ICE_RESTARTS = 8
        private const val SIGNAL_POLL_MS = 90L
    }
}
