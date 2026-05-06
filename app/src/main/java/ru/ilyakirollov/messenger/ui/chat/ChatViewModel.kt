package ru.ilyakirollov.messenger.ui.chat

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.ilyakirollov.messenger.data.model.Chat
import ru.ilyakirollov.messenger.data.model.Message
import ru.ilyakirollov.messenger.data.model.MessageType
import ru.ilyakirollov.messenger.data.model.User
import ru.ilyakirollov.messenger.data.repository.AuthRepository
import ru.ilyakirollov.messenger.data.repository.CallRepository
import ru.ilyakirollov.messenger.data.repository.ChatRepository
import ru.ilyakirollov.messenger.data.repository.UserRepository
import ru.ilyakirollov.messenger.util.VoiceRecorder

@HiltViewModel
class ChatViewModel @Inject constructor(
    application: Application,
    savedState: SavedStateHandle,
    private val authRepository: AuthRepository,
    private val chatRepository: ChatRepository,
    private val userRepository: UserRepository,
    private val callRepository: CallRepository,
) : AndroidViewModel(application) {

    val chatId: String = savedState["chatId"] ?: error("chatId required")
    val currentUid: String? get() = authRepository.currentUid

    private val voiceRecorder = VoiceRecorder(application)

    private val _input = MutableStateFlow("")
    val input: StateFlow<String> = _input.asStateFlow()

    private val _recording = MutableStateFlow(false)
    val recording: StateFlow<Boolean> = _recording.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val chat: StateFlow<Chat?> = chatRepository.observeChat(chatId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val messages: StateFlow<List<Message>> = chatRepository.observeMessages(chatId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val otherUser: StateFlow<User?> = chat
        .map { c ->
            val uid = currentUid
            val otherUid = c?.participants?.firstOrNull { it != uid } ?: return@map null
            userRepository.getUser(otherUid)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            messages.collect { list ->
                val uid = currentUid ?: return@collect
                if (list.isNotEmpty()) {
                    runCatching { chatRepository.markRead(chatId, uid, list) }
                }
            }
        }
    }

    fun setInput(value: String) { _input.value = value }

    fun clearError() { _error.value = null }

    fun sendText() {
        val text = _input.value
        if (text.isBlank()) return
        viewModelScope.launch {
            _busy.value = true
            try {
                val uid = currentUid ?: return@launch
                val me = userRepository.getUser(uid) ?: return@launch
                chatRepository.sendText(chatId, me, text)
                _input.value = ""
            } catch (t: Throwable) {
                _error.value = t.localizedMessage
            } finally {
                _busy.value = false
            }
        }
    }

    fun sendImage(uri: Uri) = sendMedia(uri, MessageType.IMAGE, "📷 Фото")
    fun sendVideo(uri: Uri) = sendMedia(uri, MessageType.VIDEO, "🎥 Видео")
    fun sendFile(uri: Uri, fileName: String?, fileSize: Long) =
        sendMedia(uri, MessageType.FILE, "📎 ${fileName ?: "Файл"}", fileName, fileSize)

    private fun sendMedia(
        uri: Uri,
        type: MessageType,
        preview: String,
        fileName: String? = null,
        fileSize: Long = 0L,
    ) {
        viewModelScope.launch {
            _busy.value = true
            try {
                val uid = currentUid ?: return@launch
                val me = userRepository.getUser(uid) ?: return@launch
                chatRepository.sendMedia(
                    chatId = chatId,
                    sender = me,
                    type = type,
                    uri = uri,
                    fileName = fileName,
                    fileSize = fileSize,
                    previewLabel = preview,
                )
            } catch (t: Throwable) {
                _error.value = t.localizedMessage
            } finally {
                _busy.value = false
            }
        }
    }

    fun startVoiceRecording() {
        if (_recording.value) return
        val uri = voiceRecorder.start() ?: run {
            _error.value = "Не удалось начать запись"
            return
        }
        _recording.value = true
        // Hold the URI in a cheap way. We'll re-use the recorder for stop().
        _pendingVoiceUri = uri
    }

    fun stopVoiceRecording(send: Boolean) {
        if (!_recording.value) return
        _recording.value = false
        val result = voiceRecorder.stop()
        if (!send) {
            voiceRecorder.cancel()
            _pendingVoiceUri = null
            return
        }
        if (result == null) {
            _error.value = "Не удалось завершить запись"
            return
        }
        val (uri, durationMs) = result
        viewModelScope.launch {
            _busy.value = true
            try {
                val uid = currentUid ?: return@launch
                val me = userRepository.getUser(uid) ?: return@launch
                chatRepository.sendMedia(
                    chatId = chatId,
                    sender = me,
                    type = MessageType.VOICE,
                    uri = uri,
                    durationMs = durationMs,
                    previewLabel = "🎙 Голосовое сообщение",
                )
            } catch (t: Throwable) {
                _error.value = t.localizedMessage
            } finally {
                _busy.value = false
                _pendingVoiceUri = null
            }
        }
    }

    suspend fun startCall(video: Boolean): String? {
        val uid = currentUid ?: return null
        val me = userRepository.getUser(uid) ?: return null
        // Read participant info directly from the chat doc so we can place the call even if
        // [otherUser] hasn't finished loading yet.
        val current = chat.value
        if (current == null || current.type != Chat.TYPE_DIRECT) {
            _error.value = "Звонки доступны только в личных чатах"
            return null
        }
        val otherUid = current.participants.firstOrNull { it != uid } ?: return null
        val other = userRepository.getUser(otherUid) ?: return null
        return runCatching { callRepository.startCall(me, other, video) }
            .onFailure { _error.value = it.localizedMessage }
            .getOrNull()
    }

    fun renameChat(title: String) {
        viewModelScope.launch {
            _busy.value = true
            try {
                chatRepository.updateChatTitle(chatId, title)
            } catch (t: Throwable) {
                _error.value = t.localizedMessage
            } finally {
                _busy.value = false
            }
        }
    }

    fun changeChatPhoto(uri: Uri) {
        viewModelScope.launch {
            _busy.value = true
            try {
                chatRepository.updateChatPhoto(chatId, uri)
            } catch (t: Throwable) {
                _error.value = t.localizedMessage ?: "Не удалось загрузить фото"
            } finally {
                _busy.value = false
            }
        }
    }

    fun clearChatPhoto() {
        viewModelScope.launch {
            _busy.value = true
            try {
                chatRepository.clearChatPhoto(chatId)
            } catch (t: Throwable) {
                _error.value = t.localizedMessage
            } finally {
                _busy.value = false
            }
        }
    }

    suspend fun deleteChatAndExit(): Boolean {
        return runCatching { chatRepository.deleteChat(chatId) }
            .onFailure { _error.value = it.localizedMessage }
            .isSuccess
    }

    private var _pendingVoiceUri: Uri? = null

    override fun onCleared() {
        super.onCleared()
        runCatching { voiceRecorder.cancel() }
    }
}
