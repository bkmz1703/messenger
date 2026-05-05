package ru.ilyakirollov.messenger.data.model

import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

enum class MessageType(val value: String) {
    TEXT("text"),
    IMAGE("image"),
    VIDEO("video"),
    VOICE("voice"),
    FILE("file"),
    SYSTEM("system");

    companion object {
        fun from(value: String?): MessageType =
            entries.firstOrNull { it.value == value } ?: TEXT
    }
}

@IgnoreExtraProperties
data class Message(
    @get:PropertyName("id") @set:PropertyName("id")
    var id: String = "",
    @get:PropertyName("chatId") @set:PropertyName("chatId")
    var chatId: String = "",
    @get:PropertyName("senderId") @set:PropertyName("senderId")
    var senderId: String = "",
    @get:PropertyName("senderNickname") @set:PropertyName("senderNickname")
    var senderNickname: String = "",
    @get:PropertyName("type") @set:PropertyName("type")
    var type: String = MessageType.TEXT.value,
    @get:PropertyName("text") @set:PropertyName("text")
    var text: String = "",
    @get:PropertyName("mediaUrl") @set:PropertyName("mediaUrl")
    var mediaUrl: String? = null,
    @get:PropertyName("mediaPath") @set:PropertyName("mediaPath")
    var mediaPath: String? = null,
    @get:PropertyName("durationMs") @set:PropertyName("durationMs")
    var durationMs: Long = 0L,
    @get:PropertyName("fileName") @set:PropertyName("fileName")
    var fileName: String? = null,
    @get:PropertyName("fileSize") @set:PropertyName("fileSize")
    var fileSize: Long = 0L,
    @get:PropertyName("readBy") @set:PropertyName("readBy")
    var readBy: List<String> = emptyList(),
    @ServerTimestamp
    @get:PropertyName("createdAt") @set:PropertyName("createdAt")
    var createdAt: Date? = null,
)
