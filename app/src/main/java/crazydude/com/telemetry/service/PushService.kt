package crazydude.com.telemetry.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import crazydude.com.telemetry.R

class PushService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage?) {
        super.onMessageReceived(message)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            var channel = notificationManager.getNotificationChannel("fcm_fallback_notification_channel")
            if (channel == null) {
                channel = NotificationChannel("fcm_fallback_notification_channel", "Miscellaneous", NotificationManager.IMPORTANCE_DEFAULT)
                notificationManager.createNotificationChannel(channel)
            }
        }

        val notification = NotificationCompat.Builder(this, "fcm_fallback_notification_channel")
            .setContentText(message?.notification?.body)
            .setContentTitle(message?.notification?.title)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()

        NotificationManagerCompat.from(this).notify(2, notification)
    }
}