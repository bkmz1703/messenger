package ru.ilyakirollov.messenger.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "messenger_prefs")

@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val keyNickname = stringPreferencesKey("nickname")
    private val keyAvatarColor = longPreferencesKey("avatar_color")

    val nickname: Flow<String?> = context.dataStore.data.map { it[keyNickname] }
    val avatarColor: Flow<Long?> = context.dataStore.data.map { it[keyAvatarColor] }

    suspend fun setNickname(nickname: String) {
        context.dataStore.edit { it[keyNickname] = nickname }
    }

    suspend fun setAvatarColor(color: Long) {
        context.dataStore.edit { it[keyAvatarColor] = color }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }
}
