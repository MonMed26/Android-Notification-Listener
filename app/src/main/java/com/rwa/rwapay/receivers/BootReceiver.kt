package com.rwa.rwapay.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.rwa.rwapay.services.CoreForegroundService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i("BootReceiver", "onReceive: ${intent.action}")

        // Start foreground service agar proses tetap hidup
        val svc = Intent(context, CoreForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(svc)
        } else {
            context.startService(svc)
        }

        // Catatan:
        // NotificationListenerService akan di-rebind otomatis oleh sistem
        // jika user sudah memberikan izin akses notifikasi pada app ini.
    }
}
