package ru.ilyakirollov.messenger.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import ru.ilyakirollov.messenger.data.model.User

@Singleton
class AuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val messaging: FirebaseMessaging,
) {
    val currentUid: String? get() = auth.currentUser?.uid

    fun authState(): Flow<String?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.uid) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    suspend fun signInAnonymously(nickname: String, color: Long): User {
        val sanitized = nickname.trim()
        require(sanitized.isNotBlank()) { "Никнейм не может быть пустым" }
        require(sanitized.length in 2..32) { "Никнейм должен быть от 2 до 32 символов" }

        val result = auth.signInAnonymously().await()
        val uid = result.user?.uid ?: error("Не удалось войти")

        val token = runCatching { messaging.token.await() }.getOrNull()
        val user = User(
            uid = uid,
            nickname = sanitized,
            nicknameLower = sanitized.lowercase(),
            avatarColor = color,
            fcmToken = token,
            online = true,
        )
        firestore.collection("users").document(uid).set(user, SetOptions.merge()).await()
        return user
    }

    suspend fun updateNickname(nickname: String) {
        val uid = currentUid ?: return
        val sanitized = nickname.trim()
        require(sanitized.isNotBlank()) { "Никнейм не может быть пустым" }
        firestore.collection("users").document(uid).set(
            mapOf(
                "nickname" to sanitized,
                "nicknameLower" to sanitized.lowercase(),
            ),
            SetOptions.merge(),
        ).await()
    }

    suspend fun updatePresence(online: Boolean) {
        val uid = currentUid ?: return
        val updates = mutableMapOf<String, Any>("online" to online)
        if (!online) updates["lastSeenAt"] = com.google.firebase.firestore.FieldValue.serverTimestamp()
        firestore.collection("users").document(uid).set(updates, SetOptions.merge()).await()
    }

    suspend fun refreshFcmToken() {
        val uid = currentUid ?: return
        val token = runCatching { messaging.token.await() }.getOrNull() ?: return
        firestore.collection("users").document(uid).set(
            mapOf("fcmToken" to token),
            SetOptions.merge(),
        ).await()
    }

    suspend fun signOut() {
        runCatching { updatePresence(online = false) }
        auth.signOut()
    }

    fun randomAvatarColor(): Long {
        val palette = listOf(
            0xFF0F9D58, 0xFF1976D2, 0xFFE53935, 0xFF8E24AA, 0xFFFB8C00,
            0xFF00897B, 0xFF3949AB, 0xFFD81B60, 0xFF6D4C41, 0xFF455A64,
        )
        return palette[Random.nextInt(palette.size)]
    }
}
