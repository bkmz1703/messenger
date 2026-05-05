package ru.ilyakirollov.messenger.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import ru.ilyakirollov.messenger.MainActivity
import ru.ilyakirollov.messenger.MessengerApp
import ru.ilyakirollov.messenger.R
import ru.ilyakirollov.messenger.data.repository.AuthRepository

@AndroidEntryPoint
class MessengerFcmService : FirebaseMessagingService() {

    @Inject lateinit var authRepository: AuthRepository
    @Inject lateinit var firestore: FirebaseFirestore

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val uid = authRepository.currentUid ?: return
        firestore.collection("users").document(uid)
            .set(mapOf("fcmToken" to token), SetOptions.merge())
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val type = message.data["type"] ?: "message"
        val title = message.notification?.title ?: message.data["title"] ?: ""
        val body = message.notification?.body ?: message.data["body"] ?: ""
        val channel = if (type == "call") MessengerApp.CHANNEL_CALLS else MessengerApp.CHANNEL_MESSAGES
        showNotification(title.ifBlank { "Сообщение" }, body, channel, message.messageId.hashCode())
    }

    private fun showNotification(title: String, body: String, channel: String, id: Int) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return
        val intent = MainActivity::class.java.let {
            android.content.Intent(this, it).apply { flags = android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP }
        }
        val pi = PendingIntent.getActivity(
            this,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notif = NotificationCompat.Builder(this, channel)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pi)
            .build()
        NotificationManagerCompat.from(this).notify(id, notif)
    }
}
