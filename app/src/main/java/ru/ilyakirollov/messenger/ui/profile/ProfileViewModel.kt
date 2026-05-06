package ru.ilyakirollov.messenger.ui.profile

import android.app.Activity
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.ilyakirollov.messenger.data.prefs.UserPreferences
import ru.ilyakirollov.messenger.data.repository.AuthRepository
import ru.ilyakirollov.messenger.data.repository.PhoneStartResult

sealed interface PhoneLinkStep {
    data object Idle : PhoneLinkStep
    data object EnteringNumber : PhoneLinkStep
    data object SendingSms : PhoneLinkStep
    data class CodeSent(val verificationId: String) : PhoneLinkStep
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val preferences: UserPreferences,
) : ViewModel() {

    val nickname: StateFlow<String?> = preferences.nickname
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val avatarColor: StateFlow<Long?> = preferences.avatarColor
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val photoUrl: StateFlow<String?> = preferences.photoUrl
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val palette: List<Long> = authRepository.avatarColors()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _phoneNumber = MutableStateFlow<String?>(FirebaseAuth.getInstance().currentUser?.phoneNumber)
    val phoneNumber: StateFlow<String?> = _phoneNumber.asStateFlow()

    private val _phoneStep = MutableStateFlow<PhoneLinkStep>(PhoneLinkStep.Idle)
    val phoneStep: StateFlow<PhoneLinkStep> = _phoneStep.asStateFlow()

    fun startPhoneLink() {
        if (_busy.value) return
        _error.value = null
        _phoneStep.value = PhoneLinkStep.EnteringNumber
    }

    fun cancelPhoneLink() {
        _phoneStep.value = PhoneLinkStep.Idle
        _error.value = null
    }

    fun submitPhoneNumber(activity: Activity, phoneE164: String) {
        if (_busy.value) return
        if (!phoneE164.startsWith("+") || phoneE164.length < 8) {
            _error.value = "Введите номер в формате +79161234567"
            return
        }
        _busy.value = true
        _error.value = null
        _phoneStep.value = PhoneLinkStep.SendingSms
        viewModelScope.launch {
            try {
                when (val r = authRepository.startPhoneVerification(activity, phoneE164)) {
                    is PhoneStartResult.AutoVerified -> {
                        authRepository.signInWithPhoneCredential(r.credential)
                        finishLink()
                    }
                    is PhoneStartResult.CodeSent -> {
                        _phoneStep.value = PhoneLinkStep.CodeSent(r.verificationId)
                    }
                    is PhoneStartResult.Failed -> {
                        _error.value = r.message
                        _phoneStep.value = PhoneLinkStep.EnteringNumber
                    }
                }
            } catch (t: Throwable) {
                _error.value = t.localizedMessage ?: "Не удалось отправить SMS"
                _phoneStep.value = PhoneLinkStep.EnteringNumber
            } finally {
                _busy.value = false
            }
        }
    }

    fun submitSmsCode(code: String) {
        val step = _phoneStep.value
        if (step !is PhoneLinkStep.CodeSent) return
        if (code.length < 4) {
            _error.value = "Введите код из SMS"
            return
        }
        if (_busy.value) return
        _busy.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                authRepository.confirmPhoneSms(step.verificationId, code)
                finishLink()
            } catch (t: Throwable) {
                _error.value = t.localizedMessage ?: "Неверный код"
            } finally {
                _busy.value = false
            }
        }
    }

    private fun finishLink() {
        _phoneNumber.value = FirebaseAuth.getInstance().currentUser?.phoneNumber
        _phoneStep.value = PhoneLinkStep.Idle
        _error.value = null
    }

    fun updateNickname(value: String) {
        viewModelScope.launch {
            _busy.value = true
            try {
                authRepository.updateNickname(value)
                preferences.setNickname(value.trim())
            } catch (t: Throwable) {
                _error.value = t.localizedMessage
            } finally {
                _busy.value = false
            }
        }
    }

    fun updateAvatarColor(color: Long) {
        viewModelScope.launch {
            _busy.value = true
            try {
                authRepository.updateAvatarColor(color)
                preferences.setAvatarColor(color)
            } catch (t: Throwable) {
                _error.value = t.localizedMessage
            } finally {
                _busy.value = false
            }
        }
    }

    fun updateAvatarPhoto(uri: Uri) {
        viewModelScope.launch {
            _busy.value = true
            try {
                val url = authRepository.updateAvatarPhoto(uri)
                preferences.setPhotoUrl(url)
            } catch (t: Throwable) {
                _error.value = t.localizedMessage ?: "Не удалось загрузить фото"
            } finally {
                _busy.value = false
            }
        }
    }

    fun clearAvatarPhoto() {
        viewModelScope.launch {
            _busy.value = true
            try {
                authRepository.clearAvatarPhoto()
                preferences.setPhotoUrl(null)
            } catch (t: Throwable) {
                _error.value = t.localizedMessage
            } finally {
                _busy.value = false
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
