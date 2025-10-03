package com.rwa.rwapay.services

import android.app.*
import android.content.*
import android.os.*
import androidx.core.app.NotificationCompat
import com.rwa.rwapay.R

class CoreForegroundService : Service() {

    companion object {
        private const val CHANNEL_ID = "rwapay_core_channel"
        private const val NOTIF_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val notif: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Service berjalan di background")
            .setSmallIcon(R.mipmap.ic_launcher) // ikon pasti ada
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        startForeground(NOTIF_ID, notif)

        // Sedikit delay lalu minta rebind listener (kalau user sudah beri akses)
        Handler(Looper.getMainLooper()).postDelayed({
            tryRebindListener()
        }, 1200)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // bisa pasang heartbeat di sini kalau perlu
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // restart otomatis jika user swipe dari recent apps
        val restartIntent = Intent(applicationContext, CoreForegroundService::class.java).apply {
            `package` = packageName
        }
        val pi = PendingIntent.getService(
            applicationContext, 1, restartIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 1000, pi)
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        super.onDestroy()
        // kalau dimatikan sistem, coba bangun lagi cepat
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getService(
            this, 2, Intent(this, CoreForegroundService::class.java),
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        am.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 1500, pi)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID, "RWAPay Core",
                    NotificationManager.IMPORTANCE_LOW
                ).apply { setShowBadge(false) }
            )
        }
    }

    private fun tryRebindListener() {
        // trik disable-enable component supaya NotificationListener di-rebind sistem
        try {
            val pm = packageManager
            val cn = ComponentName(this, NotificationListener::class.java)
            pm.setComponentEnabledSetting(
                cn, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP
            )
            pm.setComponentEnabledSetting(
                cn, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP
            )
        } catch (_: Exception) { }
    }
}
