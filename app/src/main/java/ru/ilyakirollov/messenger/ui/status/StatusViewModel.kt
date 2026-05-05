package ru.ilyakirollov.messenger.ui.status

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.ilyakirollov.messenger.data.model.Status
import ru.ilyakirollov.messenger.data.repository.AuthRepository
import ru.ilyakirollov.messenger.data.repository.StatusRepository
import ru.ilyakirollov.messenger.data.repository.UserRepository

@HiltViewModel
class StatusViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val statusRepository: StatusRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

    val currentUid: String? get() = authRepository.currentUid

    val statuses: StateFlow<List<Status>> = statusRepository.observeRecentStatuses()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _posted = MutableStateFlow(false)
    val posted: StateFlow<Boolean> = _posted.asStateFlow()

    fun postText(text: String, backgroundColor: Long) {
        viewModelScope.launch {
            _busy.value = true
            try {
                val uid = currentUid ?: return@launch
                val me = userRepository.getUser(uid) ?: return@launch
                statusRepository.postTextStatus(me, text, backgroundColor)
                _posted.value = true
            } catch (t: Throwable) {
                _error.value = t.localizedMessage
            } finally {
                _busy.value = false
            }
        }
    }

    fun postImage(uri: Uri, caption: String?) {
        viewModelScope.launch {
            _busy.value = true
            try {
                val uid = currentUid ?: return@launch
                val me = userRepository.getUser(uid) ?: return@launch
                statusRepository.postImageStatus(me, uri, caption)
                _posted.value = true
            } catch (t: Throwable) {
                _error.value = t.localizedMessage
            } finally {
                _busy.value = false
            }
        }
    }

    fun markViewed(statusId: String) {
        viewModelScope.launch {
            val uid = currentUid ?: return@launch
            runCatching { statusRepository.markViewed(statusId, uid) }
        }
    }

    fun delete(statusId: String) {
        viewModelScope.launch {
            runCatching { statusRepository.deleteStatus(statusId) }
        }
    }
}
