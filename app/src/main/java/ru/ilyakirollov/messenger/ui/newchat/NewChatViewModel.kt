package ru.ilyakirollov.messenger.ui.newchat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.ilyakirollov.messenger.data.model.User
import ru.ilyakirollov.messenger.data.repository.AuthRepository
import ru.ilyakirollov.messenger.data.repository.ChatRepository
import ru.ilyakirollov.messenger.data.repository.UserRepository

@HiltViewModel
class NewChatViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val chatRepository: ChatRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _results = MutableStateFlow<List<User>>(emptyList())
    val results: StateFlow<List<User>> = _results.asStateFlow()

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _selected = MutableStateFlow<List<User>>(emptyList())
    val selected: StateFlow<List<User>> = _selected.asStateFlow()

    private val _groupTitle = MutableStateFlow("")
    val groupTitle: StateFlow<String> = _groupTitle.asStateFlow()

    private val _groupMode = MutableStateFlow(false)
    val groupMode: StateFlow<Boolean> = _groupMode.asStateFlow()

    private var searchJob: Job? = null

    fun setGroupMode(value: Boolean) {
        _groupMode.value = value
        if (!value) _selected.value = emptyList()
    }

    fun setGroupTitle(value: String) {
        _groupTitle.value = value
    }

    fun onQueryChange(value: String) {
        _query.value = value
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(250)
            _searching.value = true
            try {
                val list = userRepository.searchByNickname(value, authRepository.currentUid)
                _results.value = list
            } catch (t: Throwable) {
                _error.value = t.localizedMessage
            } finally {
                _searching.value = false
            }
        }
    }

    fun toggleSelected(user: User) {
        val current = _selected.value
        _selected.value = if (current.any { it.uid == user.uid }) {
            current.filterNot { it.uid == user.uid }
        } else {
            current + user
        }
    }

    fun startDirect(other: User, onChatReady: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val me = authRepository.currentUid?.let { userRepository.getUser(it) } ?: return@launch
                val id = chatRepository.getOrCreateDirectChat(me, other)
                onChatReady(id)
            } catch (t: Throwable) {
                _error.value = t.localizedMessage
            }
        }
    }

    fun createGroup(onChatReady: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val me = authRepository.currentUid?.let { userRepository.getUser(it) } ?: return@launch
                val id = chatRepository.createGroupChat(me, _selected.value, _groupTitle.value)
                onChatReady(id)
            } catch (t: Throwable) {
                _error.value = t.localizedMessage
            }
        }
    }
}
