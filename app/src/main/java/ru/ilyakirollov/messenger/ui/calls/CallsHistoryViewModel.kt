package ru.ilyakirollov.messenger.ui.calls

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
class CallsHistoryViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val callRepository: CallRepository,
) : ViewModel() {

    val currentUid: String? get() = authRepository.currentUid

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val history: StateFlow<List<CallSession>> = authRepository.authState()
        .flatMapLatest { uid ->
            if (uid == null) flowOf(emptyList()) else callRepository.observeHistory(uid)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
