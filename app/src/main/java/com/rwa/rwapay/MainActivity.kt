package com.rwa.rwapay

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService.requestRebind
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.rwa.rwapay.services.NotificationListener

class MainActivity : AppCompatActivity() {
    private lateinit var reconnectButton: Button
    private lateinit var statusTextView: TextView
    private lateinit var notificationListView: ListView
    private lateinit var notificationAdapter: ArrayAdapter<String>
    private val notificationHistory = mutableListOf<String>()
    private var isConnected = false

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_LISTENER_CONNECTED -> onListenerConnected()
                ACTION_LISTENER_DISCONNECTED -> onListenerDisconnected()
                ACTION_NOTIFICATION_RECEIVED -> {
                    val title = intent.getStringExtra("title")
                    val text = intent.getStringExtra("text")
                    val notification = "$title: $text"
                    addNotification(notification)
                }
            }
        }
    }

    fun isNotificationServiceEnabled(context: Context): Boolean {
        return NotificationManagerCompat.getEnabledListenerPackages(context)
            .contains(context.packageName)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val intentFilter = IntentFilter().apply {
            addAction(ACTION_LISTENER_CONNECTED)
            addAction(ACTION_LISTENER_DISCONNECTED)
            addAction(ACTION_NOTIFICATION_RECEIVED)
        }
        registerReceiver(broadcastReceiver, intentFilter)

        reconnectButton = findViewById(R.id.reconnectButton)
        statusTextView = findViewById(R.id.statusTextView)
        notificationListView = findViewById(R.id.notificationListView)

        notificationAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, notificationHistory)
        notificationListView.adapter = notificationAdapter

        reconnectButton.setOnClickListener {
            if (!isConnected) {
                reconnectToService()
            }
        }

        if (!isNotificationServiceEnabled(this)) {
            val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
            startActivity(intent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(broadcastReceiver)
    }

    private fun reconnectToService() {
        statusTextView.text = "Status: Reconnecting..."

//        val componentName = ComponentName(context, NotificationListener::class.java)
//        requestRebind(componentName)

        restartNotificationListenerService(this)

        statusTextView.postDelayed({
            // Logika tambahan
        }, 2000)
    }

    private fun restartNotificationListenerService(context: Context) {
        try {
            val pm = context.packageManager
            val componentName = ComponentName(context, NotificationListener::class.java)

            pm.setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )

            pm.setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )

            val serviceIntent = Intent(this, NotificationListener::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(this, serviceIntent)
            } else {
                startService(serviceIntent)
            }

        } catch (e: Exception) {
            Log.e("ServiceRestart", "Gagal restart NotificationListenerService", e)
        }
    }

    fun onListenerConnected() {
        isConnected = true
        statusTextView.text = "Status: Connected"
    }

    fun onListenerDisconnected() {
        isConnected = false
        statusTextView.text = "Status: Disconnected"
    }

    fun addNotification(notification: String) {
        notificationHistory.add(notification)
        notificationAdapter.notifyDataSetChanged()
    }

    companion object {
        const val ACTION_LISTENER_CONNECTED = "com.rwa.rwapay.ACTION_LISTENER_CONNECTED"
        const val ACTION_LISTENER_DISCONNECTED = "com.rwa.rwapay.ACTION_LISTENER_DISCONNECTED"
        const val ACTION_NOTIFICATION_RECEIVED = "com.rwa.rwapay.ACTION_NOTIFICATION_RECEIVED"
    }
}