package ru.ilyakirollov.messenger.ui.profile

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
import ru.ilyakirollov.messenger.data.prefs.UserPreferences
import ru.ilyakirollov.messenger.data.repository.AuthRepository

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
