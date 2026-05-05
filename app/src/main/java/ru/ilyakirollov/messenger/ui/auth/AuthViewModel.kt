package ru.ilyakirollov.messenger.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.ilyakirollov.messenger.data.prefs.UserPreferences
import ru.ilyakirollov.messenger.data.repository.AuthRepository

sealed interface AuthState {
    data object Loading : AuthState
    data object Unauthenticated : AuthState
    data class Authenticated(val uid: String, val nickname: String, val avatarColor: Long) : AuthState
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val preferences: UserPreferences,
) : ViewModel() {

    private val authFlow = authRepository.authState()
    private val nicknameFlow = preferences.nickname
    private val colorFlow = preferences.avatarColor

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val state: StateFlow<AuthState> = combine(authFlow, nicknameFlow, colorFlow) { uid, nick, color ->
        when {
            uid != null && !nick.isNullOrBlank() -> AuthState.Authenticated(uid, nick, color ?: 0xFF0F9D58)
            uid == null -> AuthState.Unauthenticated
            else -> AuthState.Loading
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AuthState.Loading)

    fun signIn(nickname: String) {
        if (_busy.value) return
        _busy.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                val color = authRepository.randomAvatarColor()
                val user = authRepository.signInAnonymously(nickname, color)
                preferences.setNickname(user.nickname)
                preferences.setAvatarColor(user.avatarColor)
            } catch (t: Throwable) {
                _error.value = t.localizedMessage ?: "Не удалось войти"
            } finally {
                _busy.value = false
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            runCatching { authRepository.signOut() }
            preferences.clear()
        }
    }
}
