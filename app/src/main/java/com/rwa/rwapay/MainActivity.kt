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
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.rwa.rwapay.services.NotificationListener
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    companion object {
        const val ACTION_LISTENER_CONNECTED = "com.rwa.rwapay.ACTION_LISTENER_CONNECTED"
        const val ACTION_LISTENER_DISCONNECTED = "com.rwa.rwapay.ACTION_LISTENER_DISCONNECTED"
        const val ACTION_NOTIFICATION_RECEIVED = "com.rwa.rwapay.ACTION_NOTIFICATION_RECEIVED"
        const val ACTION_LOG = "com.rwa.rwapay.ACTION_LOG"  // <--- untuk log dari service
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
    private lateinit var testWebhookButton: Button
    private lateinit var logView: TextView

    private val prefs by lazy { getSharedPreferences("listener_prefs", MODE_PRIVATE) }

    private val client by lazy {
        OkHttpClient.Builder()
            .callTimeout(15, TimeUnit.SECONDS)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

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
                ACTION_LOG -> {
                    val msg = intent.getStringExtra("msg") ?: return
                    appendLog(msg)
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
            addAction(ACTION_LOG)
        })

        reconnectButton = findViewById(R.id.reconnectButton)
        statusTextView = findViewById(R.id.statusTextView)
        notificationListView = findViewById(R.id.notificationListView)
        switchEnable = findViewById(R.id.listenerSwitch)
        inputWebhook = findViewById(R.id.inputWebhook)
        saveButton = findViewById(R.id.saveButton)
        testWebhookButton = findViewById(R.id.testWebhookButton)
        logView = findViewById(R.id.logView)
        logView.movementMethod = ScrollingMovementMethod()

        notificationAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, notificationHistory)
        notificationListView.adapter = notificationAdapter

        // load awal
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
            appendLog("Toggle listener: ${if (isChecked) "ON" else "OFF"}")
            Toast.makeText(this, if (isChecked) "Enabled" else "Disabled", Toast.LENGTH_SHORT).show()
        }

        saveButton.setOnClickListener {
            val url = inputWebhook.text.toString().trim()
            prefs.edit().putString("webhook_url", url).apply()
            appendLog("Webhook URL saved: $url")
            Toast.makeText(this, "Webhook URL saved", Toast.LENGTH_SHORT).show()
        }

        // Tombol TEST WEBHOOK
        testWebhookButton.setOnClickListener {
            val url = prefs.getString("webhook_url", "") ?: ""
            if (url.isEmpty()) {
                appendLog("Test aborted: Webhook URL kosong")
                Toast.makeText(this, "Isi Webhook URL dulu", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val payload = """{
              "event_type":"test",
              "message":"Hello from RWAPay",
              "ts":${System.currentTimeMillis()}
            }""".trimIndent()
            val body = payload.toRequestBody("application/json; charset=utf-8".toMediaType())
            val req = Request.Builder()
                .url(url)
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build()
            appendLog("TEST → POST $url")
            client.newCall(req).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                    appendLog("TEST FAILED: ${e.message}")
                }
                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    response.use {
                        appendLog("TEST OK: HTTP ${response.code}")
                    }
                }
            })
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
        appendLog("Rebinding NotificationListenerService...")
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
            appendLog("ERROR restart service: ${e.message}")
        }
    }

    private fun onListenerConnected() {
        isConnected = true
        statusTextView.text = "Status: Connected"
        appendLog("Listener Connected")
    }

    private fun onListenerDisconnected() {
        isConnected = false
        statusTextView.text = "Status: Disconnected"
        appendLog("Listener Disconnected")
    }

    private fun addNotification(notification: String) {
        notificationHistory.add(notification)
        notificationAdapter.notifyDataSetChanged()
        appendLog("Notif: $notification")
    }

    private fun appendLog(msg: String) {
        runOnUiThread {
            val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
            val newText = logView.text.toString() + "\n[$time] $msg"
            logView.text = newText
            // auto-scroll
            val layout = logView.layout
            if (layout != null) {
                val scrollAmount = layout.getLineTop(logView.lineCount) - logView.height
                if (scrollAmount > 0) logView.scrollTo(0, scrollAmount) else logView.scrollTo(0, 0)
            }
        }
        Log.d("RWAPay-UI", msg)
    }
}
