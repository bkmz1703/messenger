package ru.ilyakirollov.messenger.ui.auth

import android.app.Activity
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
import ru.ilyakirollov.messenger.data.repository.PhoneStartResult

sealed interface AuthState {
    data object Loading : AuthState
    data object Unauthenticated : AuthState
    data class Authenticated(val uid: String, val nickname: String, val avatarColor: Long) : AuthState
}

sealed interface PhoneAuthStep {
    data object Idle : PhoneAuthStep
    data object SendingSms : PhoneAuthStep
    data class CodeSent(val verificationId: String) : PhoneAuthStep
    data object NeedsNickname : PhoneAuthStep
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
            // Treat "Firebase user exists but nickname not picked yet" the same as not signed in,
            // so the LoginScreen is shown instead of a blank Loading state.
            else -> AuthState.Unauthenticated
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AuthState.Loading)

    init {
        // Best-effort reconcile for users who upgraded from a previous build that didn't claim
        // nicknames or auto-create the official channel.
        viewModelScope.launch {
            state.collect { s ->
                if (s is AuthState.Authenticated) {
                    runCatching { authRepository.reconcileBoot(s.nickname) }
                }
            }
        }
    }

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

    fun forgetAccount() {
        viewModelScope.launch {
            runCatching { authRepository.forgetAccount() }
            preferences.clear()
        }
    }

    // ---- Phone auth ----

    private val _phoneStep = MutableStateFlow<PhoneAuthStep>(PhoneAuthStep.Idle)
    val phoneStep: StateFlow<PhoneAuthStep> = _phoneStep.asStateFlow()

    fun resetPhoneFlow() {
        _phoneStep.value = PhoneAuthStep.Idle
        _error.value = null
    }

    fun startPhoneSignIn(activity: Activity, phoneE164: String) {
        if (_busy.value) return
        if (!phoneE164.startsWith("+") || phoneE164.length < 8) {
            _error.value = "Введите номер в формате +79161234567"
            return
        }
        _busy.value = true
        _error.value = null
        _phoneStep.value = PhoneAuthStep.SendingSms
        viewModelScope.launch {
            try {
                when (val r = authRepository.startPhoneVerification(activity, phoneE164)) {
                    is PhoneStartResult.AutoVerified -> {
                        // Some Android devices auto-fetch the code: finish sign-in immediately.
                        authRepository.signInWithPhoneCredential(r.credential)
                        afterPhoneSignedIn()
                    }
                    is PhoneStartResult.CodeSent -> {
                        _phoneStep.value = PhoneAuthStep.CodeSent(r.verificationId)
                    }
                    is PhoneStartResult.Failed -> {
                        _error.value = r.message
                        _phoneStep.value = PhoneAuthStep.Idle
                    }
                }
            } catch (t: Throwable) {
                _error.value = t.localizedMessage ?: "Не удалось отправить SMS"
                _phoneStep.value = PhoneAuthStep.Idle
            } finally {
                _busy.value = false
            }
        }
    }

    fun confirmPhoneSms(smsCode: String) {
        val step = _phoneStep.value
        if (step !is PhoneAuthStep.CodeSent) return
        if (smsCode.length < 4) {
            _error.value = "Введите код из SMS"
            return
        }
        if (_busy.value) return
        _busy.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                authRepository.confirmPhoneSms(step.verificationId, smsCode)
                afterPhoneSignedIn()
            } catch (t: Throwable) {
                _error.value = t.localizedMessage ?: "Неверный код"
            } finally {
                _busy.value = false
            }
        }
    }

    private suspend fun afterPhoneSignedIn() {
        // If the user already had a Firestore profile (anonymous → linked, or returning phone
        // user), reuse the nickname. Otherwise prompt for a new one.
        val existing = authRepository.currentNicknameOrNull()
        if (!existing.isNullOrBlank()) {
            preferences.setNickname(existing)
            _phoneStep.value = PhoneAuthStep.Idle
        } else {
            _phoneStep.value = PhoneAuthStep.NeedsNickname
        }
    }

    fun completePhoneSignUp(nickname: String) {
        if (_busy.value) return
        _busy.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                val color = authRepository.randomAvatarColor()
                val user = authRepository.completePhoneSignUp(nickname, color)
                preferences.setNickname(user.nickname)
                preferences.setAvatarColor(user.avatarColor)
                _phoneStep.value = PhoneAuthStep.Idle
            } catch (t: Throwable) {
                _error.value = t.localizedMessage ?: "Не удалось завершить регистрацию"
            } finally {
                _busy.value = false
            }
        }
    }
}
