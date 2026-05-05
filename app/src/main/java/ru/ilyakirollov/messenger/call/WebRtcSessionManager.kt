package ru.ilyakirollov.messenger.call

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraEnumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import org.webrtc.audio.JavaAudioDeviceModule
import ru.ilyakirollov.messenger.data.model.CallSession
import ru.ilyakirollov.messenger.data.model.IceCandidateData
import ru.ilyakirollov.messenger.data.model.SessionDescriptionData
import ru.ilyakirollov.messenger.data.repository.CallRepository

/**
 * Lightweight WebRTC session manager. Uses Google STUN servers + Firestore signaling
 * (offer/answer/ICE candidates) via [CallRepository]. One instance is intended to be
 * created per active call and disposed via [release].
 */
class WebRtcSessionManager(
    private val context: Context,
    private val eglBase: EglBase,
    private val callRepository: CallRepository,
    private val callId: String,
    private val selfUid: String,
    private val isCaller: Boolean,
    private val withVideo: Boolean,
) {
    private val tag = "WebRtcSession"
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val factory: PeerConnectionFactory by lazy {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .createInitializationOptions()
        )
        PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .setAudioDeviceModule(JavaAudioDeviceModule.builder(context).createAudioDeviceModule())
            .createPeerConnectionFactory()
    }

    private var peerConnection: PeerConnection? = null
    private var videoCapturer: VideoCapturer? = null
    private var videoSource: VideoSource? = null
    private var localVideoTrack: VideoTrack? = null
    private var localAudioTrack: AudioTrack? = null
    private var surfaceHelper: SurfaceTextureHelper? = null
    private var jobs: MutableList<Job> = mutableListOf()

    private val _localTrack = MutableStateFlow<VideoTrack?>(null)
    val localTrack: StateFlow<VideoTrack?> = _localTrack.asStateFlow()

    private val _remoteTrack = MutableStateFlow<VideoTrack?>(null)
    val remoteTrack: StateFlow<VideoTrack?> = _remoteTrack.asStateFlow()

    private val _connectionState = MutableStateFlow(PeerConnection.PeerConnectionState.NEW)
    val connectionState: StateFlow<PeerConnection.PeerConnectionState> = _connectionState.asStateFlow()

    fun start() {
        val rtcConfig = PeerConnection.RTCConfiguration(STUN_SERVERS).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }
        peerConnection = factory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState) {}
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {}
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {}
            override fun onIceCandidate(candidate: IceCandidate) {
                scope.launch {
                    runCatching {
                        callRepository.addIceCandidate(
                            callId,
                            IceCandidateData(
                                sdpMid = candidate.sdpMid.orEmpty(),
                                sdpMLineIndex = candidate.sdpMLineIndex,
                                sdp = candidate.sdp,
                                from = selfUid,
                            )
                        )
                    }.onFailure { Log.w(tag, "Failed to send candidate", it) }
                }
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            override fun onAddStream(stream: org.webrtc.MediaStream?) {}
            override fun onRemoveStream(stream: org.webrtc.MediaStream?) {}
            override fun onDataChannel(channel: org.webrtc.DataChannel?) {}
            override fun onRenegotiationNeeded() {}

            override fun onAddTrack(receiver: org.webrtc.RtpReceiver?, streams: Array<out org.webrtc.MediaStream>?) {
                val track = receiver?.track()
                if (track is VideoTrack) {
                    track.setEnabled(true)
                    _remoteTrack.value = track
                }
            }

            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
                _connectionState.value = newState
            }
        })

        addLocalAudio()
        if (withVideo) addLocalVideo()

        observeRemoteSignaling()
    }

    private fun addLocalAudio() {
        val audioConstraints = MediaConstraints()
        val audioSource = factory.createAudioSource(audioConstraints)
        val track = factory.createAudioTrack("audio0", audioSource)
        track.setEnabled(true)
        localAudioTrack = track
        peerConnection?.addTrack(track, listOf(STREAM_ID))
    }

    private fun addLocalVideo() {
        val capturer = createCameraCapturer() ?: return
        videoCapturer = capturer
        val source = factory.createVideoSource(capturer.isScreencast)
        videoSource = source
        val helper = SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext)
        surfaceHelper = helper
        capturer.initialize(helper, context, source.capturerObserver)
        capturer.startCapture(1280, 720, 30)
        val track = factory.createVideoTrack("video0", source)
        track.setEnabled(true)
        localVideoTrack = track
        _localTrack.value = track
        peerConnection?.addTrack(track, listOf(STREAM_ID))
    }

    private fun createCameraCapturer(): CameraVideoCapturer? {
        val enumerator: CameraEnumerator = Camera2Enumerator(context)
        val front = enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
            ?: enumerator.deviceNames.firstOrNull() ?: return null
        return enumerator.createCapturer(front, null)
    }

    suspend fun createOfferAndPublish() {
        val pc = peerConnection ?: return
        val sdp = createSdp(pc) { observer -> pc.createOffer(observer, mediaConstraints()) }
        setLocalSdp(pc, sdp)
        callRepository.setOffer(callId, SessionDescriptionData(type = "OFFER", sdp = sdp.description))
    }

    suspend fun handleRemoteOfferAndAnswer(remote: SessionDescriptionData) {
        val pc = peerConnection ?: return
        setRemoteSdp(pc, SessionDescription(SessionDescription.Type.OFFER, remote.sdp))
        val answer = createSdp(pc) { observer -> pc.createAnswer(observer, mediaConstraints()) }
        setLocalSdp(pc, answer)
        callRepository.setAnswer(callId, SessionDescriptionData(type = "ANSWER", sdp = answer.description))
    }

    suspend fun handleRemoteAnswer(remote: SessionDescriptionData) {
        val pc = peerConnection ?: return
        setRemoteSdp(pc, SessionDescription(SessionDescription.Type.ANSWER, remote.sdp))
    }

    fun setMicEnabled(enabled: Boolean) {
        localAudioTrack?.setEnabled(enabled)
    }

    fun setCameraEnabled(enabled: Boolean) {
        localVideoTrack?.setEnabled(enabled)
    }

    fun switchCamera() {
        (videoCapturer as? CameraVideoCapturer)?.switchCamera(null)
    }

    private fun observeRemoteSignaling() {
        callRepository.observeCall(callId)
            .filterNotNull()
            .onEach { session ->
                val pc = peerConnection ?: return@onEach
                if (!isCaller && session.offer != null && pc.remoteDescription == null) {
                    runCatching { handleRemoteOfferAndAnswer(session.offer!!) }
                }
                if (isCaller && session.answer != null && pc.remoteDescription == null) {
                    runCatching { handleRemoteAnswer(session.answer!!) }
                }
            }
            .launchIn(scope)
            .also { jobs += it }

        callRepository.observeCandidates(callId, exceptFrom = selfUid)
            .onEach { c ->
                peerConnection?.addIceCandidate(IceCandidate(c.sdpMid, c.sdpMLineIndex, c.sdp))
            }
            .launchIn(scope)
            .also { jobs += it }
    }

    private suspend fun createSdp(
        pc: PeerConnection,
        action: (SdpObserver) -> Unit,
    ): SessionDescription {
        val deferred = kotlinx.coroutines.CompletableDeferred<SessionDescription>()
        action(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                if (!deferred.isCompleted) deferred.complete(sdp)
            }
            override fun onCreateFailure(error: String?) {
                if (!deferred.isCompleted) deferred.completeExceptionally(RuntimeException(error))
            }
            override fun onSetSuccess() {}
            override fun onSetFailure(error: String?) {}
        })
        return deferred.await()
    }

    private suspend fun setLocalSdp(pc: PeerConnection, sdp: SessionDescription) {
        suspendObserver { observer -> pc.setLocalDescription(observer, sdp) }
    }

    private suspend fun setRemoteSdp(pc: PeerConnection, sdp: SessionDescription) {
        suspendObserver { observer -> pc.setRemoteDescription(observer, sdp) }
    }

    private suspend fun suspendObserver(action: (SdpObserver) -> Unit) {
        val deferred = kotlinx.coroutines.CompletableDeferred<Unit>()
        action(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {}
            override fun onCreateFailure(error: String?) {}
            override fun onSetSuccess() {
                if (!deferred.isCompleted) deferred.complete(Unit)
            }
            override fun onSetFailure(error: String?) {
                if (!deferred.isCompleted) deferred.completeExceptionally(RuntimeException(error))
            }
        })
        deferred.await()
    }

    private fun mediaConstraints() = MediaConstraints().apply {
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", if (withVideo) "true" else "false"))
    }

    fun release() {
        runCatching { videoCapturer?.stopCapture() }
        videoCapturer?.dispose()
        videoSource?.dispose()
        surfaceHelper?.dispose()
        peerConnection?.close()
        peerConnection?.dispose()
        scope.coroutineContext[Job]?.cancel()
        _localTrack.value = null
        _remoteTrack.value = null
        peerConnection = null
        videoCapturer = null
        videoSource = null
        surfaceHelper = null
    }

    companion object {
        private const val STREAM_ID = "messenger_stream"
        private val STUN_SERVERS = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
        )
    }
}

@Singleton
class WebRtcEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val callRepository: CallRepository,
) {
    val eglBase: EglBase by lazy { EglBase.create() }

    fun createSession(
        callId: String,
        selfUid: String,
        isCaller: Boolean,
        withVideo: Boolean,
    ): WebRtcSessionManager = WebRtcSessionManager(
        context = context,
        eglBase = eglBase,
        callRepository = callRepository,
        callId = callId,
        selfUid = selfUid,
        isCaller = isCaller,
        withVideo = withVideo,
    )
}

/**
 * Workaround helper kept for future use: waits for the first non-null offer once the call doc
 * appears in Firestore (used when answering inbound calls reactively).
 */
internal suspend fun CallRepository.firstOffer(callId: String): SessionDescriptionData =
    observeCall(callId).filterNotNull().onEach { Unit }.first { it.offer != null }.offer!!
