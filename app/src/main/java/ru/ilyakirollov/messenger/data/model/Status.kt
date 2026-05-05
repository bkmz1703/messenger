package ru.ilyakirollov.messenger.data.model

import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

@IgnoreExtraProperties
data class Status(
    @get:PropertyName("id") @set:PropertyName("id")
    var id: String = "",
    @get:PropertyName("authorId") @set:PropertyName("authorId")
    var authorId: String = "",
    @get:PropertyName("authorNickname") @set:PropertyName("authorNickname")
    var authorNickname: String = "",
    @get:PropertyName("authorColor") @set:PropertyName("authorColor")
    var authorColor: Long = 0xFF0F9D58,
    @get:PropertyName("type") @set:PropertyName("type")
    var type: String = "text",
    @get:PropertyName("text") @set:PropertyName("text")
    var text: String = "",
    @get:PropertyName("mediaUrl") @set:PropertyName("mediaUrl")
    var mediaUrl: String? = null,
    @get:PropertyName("backgroundColor") @set:PropertyName("backgroundColor")
    var backgroundColor: Long = 0xFF1976D2,
    @get:PropertyName("viewers") @set:PropertyName("viewers")
    var viewers: List<String> = emptyList(),
    @ServerTimestamp
    @get:PropertyName("createdAt") @set:PropertyName("createdAt")
    var createdAt: Date? = null,
    @get:PropertyName("expiresAt") @set:PropertyName("expiresAt")
    var expiresAt: Date? = null,
)
