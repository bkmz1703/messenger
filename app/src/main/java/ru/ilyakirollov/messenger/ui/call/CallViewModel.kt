package ru.ilyakirollov.messenger.ui.call

import android.app.Application
import android.media.AudioManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.webrtc.PeerConnection
import org.webrtc.VideoTrack
import ru.ilyakirollov.messenger.call.CallForegroundService
import ru.ilyakirollov.messenger.call.WebRtcEngine
import ru.ilyakirollov.messenger.call.WebRtcSessionManager
import ru.ilyakirollov.messenger.data.model.CallSession
import ru.ilyakirollov.messenger.data.repository.AuthRepository
import ru.ilyakirollov.messenger.data.repository.CallRepository

@HiltViewModel
class CallViewModel @Inject constructor(
    application: Application,
    savedState: SavedStateHandle,
    private val authRepository: AuthRepository,
    private val callRepository: CallRepository,
    private val engine: WebRtcEngine,
) : AndroidViewModel(application) {

    val callId: String = savedState["callId"] ?: error("callId required")
    val isCaller: Boolean = savedState.get<Boolean>("caller") ?: false
    val isVideo: Boolean = savedState.get<Boolean>("video") ?: false

    private val audioManager =
        application.getSystemService(Application.AUDIO_SERVICE) as AudioManager

    private val _micEnabled = MutableStateFlow(true)
    val micEnabled: StateFlow<Boolean> = _micEnabled.asStateFlow()

    private val _cameraEnabled = MutableStateFlow(isVideo)
    val cameraEnabled: StateFlow<Boolean> = _cameraEnabled.asStateFlow()

    private val _speakerOn = MutableStateFlow(isVideo)
    val speakerOn: StateFlow<Boolean> = _speakerOn.asStateFlow()

    private val _connectionState = MutableStateFlow(PeerConnection.PeerConnectionState.NEW)
    val connectionState: StateFlow<PeerConnection.PeerConnectionState> = _connectionState.asStateFlow()

    private val _localTrack = MutableStateFlow<VideoTrack?>(null)
    val localTrack: StateFlow<VideoTrack?> = _localTrack.asStateFlow()

    private val _remoteTrack = MutableStateFlow<VideoTrack?>(null)
    val remoteTrack: StateFlow<VideoTrack?> = _remoteTrack.asStateFlow()

    val session: StateFlow<CallSession?> = callRepository.observeCall(callId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val eglContext get() = engine.eglBase.eglBaseContext

    private var webRtc: WebRtcSessionManager? = null

    fun start() {
        if (webRtc != null) return
        val uid = authRepository.currentUid ?: return
        val rtc = engine.createSession(callId, uid, isCaller, isVideo).also { webRtc = it }
        rtc.start()
        viewModelScope.launch {
            rtc.localTrack.collect { _localTrack.value = it }
        }
        viewModelScope.launch {
            rtc.remoteTrack.collect { _remoteTrack.value = it }
        }
        viewModelScope.launch {
            rtc.connectionState.collect { _connectionState.value = it }
        }

        applyAudioRoute(_speakerOn.value)
        CallForegroundService.start(getApplication())

        if (isCaller) {
            viewModelScope.launch {
                runCatching { rtc.createOfferAndPublish() }
            }
        }
    }

    fun toggleMic() {
        val v = !_micEnabled.value
        _micEnabled.value = v
        webRtc?.setMicEnabled(v)
    }

    fun toggleCamera() {
        val v = !_cameraEnabled.value
        _cameraEnabled.value = v
        webRtc?.setCameraEnabled(v)
    }

    fun switchCamera() {
        webRtc?.switchCamera()
    }

    fun toggleSpeaker() {
        val v = !_speakerOn.value
        _speakerOn.value = v
        applyAudioRoute(v)
    }

    private fun applyAudioRoute(speaker: Boolean) {
        runCatching {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isSpeakerphoneOn = speaker
        }
    }

    fun answer() {
        viewModelScope.launch { runCatching { /* answering happens automatically when offer arrives */ } }
    }

    fun decline() {
        viewModelScope.launch {
            runCatching { callRepository.decline(callId) }
            release()
        }
    }

    fun end() {
        viewModelScope.launch {
            runCatching { callRepository.end(callId) }
            release()
        }
    }

    fun release() {
        webRtc?.release()
        webRtc = null
        runCatching {
            audioManager.mode = AudioManager.MODE_NORMAL
            audioManager.isSpeakerphoneOn = false
        }
        CallForegroundService.stop(getApplication())
    }

    override fun onCleared() {
        super.onCleared()
        release()
    }
}
