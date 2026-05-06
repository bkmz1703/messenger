package ru.ilyakirollov.messenger.call

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import ru.ilyakirollov.messenger.MainActivity
import ru.ilyakirollov.messenger.MessengerApp
import ru.ilyakirollov.messenger.R

/**
 * Foreground service to keep WebRTC mic/camera capture alive while the call is in progress.
 * Started/stopped from the call screen view-model.
 */
class CallForegroundService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notif = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIF_ID,
                notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA,
            )
        } else {
            startForeground(NOTIF_ID, notif)
        }
        return START_NOT_STICKY
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, MessengerApp.CHANNEL_CALLS)
            .setContentTitle(getString(R.string.ongoing_call_title))
            .setContentText(getString(R.string.call_in_progress))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }

    companion object {
        const val NOTIF_ID = 4242
        fun start(context: android.content.Context) {
            val intent = Intent(context, CallForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: android.content.Context) {
            context.stopService(Intent(context, CallForegroundService::class.java))
        }
    }
}
