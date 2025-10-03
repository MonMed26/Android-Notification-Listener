package com.rwa.rwapay.services

import android.app.Notification
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.rwa.rwapay.MainActivity
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class NotificationListener : NotificationListenerService() {

    private val TAG = "RWANotificationListener"

    private val client by lazy {
        OkHttpClient.Builder()
            .callTimeout(15, TimeUnit.SECONDS)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        sendBroadcast(Intent(MainActivity.ACTION_LISTENER_CONNECTED))
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        sendBroadcast(Intent(MainActivity.ACTION_LISTENER_DISCONNECTED))
    }

    private fun isEnabled(): Boolean {
        val p = getSharedPreferences("listener_prefs", MODE_PRIVATE)
        return p.getBoolean("listener_enabled", true)
    }

    private fun getWebhook(): Pair<String, String> {
        val p = getSharedPreferences("listener_prefs", MODE_PRIVATE)
        val url = p.getString("webhook_url", "") ?: ""
        val secret = p.getString("webhook_secret", "") ?: ""
        return url to secret
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (!isEnabled()) return

        val pkg = sbn?.packageName ?: return
        val n = sbn.notification ?: return
        val title = n.extras.getString(Notification.EXTRA_TITLE)
        val text = n.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()

        // ====== FILTER CONTOH SESUAI LOGIKA LAMA ======
        if (pkg == "com.forum_asisten" && text?.contains("berhasil menerima Rp") == true) {
            sendToWebhook(title, text, pkg)
            sendToMain(title, text)
        }
        if (pkg == "id.dana" && text?.contains("berhasil menerima Rp") == true) {
            sendToWebhook(title, text, pkg)
            sendToMain(title, text)
        }
        // Tambah filter lain di sini bila diperlukan.
    }

    private fun sendToWebhook(title: String?, text: String?, packageName: String) {
        val (webhookUrl, secret) = getWebhook()
        if (webhookUrl.isEmpty()) {
            Log.w(TAG, "Webhook URL kosong — skip.")
            return
        }

        val json = JSONObject()
            .put("event_type", "notification_posted")
            .put("package", packageName)
            .put("title", title ?: "")
            .put("text", text ?: "")
            .put("posted_at", System.currentTimeMillis())
            .toString()

        val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
        val req = Request.Builder()
            .url(webhookUrl)
            .addHeader("X-Listener-Token", secret)
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Webhook gagal: ${e.message}", e)
            }
            override fun onResponse(call: Call, response: Response) {
                response.close()
                Log.d(TAG, "Webhook response code: ${response.code}")
            }
        })
    }

    private fun sendToMain(title: String?, text: String?) {
        val intent = Intent(MainActivity.ACTION_NOTIFICATION_RECEIVED)
        intent.putExtra("title", title ?: "")
        intent.putExtra("text", text ?: "")
        sendBroadcast(intent)
    }
}
