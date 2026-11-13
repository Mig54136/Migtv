package fr.migtv.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder

/** Service de premier plan : empêche Android de couper la conversion audio quand l'écran s'éteint. */
class RelayService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("relay", "Conversion audio", NotificationManager.IMPORTANCE_LOW)
        )
        val notification = Notification.Builder(this, "relay")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("MigTV")
            .setContentText("Conversion audio pour le Chromecast en cours")
            .setOngoing(true)
            .build()
        startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
