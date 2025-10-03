package com.rwa.rwapay.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.rwa.rwapay.R

class CoreForegroundService : Service() {

    companion object {
        private const val CHANNEL_ID = "rwapay_core_channel"
        private const val NOTIF_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()
        Log.i("CoreForegroundService", "onCreate")
        createChannel()
        startForeground(NOTIF_ID, buildNotification("Layanan aktif dan memantau notifikasi..."))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i("CoreForegroundService", "onStartCommand")
        // TODO: Jika perlu, jadwalkan worker/heartbeat di sini.
        return START_STICKY
    }

    override fun onDestroy() {
        Log.w("CoreForegroundService", "onDestroy - service dimatikan")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(
                CHANNEL_ID,
                "RWAPay Core",
                NotificationManager.IMPORTANCE_LOW
            )
            chan.setShowBadge(false)
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(chan)
        }
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }
}
