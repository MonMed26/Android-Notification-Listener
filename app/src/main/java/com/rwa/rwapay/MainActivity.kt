package com.rwa.rwapay

import android.Manifest
import android.content.*
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.rwa.rwapay.services.NotificationListener
import com.rwa.rwapay.ui.AppItem
import com.rwa.rwapay.ui.AppListAdapter
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
        const val ACTION_LOG = "com.rwa.rwapay.ACTION_LOG"
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

    // Tombol opsional untuk buka setting battery/auto-start (kalau ada di layout-mu)
    // private lateinit var btnBattery: Button
    // private lateinit var btnAutostart: Button

    // RecyclerView
    private lateinit var appRecycler: RecyclerView
    private lateinit var appAdapter: AppListAdapter

    private val prefs by lazy { getSharedPreferences("listener_prefs", MODE_PRIVATE) }

    private val client by lazy {
        OkHttpClient.Builder()
            .callTimeout(15, TimeUnit.SECONDS)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    // Permission POST_NOTIFICATIONS (Android 13+) — aman kalau tak dipakai
    private val requestNotifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        appendLog(if (granted) "Izin notifikasi diberikan" else "Izin notifikasi ditolak")
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
                ACTION_LOG -> appendLog(intent.getStringExtra("msg") ?: return)
            }
        }
    }

    private fun isNotificationServiceEnabled(context: Context): Boolean {
        return NotificationManagerCompat.getEnabledListenerPackages(context)
            .contains(context.packageName)
    }

    // ====== Battery Optimization helpers ======
    private fun isIgnoringBatteryOptimizations(): Boolean {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun requestIgnoreBatteryOptimizations() {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
            appendLog("Meminta whitelist battery optimization…")
        } catch (e: Exception) {
            appendLog("Gagal buka REQUEST_IGNORE_BATTERY_OPTIMIZATIONS: ${e.message}")
        }
    }

    private fun openManufacturerAutoStartSettings() {
        // Beberapa vendor punya halaman autostart sendiri.
        val intents = listOf(
            // Xiaomi
            Intent().setClassName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            ),
            // newer MIUI
            Intent("miui.intent.action.POWER_HIDE_MODE_APP_LIST").addCategory(Intent.CATEGORY_DEFAULT),
            // Oppo
            Intent().setClassName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.startupapp.StartupAppListActivity"
            ),
            Intent().setClassName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.permission.startup.StartupAppListActivity"
            ),
            // Vivo
            Intent().setClassName(
                "com.vivo.permissionmanager",
                "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
            ),
            // Huawei
            Intent().setClassName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
            )
        )

        for (i in intents) {
            try {
                startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                appendLog("Membuka pengaturan auto-start vendor…")
                return
            } catch (_: Exception) { /* coba intent berikutnya */ }
        }
        appendLog("Tidak menemukan halaman auto-start vendor.")
    }
    // ==========================================

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
        logView = findViewById(R.id.logView); logView.movementMethod = ScrollingMovementMethod()
        appRecycler = findViewById(R.id.appRecycler)
        // btnBattery = findViewById(R.id.btnBattery)       // kalau kamu punya di layout
        // btnAutostart = findViewById(R.id.btnAutostart)   // kalau kamu punya di layout

        // list log/riwayat notif
        notificationAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, notificationHistory)
        notificationListView.adapter = notificationAdapter

        // load awal
        switchEnable.isChecked = prefs.getBoolean("listener_enabled", true)
        inputWebhook.setText(prefs.getString("webhook_url", ""))
        statusTextView.text = if (switchEnable.isChecked) "Listener Enabled" else "Listener Disabled"

        // RecyclerView setup
        appAdapter = AppListAdapter(mutableListOf()) { item, checked ->
            val set = prefs.getStringSet("allowed_packages", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
            if (checked) set.add(item.packageName) else set.remove(item.packageName)
            prefs.edit().putStringSet("allowed_packages", set).apply()
            appendLog("Filter ${item.packageName}: ${if (checked) "ON" else "OFF"}")
        }
        appRecycler.layoutManager = LinearLayoutManager(this)
        appRecycler.adapter = appAdapter

        // Muat daftar aplikasi
        loadInstalledApps()

        reconnectButton.setOnClickListener {
            if (!isConnected) {
                reconnectToService()
            } else {
                try { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) } catch (_: Exception) {}
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

        testWebhookButton.setOnClickListener {
            val url = prefs.getString("webhook_url", "") ?: ""
            if (url.isEmpty()) {
                appendLog("Test aborted: Webhook URL kosong")
                Toast.makeText(this, "Isi Webhook URL dulu", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val payload = """{"event_type":"test","message":"Hello from RWAPay","ts":${System.currentTimeMillis()}}"""
            val body = payload.toRequestBody("application/json; charset=utf-8".toMediaType())
            val req = Request.Builder().url(url).addHeader("Content-Type", "application/json").post(body).build()
            appendLog("TEST → POST $url")
            client.newCall(req).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                    appendLog("TEST FAILED: ${e.message}")
                }
                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    response.use { appendLog("TEST OK: HTTP ${response.code}") }
                }
            })
        }

        // ====== PERMINTAAN IZIN PENTING ======
        // 1) POST_NOTIFICATIONS (Android 13+) — opsional tapi bagus untuk notifikasi foreground service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // 2) Ajak user whitelist battery optimization bila belum
        if (!isIgnoringBatteryOptimizations()) {
            requestIgnoreBatteryOptimizations()
            // opsional: buka halaman autostart vendor agar service tak dibunuh
            openManufacturerAutoStartSettings()
        }

        // 3) Ajak user aktifkan akses notifikasi jika belum
        if (!isNotificationServiceEnabled(this)) {
            try { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) } catch (_: Exception) {}
        }

        // Jika kamu punya tombol khusus di UI:
        // btnBattery.setOnClickListener { requestIgnoreBatteryOptimizations() }
        // btnAutostart.setOnClickListener { openManufacturerAutoStartSettings() }
    }

    override fun onResume() {
        super.onResume()
        // Re-check status setelah user balik dari Settings
        appendLog(
            "Battery optimization ignored: " +
                if (isIgnoringBatteryOptimizations()) "YA" else "TIDAK"
        )
    }

    private fun loadInstalledApps() {
        val pm = packageManager
        val allowed = prefs.getStringSet("allowed_packages", emptySet()) ?: emptySet()
        val apps = pm.getInstalledApplications(0)
            .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 } // hide system apps; hapus baris ini jika ingin tampil semua
            .sortedBy { pm.getApplicationLabel(it).toString().lowercase() }
            .map {
                AppItem(
                    label = pm.getApplicationLabel(it).toString(),
                    packageName = it.packageName,
                    icon = pm.getApplicationIcon(it),
                    enabled = allowed.contains(it.packageName)
                )
            }
        appAdapter.replaceAll(apps)
        appendLog("Loaded ${apps.size} apps into filter list")
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
            } else startService(serviceIntent)

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
            val layout = logView.layout
            if (layout != null) {
                val scrollAmount = layout.getLineTop(logView.lineCount) - logView.height
                if (scrollAmount > 0) logView.scrollTo(0, scrollAmount) else logView.scrollTo(0, 0)
            }
        }
        Log.d("RWAPay-UI", msg)
    }
}
