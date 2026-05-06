package ru.ilyakirollov.messenger.data.model

import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

@IgnoreExtraProperties
data class CallSession(
    @get:PropertyName("id") @set:PropertyName("id")
    var id: String = "",
    @get:PropertyName("callerId") @set:PropertyName("callerId")
    var callerId: String = "",
    @get:PropertyName("callerNickname") @set:PropertyName("callerNickname")
    var callerNickname: String = "",
    @get:PropertyName("calleeId") @set:PropertyName("calleeId")
    var calleeId: String = "",
    @get:PropertyName("calleeNickname") @set:PropertyName("calleeNickname")
    var calleeNickname: String = "",
    @get:PropertyName("video") @set:PropertyName("video")
    var video: Boolean = false,
    @get:PropertyName("status") @set:PropertyName("status")
    var status: String = STATUS_RINGING,
    @get:PropertyName("offer") @set:PropertyName("offer")
    var offer: SessionDescriptionData? = null,
    @get:PropertyName("answer") @set:PropertyName("answer")
    var answer: SessionDescriptionData? = null,
    @ServerTimestamp
    @get:PropertyName("createdAt") @set:PropertyName("createdAt")
    var createdAt: Date? = null,
    @get:PropertyName("endedAt") @set:PropertyName("endedAt")
    var endedAt: Date? = null,
) {
    companion object {
        const val STATUS_RINGING = "ringing"
        const val STATUS_ANSWERED = "answered"
        const val STATUS_DECLINED = "declined"
        const val STATUS_ENDED = "ended"
        const val STATUS_MISSED = "missed"
    }
}

@IgnoreExtraProperties
data class SessionDescriptionData(
    @get:PropertyName("type") @set:PropertyName("type")
    var type: String = "",
    @get:PropertyName("sdp") @set:PropertyName("sdp")
    var sdp: String = "",
)

@IgnoreExtraProperties
data class IceCandidateData(
    @get:PropertyName("sdpMid") @set:PropertyName("sdpMid")
    var sdpMid: String = "",
    @get:PropertyName("sdpMLineIndex") @set:PropertyName("sdpMLineIndex")
    var sdpMLineIndex: Int = 0,
    @get:PropertyName("sdp") @set:PropertyName("sdp")
    var sdp: String = "",
    @get:PropertyName("from") @set:PropertyName("from")
    var from: String = "",
)
