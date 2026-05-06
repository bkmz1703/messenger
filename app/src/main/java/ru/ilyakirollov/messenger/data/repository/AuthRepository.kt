package ru.ilyakirollov.messenger.data.repository

import android.net.Uri
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
import ru.ilyakirollov.messenger.data.upload.CloudinaryResourceType
import ru.ilyakirollov.messenger.data.upload.CloudinaryUploader

@Singleton
class AuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val messaging: FirebaseMessaging,
    private val uploader: CloudinaryUploader,
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

        // Reuse existing anonymous user if Firebase already remembers one. This is critical:
        // creating a fresh anonymous account on every login would orphan all previous chats.
        val existing = auth.currentUser
        val uid = if (existing != null) {
            existing.uid
        } else {
            val result = auth.signInAnonymously().await()
            result.user?.uid ?: error("Не удалось войти")
        }

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
        propagateUserToChats(uid, sanitized, color, photoUrl = null)
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
        propagateUserToChats(uid, nickname = sanitized, color = null, photoUrl = null)
    }

    suspend fun updateAvatarColor(color: Long) {
        val uid = currentUid ?: return
        firestore.collection("users").document(uid).set(
            mapOf("avatarColor" to color),
            SetOptions.merge(),
        ).await()
        propagateUserToChats(uid, nickname = null, color = color, photoUrl = null)
    }

    /** Upload an image from the user's gallery to Cloudinary and use it as the avatar photo. */
    suspend fun updateAvatarPhoto(uri: Uri): String {
        val uid = currentUid ?: error("Не авторизован")
        val result = uploader.upload(uri, CloudinaryResourceType.IMAGE, "avatar_$uid")
        firestore.collection("users").document(uid).set(
            mapOf("photoUrl" to result.secureUrl),
            SetOptions.merge(),
        ).await()
        propagateUserToChats(uid, nickname = null, color = null, photoUrl = result.secureUrl)
        return result.secureUrl
    }

    /** Remove avatar photo, falling back to the color avatar. */
    suspend fun clearAvatarPhoto() {
        val uid = currentUid ?: return
        firestore.collection("users").document(uid).set(
            mapOf("photoUrl" to null),
            SetOptions.merge(),
        ).await()
        propagateUserToChats(uid, nickname = null, color = null, photoUrl = "")
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

    /**
     * Local-only sign out: clears prefs but keeps the Firebase anonymous credential. Calling
     * `signInAnonymously` afterwards will reuse the same UID, so the user keeps their chats.
     * Use [forgetAccount] to fully drop the anonymous account.
     */
    suspend fun signOut() {
        runCatching { updatePresence(online = false) }
    }

    /**
     * Hard reset: signs the anonymous user out for real. The next sign-in produces a brand new
     * UID, so any existing chats will no longer be visible to this device.
     */
    suspend fun forgetAccount() {
        runCatching { updatePresence(online = false) }
        auth.signOut()
    }

    /**
     * Update denormalized participant info on every chat the user belongs to. Pass non-null
     * values for the fields that changed; pass an empty string in [photoUrl] to clear the
     * photo (set to null in Firestore).
     */
    private suspend fun propagateUserToChats(
        uid: String,
        nickname: String?,
        color: Long?,
        photoUrl: String?,
    ) {
        if (nickname == null && color == null && photoUrl == null) return
        val chats = runCatching {
            firestore.collection("chats")
                .whereArrayContains("participants", uid)
                .get()
                .await()
        }.getOrNull() ?: return

        if (chats.isEmpty) return
        val updates = mutableMapOf<String, Any?>()
        if (nickname != null) updates["participantNicknames.$uid"] = nickname
        if (color != null) updates["participantColors.$uid"] = color
        if (photoUrl != null) {
            // Empty string means "remove the photo"; otherwise store the URL.
            updates["participantPhotoUrls.$uid"] =
                if (photoUrl.isBlank()) com.google.firebase.firestore.FieldValue.delete() else photoUrl
        }

        firestore.runBatch { batch ->
            chats.documents.forEach { doc ->
                batch.update(doc.reference, updates)
            }
        }.await()
    }

    fun randomAvatarColor(): Long = avatarPalette[Random.nextInt(avatarPalette.size)]

    fun avatarColors(): List<Long> = avatarPalette

    companion object {
        private val avatarPalette = listOf(
            0xFF0F9D58, 0xFF1976D2, 0xFFE53935, 0xFF8E24AA, 0xFFFB8C00,
            0xFF00897B, 0xFF3949AB, 0xFFD81B60, 0xFF6D4C41, 0xFF455A64,
            0xFF2E7D32, 0xFF6A1B9A, 0xFFF4511E, 0xFF00838F, 0xFF5D4037,
        )
    }
}
