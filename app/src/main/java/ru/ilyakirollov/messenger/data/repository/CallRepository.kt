package ru.ilyakirollov.messenger.data.repository

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import ru.ilyakirollov.messenger.data.model.CallSession
import ru.ilyakirollov.messenger.data.model.IceCandidateData
import ru.ilyakirollov.messenger.data.model.SessionDescriptionData
import ru.ilyakirollov.messenger.data.model.User

@Singleton
class CallRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) {
    private val calls = firestore.collection("calls")

    suspend fun startCall(caller: User, callee: User, video: Boolean): String {
        val ref = calls.document()
        val session = CallSession(
            id = ref.id,
            callerId = caller.uid,
            callerNickname = caller.nickname,
            calleeId = callee.uid,
            calleeNickname = callee.nickname,
            video = video,
            status = CallSession.STATUS_RINGING,
        )
        ref.set(session).await()
        return ref.id
    }

    suspend fun setOffer(callId: String, offer: SessionDescriptionData) {
        calls.document(callId).set(mapOf("offer" to offer), SetOptions.merge()).await()
    }

    suspend fun setAnswer(callId: String, answer: SessionDescriptionData) {
        calls.document(callId).set(
            mapOf(
                "answer" to answer,
                "status" to CallSession.STATUS_ANSWERED,
            ),
            SetOptions.merge(),
        ).await()
    }

    suspend fun decline(callId: String) {
        calls.document(callId).set(
            mapOf("status" to CallSession.STATUS_DECLINED, "endedAt" to FieldValue.serverTimestamp()),
            SetOptions.merge(),
        ).await()
    }

    suspend fun end(callId: String) {
        calls.document(callId).set(
            mapOf("status" to CallSession.STATUS_ENDED, "endedAt" to FieldValue.serverTimestamp()),
            SetOptions.merge(),
        ).await()
    }

    suspend fun addIceCandidate(callId: String, candidate: IceCandidateData) {
        calls.document(callId).collection("candidates").add(candidate).await()
    }

    fun observeCall(callId: String): Flow<CallSession?> = callbackFlow {
        val reg = calls.document(callId).addSnapshotListener { snap, _ ->
            trySend(snap?.toObject(CallSession::class.java)?.apply { id = snap.id })
        }
        awaitClose { reg.remove() }
    }

    fun observeCandidates(callId: String, exceptFrom: String): Flow<IceCandidateData> = callbackFlow {
        val reg = calls.document(callId).collection("candidates")
            .addSnapshotListener { snap, _ ->
                snap?.documentChanges.orEmpty().forEach { change ->
                    if (change.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                        val cand = change.document.toObject(IceCandidateData::class.java)
                        if (cand.from != exceptFrom) trySend(cand)
                    }
                }
            }
        awaitClose { reg.remove() }
    }

    fun observeIncoming(uid: String): Flow<CallSession?> = callbackFlow {
        val reg = calls
            .whereEqualTo("calleeId", uid)
            .whereEqualTo("status", CallSession.STATUS_RINGING)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(1)
            .addSnapshotListener { snap, _ ->
                val first = snap?.documents?.firstOrNull()
                trySend(first?.toObject(CallSession::class.java)?.apply { id = first.id })
            }
        awaitClose { reg.remove() }
    }

    fun observeHistory(uid: String): Flow<List<CallSession>> = callbackFlow {
        val reg = calls
            .where(com.google.firebase.firestore.Filter.or(
                com.google.firebase.firestore.Filter.equalTo("callerId", uid),
                com.google.firebase.firestore.Filter.equalTo("calleeId", uid),
            ))
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snap, _ ->
                val list = snap?.documents.orEmpty().mapNotNull { d ->
                    d.toObject(CallSession::class.java)?.apply { id = d.id }
                }
                trySend(list)
            }
        awaitClose { reg.remove() }
    }
}
