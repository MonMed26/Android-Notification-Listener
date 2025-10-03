package com.rwa.rwapay

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.rwa.rwapay.services.NotificationListener

class MainActivity : AppCompatActivity() {

    companion object {
        const val ACTION_LISTENER_CONNECTED = "com.rwa.rwapay.ACTION_LISTENER_CONNECTED"
        const val ACTION_LISTENER_DISCONNECTED = "com.rwa.rwapay.ACTION_LISTENER_DISCONNECTED"
        const val ACTION_NOTIFICATION_RECEIVED = "com.rwa.rwapay.ACTION_NOTIFICATION_RECEIVED"
    }

    private lateinit var reconnectButton: Button
    private lateinit var statusTextView: TextView
    private lateinit var notificationListView: ListView
    private lateinit var notificationAdapter: ArrayAdapter<String>
    private val notificationHistory = mutableListOf<String>()
    private var isConnected = false

    private lateinit var switchEnable: Switch
    private lateinit var inputWebhook: EditText
    private lateinit var saveButton: Button

    private val prefs by lazy { getSharedPreferences("listener_prefs", MODE_PRIVATE) }

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

    private fun isNotificationServiceEnabled(context: Context): Boolean {
        return NotificationManagerCompat.getEnabledListenerPackages(context)
            .contains(context.packageName)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        registerReceiver(broadcastReceiver, IntentFilter().apply {
            addAction(ACTION_LISTENER_CONNECTED)
            addAction(ACTION_LISTENER_DISCONNECTED)
            addAction(ACTION_NOTIFICATION_RECEIVED)
        })

        reconnectButton = findViewById(R.id.reconnectButton)
        statusTextView = findViewById(R.id.statusTextView)
        notificationListView = findViewById(R.id.notificationListView)

        switchEnable = findViewById(R.id.listenerSwitch)
        inputWebhook = findViewById(R.id.inputWebhook)
        saveButton = findViewById(R.id.saveButton)

        notificationAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, notificationHistory)
        notificationListView.adapter = notificationAdapter

        // load nilai awal
        switchEnable.isChecked = prefs.getBoolean("listener_enabled", true)
        inputWebhook.setText(prefs.getString("webhook_url", ""))

        statusTextView.text = if (switchEnable.isChecked) "Listener Enabled" else "Listener Disabled"

        reconnectButton.setOnClickListener {
            if (!isConnected) {
                reconnectToService()
            } else {
                try {
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                } catch (_: Exception) {}
            }
        }

        switchEnable.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("listener_enabled", isChecked).apply()
            statusTextView.text = if (isChecked) "Listener Enabled" else "Listener Disabled"
            Toast.makeText(this, if (isChecked) "Enabled" else "Disabled", Toast.LENGTH_SHORT).show()
        }

        saveButton.setOnClickListener {
            prefs.edit()
                .putString("webhook_url", inputWebhook.text.toString().trim())
                .apply()
            Toast.makeText(this, "Webhook URL saved", Toast.LENGTH_SHORT).show()
        }

        if (!isNotificationServiceEnabled(this)) {
            try {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            } catch (_: Exception) {}
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(broadcastReceiver)
    }

    private fun reconnectToService() {
        statusTextView.text = "Status: Reconnecting..."
        restartNotificationListenerService(this)
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

    private fun onListenerConnected() {
        isConnected = true
        statusTextView.text = "Status: Connected"
    }

    private fun onListenerDisconnected() {
        isConnected = false
        statusTextView.text = "Status: Disconnected"
    }

    private fun addNotification(notification: String) {
        notificationHistory.add(notification)
        notificationAdapter.notifyDataSetChanged()
    }
}
