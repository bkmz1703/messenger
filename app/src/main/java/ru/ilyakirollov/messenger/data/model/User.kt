package ru.ilyakirollov.messenger.data.model

import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

@IgnoreExtraProperties
data class User(
    @get:PropertyName("uid") @set:PropertyName("uid")
    var uid: String = "",
    @get:PropertyName("nickname") @set:PropertyName("nickname")
    var nickname: String = "",
    @get:PropertyName("nicknameLower") @set:PropertyName("nicknameLower")
    var nicknameLower: String = "",
    @get:PropertyName("avatarColor") @set:PropertyName("avatarColor")
    var avatarColor: Long = 0xFF0F9D58,
    @get:PropertyName("photoUrl") @set:PropertyName("photoUrl")
    var photoUrl: String? = null,
    @get:PropertyName("fcmToken") @set:PropertyName("fcmToken")
    var fcmToken: String? = null,
    @get:PropertyName("online") @set:PropertyName("online")
    var online: Boolean = false,
    @ServerTimestamp
    @get:PropertyName("lastSeenAt") @set:PropertyName("lastSeenAt")
    var lastSeenAt: Date? = null,
    @ServerTimestamp
    @get:PropertyName("createdAt") @set:PropertyName("createdAt")
    var createdAt: Date? = null,
)
