package ru.ilyakirollov.messenger.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import ru.ilyakirollov.messenger.data.model.User

@Singleton
class UserRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) {
    fun observeUser(uid: String): Flow<User?> = callbackFlow {
        val reg = firestore.collection("users").document(uid).addSnapshotListener { snapshot, _ ->
            val user = snapshot?.toObject(User::class.java)?.apply { this.uid = snapshot.id }
            trySend(user)
        }
        awaitClose { reg.remove() }
    }

    suspend fun getUser(uid: String): User? {
        val doc = firestore.collection("users").document(uid).get().await()
        return doc.toObject(User::class.java)?.apply { this.uid = doc.id }
    }

    suspend fun searchByNickname(query: String, excludeUid: String?): List<User> {
        val q = query.trim().lowercase()
        if (q.length < 2) return emptyList()
        val snap = firestore.collection("users")
            .orderBy("nicknameLower")
            .startAt(q)
            .endAt(q + "\uf8ff")
            .limit(20)
            .get()
            .await()
        return snap.documents.mapNotNull { d ->
            d.toObject(User::class.java)?.apply { this.uid = d.id }
        }.filter { it.uid != excludeUid }
    }
}
