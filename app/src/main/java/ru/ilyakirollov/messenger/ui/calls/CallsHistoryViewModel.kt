package ru.ilyakirollov.messenger.ui.calls

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.ilyakirollov.messenger.data.model.CallSession
import ru.ilyakirollov.messenger.data.model.User
import ru.ilyakirollov.messenger.data.repository.AuthRepository
import ru.ilyakirollov.messenger.data.repository.CallRepository
import ru.ilyakirollov.messenger.data.repository.UserRepository

@HiltViewModel
class CallsHistoryViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val callRepository: CallRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

    val currentUid: String? get() = authRepository.currentUid

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _results = MutableStateFlow<List<User>>(emptyList())
    val results: StateFlow<List<User>> = _results.asStateFlow()

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()

    private var searchJob: Job? = null

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val history: StateFlow<List<CallSession>> = authRepository.authState()
        .flatMapLatest { uid ->
            if (uid == null) flowOf(emptyList()) else callRepository.observeHistory(uid)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun clearError() { _error.value = null }

    fun onQueryChange(value: String) {
        _query.value = value
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(250)
            _searching.value = true
            try {
                _results.value = userRepository.searchByNickname(value, authRepository.currentUid)
            } catch (t: Throwable) {
                _error.value = t.localizedMessage
            } finally {
                _searching.value = false
            }
        }
    }

    fun resetSearch() {
        searchJob?.cancel()
        _query.value = ""
        _results.value = emptyList()
        _searching.value = false
    }

    /**
     * Place a fresh call to [other]. Returns the newly-created callId, or null on error.
     */
    suspend fun callUser(other: User, video: Boolean): String? {
        val uid = currentUid ?: return null
        val me = userRepository.getUser(uid) ?: return null
        return runCatching { callRepository.startCall(me, other, video) }
            .onFailure { _error.value = it.localizedMessage ?: "Не удалось позвонить" }
            .getOrNull()
    }

    /**
     * Place a fresh call to the other party of [call]. Returns the newly-created callId, or
     * null if anything goes wrong (no auth, missing user, network error). Pass [video] to
     * override the call type; default is to keep whatever the original call was.
     */
    suspend fun redial(call: CallSession, video: Boolean = call.video): String? {
        val uid = currentUid ?: return null
        val me = userRepository.getUser(uid) ?: return null
        val otherUid = if (call.callerId == uid) call.calleeId else call.callerId
        if (otherUid.isBlank()) return null
        val other = userRepository.getUser(otherUid) ?: run {
            _error.value = "Пользователь не найден"
            return null
        }
        return runCatching { callRepository.startCall(me, other, video) }
            .onFailure { _error.value = it.localizedMessage ?: "Не удалось позвонить" }
            .getOrNull()
    }
}
