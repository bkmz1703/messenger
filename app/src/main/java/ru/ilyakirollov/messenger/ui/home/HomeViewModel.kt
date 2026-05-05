package ru.ilyakirollov.messenger.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import ru.ilyakirollov.messenger.data.model.CallSession
import ru.ilyakirollov.messenger.data.repository.AuthRepository
import ru.ilyakirollov.messenger.data.repository.CallRepository

@HiltViewModel
class HomeViewModel @Inject constructor(
    authRepository: AuthRepository,
    callRepository: CallRepository,
) : ViewModel() {

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val incomingCall: StateFlow<CallSession?> = authRepository.authState()
        .flatMapLatest { uid ->
            if (uid == null) flowOf(null) else callRepository.observeIncoming(uid)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
