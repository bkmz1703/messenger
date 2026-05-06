package ru.ilyakirollov.messenger.ui.profile

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

    val palette: List<Long> = authRepository.avatarColors()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun updateNickname(value: String) {
        viewModelScope.launch {
            _busy.value = true
            try {
                authRepository.updateNickname(value)
                preferences.setNickname(value.trim())
            } catch (_: Throwable) {
                // ignore for MVP
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
            } catch (_: Throwable) {
                // ignore for MVP
            } finally {
                _busy.value = false
            }
        }
    }
}
