package com.rwa.rwapay.services

import android.app.Notification
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.rwa.rwapay.MainActivity
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class NotificationListener : NotificationListenerService() {
    private val TAG = "RWANotificationListenerService"
    private val WEBHOOK_URL = "" // set webbook here
    private val LISTENER_SECRET = "" // set webhook listener secret here

    override fun onListenerConnected() {
        super.onListenerConnected()
        sendBroadcast(Intent(MainActivity.ACTION_LISTENER_CONNECTED))
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        sendBroadcast(Intent(MainActivity.ACTION_LISTENER_DISCONNECTED))
    }

    fun getUnsafeOkHttpClient(): OkHttpClient {
        val trustAllCerts = arrayOf<TrustManager>(
            object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            }
        )

        val sslContext = SSLContext.getInstance("SSL")
        sslContext.init(null, trustAllCerts, SecureRandom())
        val sslSocketFactory = sslContext.socketFactory

        return OkHttpClient.Builder()
            .sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .build()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)

        val packageName = sbn?.packageName
        val notification = sbn?.notification
        val title = notification?.extras?.getString(Notification.EXTRA_TITLE)
        val text = notification?.extras?.getString(Notification.EXTRA_TEXT)

        if (packageName == "com.forum_asisten" && text?.contains("berhasil menerima Rp") == true) {
            sendToWebhook(title, text)
            sendNotificationToMainActivity(title, text)
        }

        if (packageName == "id.dana" && text?.contains("berhasil menerima Rp") == true) {
            sendToWebhook(title, text)
            sendNotificationToMainActivity(title, text)
        }
    }

    private fun sendToWebhook(title: String?, text: String?) {
        val client = getUnsafeOkHttpClient()

        val json = JSONObject()
        json.put("title", title)
        json.put("text", text)

//        val body = RequestBody.create(
//            "application/json; charset=utf-8".toMediaTypeOrNull(),
//            json.toString()
//        )

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = json.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url(WEBHOOK_URL)
            .addHeader("X-Listener-Token", LISTENER_SECRET)
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) throw IOException("Unexpected code $response")

                    Log.d(TAG, "Webhook response: ${response.body?.string()}")
                }
            }
        })
    }

    private fun sendNotificationToMainActivity(title: String?, text: String?) {
        val intent = Intent(MainActivity.ACTION_NOTIFICATION_RECEIVED)
        intent.putExtra("title", title)
        intent.putExtra("text", text)
        sendBroadcast(intent)
    }
}