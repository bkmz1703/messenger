package ru.ilyakirollov.messenger.data.repository

import android.net.Uri
import com.google.firebase.firestore.FieldPath
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
import ru.ilyakirollov.messenger.data.model.Chat
import ru.ilyakirollov.messenger.data.model.Message
import ru.ilyakirollov.messenger.data.model.MessageType
import ru.ilyakirollov.messenger.data.model.User
import ru.ilyakirollov.messenger.data.upload.CloudinaryResourceType
import ru.ilyakirollov.messenger.data.upload.CloudinaryUploader

@Singleton
class ChatRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val uploader: CloudinaryUploader,
) {
    fun observeChats(uid: String): Flow<List<Chat>> = callbackFlow {
        val reg = firestore.collection("chats")
            .whereArrayContains("participants", uid)
            .orderBy("lastMessageAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, _ ->
                val list = snap?.documents.orEmpty().mapNotNull { d ->
                    d.toObject(Chat::class.java)?.apply { id = d.id }
                }
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    fun observeChat(chatId: String): Flow<Chat?> = callbackFlow {
        val reg = firestore.collection("chats").document(chatId)
            .addSnapshotListener { snap, _ ->
                val chat = snap?.toObject(Chat::class.java)?.apply { id = snap.id }
                trySend(chat)
            }
        awaitClose { reg.remove() }
    }

    fun observeMessages(chatId: String, limit: Long = 200): Flow<List<Message>> = callbackFlow {
        val reg = firestore.collection("chats").document(chatId)
            .collection("messages")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit)
            .addSnapshotListener { snap, _ ->
                val list = snap?.documents.orEmpty().mapNotNull { d ->
                    d.toObject(Message::class.java)?.apply { id = d.id; this.chatId = chatId }
                }.reversed()
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    suspend fun getOrCreateDirectChat(currentUser: User, other: User): String {
        val ids = listOf(currentUser.uid, other.uid).sorted()
        val chatId = "dm_${ids[0]}_${ids[1]}"
        val ref = firestore.collection("chats").document(chatId)
        val snap = ref.get().await()
        if (!snap.exists()) {
            val chat = Chat(
                id = chatId,
                type = Chat.TYPE_DIRECT,
                participants = ids,
                participantNicknames = mapOf(
                    currentUser.uid to currentUser.nickname,
                    other.uid to other.nickname,
                ),
                participantColors = mapOf(
                    currentUser.uid to currentUser.avatarColor,
                    other.uid to other.avatarColor,
                ),
                unreadCounts = mapOf(currentUser.uid to 0L, other.uid to 0L),
            )
            ref.set(chat).await()
        }
        return chatId
    }

    suspend fun createGroupChat(currentUser: User, members: List<User>, title: String): String {
        require(title.isNotBlank()) { "Название группы обязательно" }
        require(members.size >= 1) { "Добавьте хотя бы одного собеседника" }
        val all = (members + currentUser).distinctBy { it.uid }
        val ref = firestore.collection("chats").document()
        val chat = Chat(
            id = ref.id,
            type = Chat.TYPE_GROUP,
            title = title.trim(),
            participants = all.map { it.uid },
            participantNicknames = all.associate { it.uid to it.nickname },
            participantColors = all.associate { it.uid to it.avatarColor },
            unreadCounts = all.associate { it.uid to 0L },
        )
        ref.set(chat).await()
        return ref.id
    }

    suspend fun sendText(chatId: String, sender: User, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        sendMessage(
            chatId,
            Message(
                senderId = sender.uid,
                senderNickname = sender.nickname,
                type = MessageType.TEXT.value,
                text = trimmed,
            ),
            preview = trimmed,
        )
    }

    suspend fun sendMedia(
        chatId: String,
        sender: User,
        type: MessageType,
        uri: Uri,
        durationMs: Long = 0L,
        fileName: String? = null,
        fileSize: Long = 0L,
        previewLabel: String,
    ) {
        val resourceType = when (type) {
            MessageType.IMAGE -> CloudinaryResourceType.IMAGE
            // Cloudinary handles audio under the "video" resource type (m4a, mp3, ogg, wav).
            MessageType.VIDEO, MessageType.VOICE -> CloudinaryResourceType.VIDEO
            else -> CloudinaryResourceType.AUTO
        }
        val displayName = fileName?.takeIf { it.isNotBlank() }
            ?: "chat_${chatId}_${System.currentTimeMillis()}"
        val result = uploader.upload(uri, resourceType, displayName)

        sendMessage(
            chatId,
            Message(
                senderId = sender.uid,
                senderNickname = sender.nickname,
                type = type.value,
                text = "",
                mediaUrl = result.secureUrl,
                mediaPath = result.publicId,
                durationMs = durationMs,
                fileName = fileName,
                fileSize = if (fileSize > 0) fileSize else result.bytes,
            ),
            preview = previewLabel,
        )
    }

    private suspend fun sendMessage(chatId: String, message: Message, preview: String) {
        val chatRef = firestore.collection("chats").document(chatId)
        val msgRef = chatRef.collection("messages").document()
        val toWrite = message.copy(id = msgRef.id, chatId = chatId, readBy = listOf(message.senderId))

        firestore.runBatch { batch ->
            batch.set(msgRef, toWrite)
            batch.set(
                chatRef,
                mapOf(
                    "lastMessage" to preview,
                    "lastMessageType" to toWrite.type,
                    "lastMessageSenderId" to toWrite.senderId,
                    "lastMessageAt" to FieldValue.serverTimestamp(),
                ),
                SetOptions.merge(),
            )
        }.await()

        // Bump unread counts for everyone but the sender. Done as a follow-up update so the
        // message write isn't blocked on reading the chat doc.
        val chatSnap = chatRef.get().await()
        val chat = chatSnap.toObject(Chat::class.java) ?: return
        val updates = chat.participants
            .filter { it != message.senderId }
            .associate { uid -> "unreadCounts.$uid" to FieldValue.increment(1) }
        if (updates.isNotEmpty()) {
            chatRef.update(updates).await()
        }
    }

    suspend fun markRead(chatId: String, uid: String, messages: List<Message>) {
        val unread = messages.filter { it.senderId != uid && uid !in it.readBy }
        if (unread.isEmpty()) return
        val chatRef = firestore.collection("chats").document(chatId)
        firestore.runBatch { batch ->
            unread.forEach { msg ->
                batch.update(
                    chatRef.collection("messages").document(msg.id),
                    "readBy",
                    FieldValue.arrayUnion(uid),
                )
            }
            batch.update(chatRef, "unreadCounts.$uid", 0L)
        }.await()
    }

    suspend fun setTyping(chatId: String, uid: String, typing: Boolean) {
        val ref = firestore.collection("chats").document(chatId)
        ref.update("typing.$uid", if (typing) FieldValue.serverTimestamp() else FieldValue.delete()).await()
    }

    suspend fun deleteChat(chatId: String) {
        val ref = firestore.collection("chats").document(chatId)
        val msgs = ref.collection("messages").get().await()
        firestore.runBatch { batch ->
            msgs.documents.forEach { batch.delete(it.reference) }
            batch.delete(ref)
        }.await()
    }

    suspend fun getUsers(uids: List<String>): List<User> {
        if (uids.isEmpty()) return emptyList()
        val snap = firestore.collection("users")
            .whereIn(FieldPath.documentId(), uids.take(10))
            .get()
            .await()
        return snap.documents.mapNotNull { d ->
            d.toObject(User::class.java)?.apply { uid = d.id }
        }
    }
}
