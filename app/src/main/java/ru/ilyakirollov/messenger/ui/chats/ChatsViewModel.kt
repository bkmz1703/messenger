package ru.ilyakirollov.messenger.ui.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.ilyakirollov.messenger.data.model.Chat
import ru.ilyakirollov.messenger.data.repository.AuthRepository
import ru.ilyakirollov.messenger.data.repository.ChatRepository

@HiltViewModel
class ChatsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val chatRepository: ChatRepository,
) : ViewModel() {

    val currentUid: String? get() = authRepository.currentUid

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val chats: StateFlow<List<Chat>> = authRepository.authState()
        .flatMapLatest { uid ->
            if (uid == null) flowOf(emptyList()) else chatRepository.observeChats(uid)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun deleteChat(chatId: String) {
        viewModelScope.launch { runCatching { chatRepository.deleteChat(chatId) } }
    }
}
