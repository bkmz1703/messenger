package ru.ilyakirollov.messenger.data.repository

import android.net.Uri
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import ru.ilyakirollov.messenger.data.model.Status
import ru.ilyakirollov.messenger.data.model.User

@Singleton
class StatusRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage,
) {
    fun observeRecentStatuses(): Flow<List<Status>> = callbackFlow {
        val cutoff = Timestamp(java.util.Date(System.currentTimeMillis() - TWENTY_FOUR_H_MS))
        val reg = firestore.collection("statuses")
            .whereGreaterThan("createdAt", cutoff)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, _ ->
                val list = snap?.documents.orEmpty().mapNotNull { d ->
                    d.toObject(Status::class.java)?.apply { id = d.id }
                }
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    suspend fun postTextStatus(author: User, text: String, backgroundColor: Long): String {
        require(text.isNotBlank()) { "Текст статуса не может быть пустым" }
        val ref = firestore.collection("statuses").document()
        val now = System.currentTimeMillis()
        val status = Status(
            id = ref.id,
            authorId = author.uid,
            authorNickname = author.nickname,
            authorColor = author.avatarColor,
            type = "text",
            text = text.trim(),
            backgroundColor = backgroundColor,
            expiresAt = java.util.Date(now + TWENTY_FOUR_H_MS),
        )
        ref.set(status).await()
        return ref.id
    }

    suspend fun postImageStatus(author: User, uri: Uri, caption: String?): String {
        val ref = firestore.collection("statuses").document()
        val path = "statuses/${author.uid}/${ref.id}.jpg"
        val storageRef = storage.reference.child(path)
        storageRef.putFile(uri).await()
        val url = storageRef.downloadUrl.await().toString()
        val now = System.currentTimeMillis()
        val status = Status(
            id = ref.id,
            authorId = author.uid,
            authorNickname = author.nickname,
            authorColor = author.avatarColor,
            type = "image",
            text = caption?.trim().orEmpty(),
            mediaUrl = url,
            expiresAt = java.util.Date(now + TWENTY_FOUR_H_MS),
        )
        ref.set(status).await()
        return ref.id
    }

    suspend fun markViewed(statusId: String, viewerUid: String) {
        firestore.collection("statuses").document(statusId)
            .update("viewers", FieldValue.arrayUnion(viewerUid))
            .await()
    }

    suspend fun deleteStatus(statusId: String) {
        firestore.collection("statuses").document(statusId).delete().await()
    }

    companion object {
        private const val TWENTY_FOUR_H_MS = 24L * 60L * 60L * 1000L
    }
}
