package ru.ilyakirollov.messenger.data.model

import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

@IgnoreExtraProperties
data class Chat(
    @get:PropertyName("id") @set:PropertyName("id")
    var id: String = "",
    @get:PropertyName("type") @set:PropertyName("type")
    var type: String = TYPE_DIRECT,
    @get:PropertyName("title") @set:PropertyName("title")
    var title: String? = null,
    @get:PropertyName("participants") @set:PropertyName("participants")
    var participants: List<String> = emptyList(),
    @get:PropertyName("participantNicknames") @set:PropertyName("participantNicknames")
    var participantNicknames: Map<String, String> = emptyMap(),
    @get:PropertyName("participantColors") @set:PropertyName("participantColors")
    var participantColors: Map<String, Long> = emptyMap(),
    @get:PropertyName("lastMessage") @set:PropertyName("lastMessage")
    var lastMessage: String = "",
    @get:PropertyName("lastMessageType") @set:PropertyName("lastMessageType")
    var lastMessageType: String = MessageType.TEXT.value,
    @get:PropertyName("lastMessageSenderId") @set:PropertyName("lastMessageSenderId")
    var lastMessageSenderId: String = "",
    @ServerTimestamp
    @get:PropertyName("lastMessageAt") @set:PropertyName("lastMessageAt")
    var lastMessageAt: Date? = null,
    @get:PropertyName("unreadCounts") @set:PropertyName("unreadCounts")
    var unreadCounts: Map<String, Long> = emptyMap(),
    @ServerTimestamp
    @get:PropertyName("createdAt") @set:PropertyName("createdAt")
    var createdAt: Date? = null,
) {
    companion object {
        const val TYPE_DIRECT = "direct"
        const val TYPE_GROUP = "group"
    }
}
